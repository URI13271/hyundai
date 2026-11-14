use std::collections::HashMap;
use std::fs;
use std::io;
use std::path::{Path, PathBuf};
use std::sync::Mutex;

use crate::session::{random_bytes, ApiError, Card, Session};

pub struct Store {
    root: PathBuf,
    sessions: Mutex<HashMap<String, Session>>,
}

impl Store {
    pub fn open(root: impl Into<PathBuf>) -> io::Result<Self> {
        let root = root.into();
        let dir = root.join("sessions");
        fs::create_dir_all(&dir)?;
        let mut sessions = HashMap::new();
        for entry in fs::read_dir(&dir)? {
            let entry = entry?;
            let Some(name) = entry.file_name().to_str().map(str::to_string) else {
                continue;
            };
            let Some(id) = name.strip_suffix(".json") else {
                continue;
            };
            if !valid_id(id) {
                continue;
            }
            let text = match fs::read_to_string(entry.path()) {
                Ok(text) => text,
                Err(err) => {
                    eprintln!("skip session {id}: {err}");
                    continue;
                }
            };
            match serde_json::from_str::<Session>(&text) {
                Ok(mut session) => {
                    session.set_id(id.to_string());
                    sessions.insert(id.to_string(), session);
                }
                Err(err) => eprintln!("skip session {id}: {err}"),
            }
        }
        Ok(Self {
            root,
            sessions: Mutex::new(sessions),
        })
    }

    pub fn len(&self) -> usize {
        self.lock().len()
    }

    pub fn create(&self, deck: Vec<Card>) -> Result<Session, ApiError> {
        let mut guard = self.lock();
        let id = loop {
            let id = new_id();
            if !guard.contains_key(&id) {
                break id;
            }
        };
        let session = Session::open(id, deck);
        self.write_session(&session)?;
        guard.insert(session.id().to_string(), session.clone());
        Ok(session)
    }

    pub fn get(&self, id: &str) -> Result<Session, ApiError> {
        if !valid_id(id) {
            return Err(ApiError::NotFound);
        }
        self.lock().get(id).cloned().ok_or(ApiError::NotFound)
    }

    pub fn update(
        &self,
        id: &str,
        f: impl FnOnce(&mut Session) -> Result<(), ApiError>,
    ) -> Result<Session, ApiError> {
        if !valid_id(id) {
            return Err(ApiError::NotFound);
        }
        let mut guard = self.lock();
        let mut session = guard.get(id).cloned().ok_or(ApiError::NotFound)?;
        f(&mut session)?;
        self.write_session(&session)?;
        guard.insert(id.to_string(), session.clone());
        Ok(session)
    }

    pub fn delete(&self, id: &str) -> Result<(), ApiError> {
        if !valid_id(id) {
            return Err(ApiError::NotFound);
        }
        let mut guard = self.lock();
        if !guard.contains_key(id) {
            return Err(ApiError::NotFound);
        }
        match fs::remove_file(self.path_for(id)) {
            Ok(()) => {}
            Err(err) if err.kind() == io::ErrorKind::NotFound => {}
            Err(_) => return Err(ApiError::Store),
        }
        guard.remove(id);
        Ok(())
    }

    fn path_for(&self, id: &str) -> PathBuf {
        self.root.join("sessions").join(format!("{id}.json"))
    }

    fn write_session(&self, session: &Session) -> Result<(), ApiError> {
        let bytes = serde_json::to_vec_pretty(session).map_err(|_| ApiError::Store)?;
        atomic_write(&self.path_for(session.id()), &bytes).map_err(|_| ApiError::Store)
    }

    fn lock(&self) -> std::sync::MutexGuard<'_, HashMap<String, Session>> {
        self.sessions.lock().unwrap_or_else(|err| err.into_inner())
    }
}

fn valid_id(id: &str) -> bool {
    id.len() == 8
        && id
            .bytes()
            .all(|byte| byte.is_ascii_hexdigit() && !byte.is_ascii_uppercase())
}

fn new_id() -> String {
    let mut buf = [0u8; 4];
    random_bytes(&mut buf);
    buf.iter().map(|byte| format!("{byte:02x}")).collect()
}

fn atomic_write(path: &Path, bytes: &[u8]) -> io::Result<()> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }
    let tmp = path.with_extension("json.tmp");
    fs::write(&tmp, bytes)?;
    fs::rename(&tmp, path)?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::session::{Choice, Screen, SEATS};

    fn temp_root() -> PathBuf {
        let mut buf = [0u8; 4];
        random_bytes(&mut buf);
        let dir = std::env::temp_dir().join(format!(
            "drive-talk-{}-{:02x}{:02x}{:02x}{:02x}",
            std::process::id(),
            buf[0],
            buf[1],
            buf[2],
            buf[3]
        ));
        let _ = fs::remove_dir_all(&dir);
        dir
    }

    fn deck() -> Vec<Card> {
        (0..2)
            .map(|i| Card {
                tag: i.to_string(),
                title: format!("q-{i}"),
                a: "산".into(),
                b: "바다".into(),
            })
            .collect()
    }

    #[test]
    fn reload_keeps_open_round() {
        let dir = temp_root();
        let id = {
            let store = Store::open(&dir).unwrap();
            let session = store.create(deck()).unwrap();
            let id = session.id().to_string();
            store.update(&id, Session::begin).unwrap();
            store.update(&id, |session| session.hear("산")).unwrap();
            id
        };
        let store = Store::open(&dir).unwrap();
        let session = store.get(&id).unwrap();
        assert_eq!(session.screen(), Screen::Answer);
        assert_eq!(session.view().seats[0].choice, Some(Choice::A));
        assert_eq!(session.view().deck[0].a, "산");
        let _ = fs::remove_dir_all(&dir);
    }

    #[test]
    fn delete_does_not_come_back() {
        let dir = temp_root();
        let store = Store::open(&dir).unwrap();
        let id = store.create(deck()).unwrap().id().to_string();
        store.delete(&id).unwrap();
        assert!(matches!(store.get(&id), Err(ApiError::NotFound)));
        drop(store);
        let store = Store::open(&dir).unwrap();
        assert!(matches!(store.get(&id), Err(ApiError::NotFound)));
        assert!(matches!(store.get("../secret"), Err(ApiError::NotFound)));
        assert!(matches!(
            store.delete(&format!("../{}", SEATS[0])),
            Err(ApiError::NotFound)
        ));
        let _ = fs::remove_dir_all(&dir);
    }
}
