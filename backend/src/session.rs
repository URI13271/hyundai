use std::collections::BTreeMap;
use std::path::Path;

use serde::{Deserialize, Serialize};

pub const SEATS: [&str; 4] = ["driver", "passenger", "rear_left", "rear_right"];
const ROUND: usize = 10;
const FALLBACK_DECK: &str = include_str!("../deck.json");

#[derive(Debug)]
pub enum ApiError {
    NotFound,
    Conflict(&'static str),
    Unprocessable(&'static str),
    BadRequest(&'static str),
    Store,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum Screen {
    Home,
    Shuffle,
    Seats,
    Read,
    Answer,
    Done,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Choice {
    A,
    B,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct Card {
    pub tag: String,
    pub title: String,
    pub a: String,
    pub b: String,
}

#[derive(Clone, Debug, PartialEq, Eq, Serialize, Deserialize)]
pub struct LogEntry {
    pub tag: String,
    pub title: String,
    pub a_label: String,
    pub b_label: String,
    pub a: u32,
    pub b: u32,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct Session {
    id: String,
    screen: Screen,
    index: usize,
    deck: Vec<Card>,
    joined: Vec<String>,
    picks: BTreeMap<String, Choice>,
    log: Vec<LogEntry>,
}

#[derive(Debug, Serialize)]
pub struct SeatOut {
    pub id: String,
    pub joined: bool,
    pub choice: Option<Choice>,
}

#[derive(Debug, PartialEq, Eq, Serialize)]
pub struct Counts {
    pub a: u32,
    pub b: u32,
}

#[derive(Debug, Serialize)]
pub struct View {
    pub id: String,
    pub screen: Screen,
    pub index: usize,
    pub deck: Vec<Card>,
    pub seats: Vec<SeatOut>,
    pub counts: Counts,
    pub log: Vec<LogEntry>,
}

pub fn load_deck(path: &Path) -> Result<Vec<Card>, String> {
    let (label, text) = match std::fs::read_to_string(path) {
        Ok(text) => (path.display().to_string(), text),
        Err(err)
            if err.kind() == std::io::ErrorKind::NotFound
                && std::env::var_os("DRIVE_TALK_DECK").is_none() =>
        {
            ("embedded deck".into(), FALLBACK_DECK.to_string())
        }
        Err(err) => return Err(format!("{}: {err}", path.display())),
    };
    let deck: Vec<Card> = serde_json::from_str(&text).map_err(|err| format!("{label}: {err}"))?;
    if deck.is_empty() {
        return Err(format!("{label}: empty"));
    }
    Ok(deck)
}

pub fn deal(mut deck: Vec<Card>) -> Vec<Card> {
    shuffle(&mut deck);
    deck.truncate(ROUND);
    deck
}

pub(crate) fn random_bytes(buf: &mut [u8]) {
    getrandom::fill(buf).expect("os random");
}

fn shuffle<T>(items: &mut [T]) {
    if items.len() < 2 {
        return;
    }
    let mut buf = [0u8; 8];
    for i in (1..items.len()).rev() {
        random_bytes(&mut buf);
        let j = (u64::from_ne_bytes(buf) as usize) % (i + 1);
        items.swap(i, j);
    }
}

impl Session {
    pub fn open(id: String, deck: Vec<Card>) -> Self {
        Self {
            id,
            screen: Screen::Shuffle,
            index: 0,
            deck,
            joined: SEATS.iter().map(|seat| (*seat).to_string()).collect(),
            picks: BTreeMap::new(),
            log: Vec::new(),
        }
    }

    pub fn id(&self) -> &str {
        &self.id
    }

    #[cfg(test)]
    pub fn screen(&self) -> Screen {
        self.screen
    }

    pub(crate) fn set_id(&mut self, id: String) {
        self.id = id;
    }

    pub fn view(&self) -> View {
        View {
            id: self.id.clone(),
            screen: self.screen,
            index: self.index,
            deck: self.deck.clone(),
            seats: SEATS
                .iter()
                .copied()
                .map(|id| SeatOut {
                    id: id.to_string(),
                    joined: self.is_joined(id),
                    choice: self.picks.get(id).copied(),
                })
                .collect(),
            counts: self.counts(),
            log: self.log.clone(),
        }
    }

    pub fn toggle_seat(&mut self, id: &str) -> Result<(), ApiError> {
        if !matches!(self.screen, Screen::Shuffle | Screen::Seats) {
            return Err(ApiError::Conflict("bad phase"));
        }
        if !SEATS.contains(&id) {
            return Err(ApiError::BadRequest("unknown seat"));
        }
        self.screen = Screen::Seats;
        if let Some(pos) = self.joined.iter().position(|seat| seat == id) {
            self.joined.remove(pos);
        } else {
            self.joined.push(id.to_string());
        }
        Ok(())
    }

    pub fn begin(&mut self) -> Result<(), ApiError> {
        if !matches!(self.screen, Screen::Shuffle | Screen::Seats) {
            return Err(ApiError::Conflict("bad phase"));
        }
        if !SEATS.iter().any(|id| self.is_joined(id)) {
            return Err(ApiError::Unprocessable("no seats joined"));
        }
        self.picks.clear();
        self.index = 0;
        self.screen = Screen::Read;
        Ok(())
    }

    pub fn ready(&mut self) -> Result<(), ApiError> {
        self.screen = match self.screen {
            Screen::Shuffle => Screen::Seats,
            Screen::Read => Screen::Answer,
            _ => return Err(ApiError::Conflict("bad phase")),
        };
        Ok(())
    }

    pub fn hear(&mut self, token: &str) -> Result<(), ApiError> {
        let seat = self
            .current_seat()
            .ok_or(ApiError::Conflict("no current turn"))?;
        let card = self
            .deck
            .get(self.index)
            .ok_or(ApiError::Conflict("no current turn"))?;
        let choice = parse_choice(token, card).ok_or(ApiError::BadRequest("bad choice"))?;
        self.picks.insert(seat, choice);
        self.screen = Screen::Answer;
        Ok(())
    }

    pub fn advance(&mut self) -> Result<(), ApiError> {
        if !matches!(self.screen, Screen::Read | Screen::Answer) {
            return Err(ApiError::Conflict("bad phase"));
        }
        let card = self
            .deck
            .get(self.index)
            .ok_or(ApiError::Conflict("bad phase"))?
            .clone();
        let counts = self.counts();
        self.log.push(LogEntry {
            tag: card.tag,
            title: card.title,
            a_label: card.a,
            b_label: card.b,
            a: counts.a,
            b: counts.b,
        });
        self.picks.clear();
        if self.index + 1 >= self.deck.len() {
            self.screen = Screen::Done;
        } else {
            self.index += 1;
            self.screen = Screen::Read;
        }
        Ok(())
    }

    pub fn replay(&mut self) -> Result<(), ApiError> {
        if !matches!(self.screen, Screen::Read | Screen::Answer) {
            return Err(ApiError::Conflict("bad phase"));
        }
        self.screen = Screen::Read;
        Ok(())
    }

    fn is_joined(&self, id: &str) -> bool {
        self.joined.iter().any(|seat| seat == id)
    }

    fn counts(&self) -> Counts {
        let mut counts = Counts { a: 0, b: 0 };
        for id in SEATS {
            if !self.is_joined(id) {
                continue;
            }
            match self.picks.get(id) {
                Some(Choice::A) => counts.a += 1,
                Some(Choice::B) => counts.b += 1,
                None => {}
            }
        }
        counts
    }

    // 차례는 운전석, 조수석, 뒷좌석 왼쪽, 뒷좌석 오른쪽 중 아직 고르지 않은 첫 참여 자리다.
    fn current_seat(&self) -> Option<String> {
        if !matches!(self.screen, Screen::Read | Screen::Answer) {
            return None;
        }
        SEATS
            .iter()
            .copied()
            .find(|id| self.is_joined(id) && !self.picks.contains_key(*id))
            .map(str::to_string)
    }
}

fn parse_choice(token: &str, card: &Card) -> Option<Choice> {
    match token.trim() {
        "a" | "A" => Some(Choice::A),
        "b" | "B" => Some(Choice::B),
        other if other == card.a => Some(Choice::A),
        other if other == card.b => Some(Choice::B),
        _ => None,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn card(tag: &str) -> Card {
        Card {
            tag: tag.into(),
            title: format!("q-{tag}"),
            a: format!("a-{tag}"),
            b: format!("b-{tag}"),
        }
    }

    fn opened() -> Session {
        Session::open(
            "abcd1234".into(),
            (0..3).map(|i| card(&i.to_string())).collect(),
        )
    }

    #[test]
    fn deal_keeps_ten() {
        let dealt = deal((0..12).map(|i| card(&i.to_string())).collect());
        assert_eq!(dealt.len(), 10);
    }

    #[test]
    fn view_lists_four_seats_in_order() {
        let view = opened().view();
        let ids: Vec<_> = view.seats.iter().map(|seat| seat.id.as_str()).collect();
        assert_eq!(ids, SEATS);
        assert!(view
            .seats
            .iter()
            .all(|seat| seat.joined && seat.choice.is_none()));
        assert_eq!(view.counts, Counts { a: 0, b: 0 });
        assert_eq!(view.screen, Screen::Shuffle);
    }

    #[test]
    fn unknown_seat_does_not_leave_shuffle() {
        let mut session = opened();
        assert!(matches!(
            session.toggle_seat("trunk"),
            Err(ApiError::BadRequest("unknown seat"))
        ));
        assert_eq!(session.screen(), Screen::Shuffle);
    }

    #[test]
    fn begin_rejects_empty_seats() {
        let mut session = opened();
        for id in SEATS {
            session.toggle_seat(id).unwrap();
        }
        assert!(matches!(
            session.begin(),
            Err(ApiError::Unprocessable("no seats joined"))
        ));
        assert_eq!(session.screen(), Screen::Seats);
    }

    #[test]
    fn hear_without_turn_conflicts() {
        let mut session = opened();
        assert!(matches!(
            session.hear("a"),
            Err(ApiError::Conflict("no current turn"))
        ));
    }

    #[test]
    fn bad_choice_does_not_consume_turn() {
        let mut session = opened();
        session.begin().unwrap();
        assert!(matches!(
            session.hear("nope"),
            Err(ApiError::BadRequest("bad choice"))
        ));
        assert!(session
            .view()
            .seats
            .iter()
            .all(|seat| seat.choice.is_none()));
    }

    #[test]
    fn turn_skips_unjoined_and_taken() {
        let mut session = opened();
        session.toggle_seat("driver").unwrap();
        session.toggle_seat("rear_left").unwrap();
        session.begin().unwrap();
        session.hear("a").unwrap();
        session.hear("b-0").unwrap();
        let view = session.view();
        assert_eq!(view.seats[1].choice, Some(Choice::A));
        assert_eq!(view.seats[3].choice, Some(Choice::B));
        assert_eq!(view.counts, Counts { a: 1, b: 1 });
        assert!(matches!(
            session.hear("a"),
            Err(ApiError::Conflict("no current turn"))
        ));
    }

    #[test]
    fn replay_keeps_picks() {
        let mut session = opened();
        session.begin().unwrap();
        session.hear("A").unwrap();
        session.replay().unwrap();
        assert_eq!(session.screen(), Screen::Read);
        assert_eq!(session.view().seats[0].choice, Some(Choice::A));
        session.hear("b").unwrap();
        assert_eq!(session.view().seats[1].choice, Some(Choice::B));
    }

    #[test]
    fn advance_skips_remaining_and_finishes() {
        let mut session = opened();
        session.begin().unwrap();
        session.hear("a-0").unwrap();
        session.advance().unwrap();
        assert_eq!(session.screen(), Screen::Read);
        assert_eq!(session.view().index, 1);
        assert_eq!(session.view().log[0].a_label, "a-0");
        assert_eq!(session.view().log[0].a, 1);
        assert_eq!(session.view().log[0].b, 0);
        assert!(session
            .view()
            .seats
            .iter()
            .all(|seat| seat.choice.is_none()));
        session.advance().unwrap();
        session.advance().unwrap();
        assert_eq!(session.screen(), Screen::Done);
        assert_eq!(session.view().log.len(), 3);
        assert!(matches!(
            session.advance(),
            Err(ApiError::Conflict("bad phase"))
        ));
    }

    #[test]
    fn ready_advances_shuffle_and_read_only() {
        let mut session = opened();
        session.ready().unwrap();
        assert_eq!(session.screen(), Screen::Seats);
        assert!(matches!(
            session.ready(),
            Err(ApiError::Conflict("bad phase"))
        ));
        session.begin().unwrap();
        session.ready().unwrap();
        assert_eq!(session.screen(), Screen::Answer);
    }
}
