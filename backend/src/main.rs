mod session;
mod store;

use std::path::Path;
use std::sync::Arc;

use axum::extract::{Path as UrlPath, State};
use axum::http::StatusCode;
use axum::routing::{get, post};
use axum::{Json, Router};
use serde::Deserialize;

use session::{ApiError, View};
use store::Store;

#[derive(Clone)]
struct App {
    store: Arc<Store>,
    deck: Arc<Vec<session::Card>>,
}

#[derive(Deserialize)]
struct SeatBody {
    id: String,
}

#[derive(Deserialize)]
struct HearBody {
    choice: String,
}

#[tokio::main]
async fn main() {
    if let Err(err) = run().await {
        eprintln!("drive-talk: {err}");
        std::process::exit(1);
    }
}

async fn run() -> Result<(), Box<dyn std::error::Error>> {
    let port = env_port()?;
    let bind = std::env::var("BIND").unwrap_or_else(|_| "127.0.0.1".into());
    let deck_path = std::env::var("DRIVE_TALK_DECK").unwrap_or_else(|_| "deck.json".into());
    let data = std::env::var("DRIVE_TALK_DATA").unwrap_or_else(|_| "data".into());
    if !Path::new(&deck_path).is_file() && std::env::var_os("DRIVE_TALK_DECK").is_none() {
        eprintln!("deck file missing, using embedded deck");
    }
    let deck = session::load_deck(Path::new(&deck_path))?;
    let store = Store::open(data)?;
    let restored = store.len();
    let app = App {
        store: Arc::new(store),
        deck: Arc::new(deck),
    };
    let router = router(app);
    let listener = tokio::net::TcpListener::bind(format!("{bind}:{port}")).await?;
    let local = listener.local_addr()?;
    eprintln!("restored {restored} session(s)");
    eprintln!("listening on http://{local}");
    axum::serve(listener, router)
        .with_graceful_shutdown(async {
            let _ = tokio::signal::ctrl_c().await;
        })
        .await?;
    Ok(())
}

fn env_port() -> Result<u16, String> {
    match std::env::var("PORT") {
        Ok(value) => value.parse().map_err(|_| format!("bad PORT: {value}")),
        Err(_) => Ok(8080),
    }
}

fn router(app: App) -> Router {
    Router::new()
        .route("/v1/sessions", post(create))
        .route("/v1/sessions/{id}", get(show).delete(remove))
        .route("/v1/sessions/{id}/seats", post(seats))
        .route("/v1/sessions/{id}/begin", post(begin))
        .route("/v1/sessions/{id}/hear", post(hear))
        .route("/v1/sessions/{id}/next", post(advance))
        .route("/v1/sessions/{id}/replay", post(replay))
        .route("/v1/sessions/{id}/ready", post(ready))
        .with_state(app)
}

async fn create(State(app): State<App>) -> Result<Json<View>, ApiError> {
    let session = app.store.create(session::deal((*app.deck).clone()))?;
    Ok(Json(session.view()))
}

async fn show(
    State(app): State<App>,
    UrlPath(id): UrlPath<String>,
) -> Result<Json<View>, ApiError> {
    Ok(Json(app.store.get(&id)?.view()))
}

async fn remove(
    State(app): State<App>,
    UrlPath(id): UrlPath<String>,
) -> Result<StatusCode, ApiError> {
    app.store.delete(&id)?;
    Ok(StatusCode::NO_CONTENT)
}

async fn seats(
    State(app): State<App>,
    UrlPath(id): UrlPath<String>,
    Json(body): Json<SeatBody>,
) -> Result<Json<View>, ApiError> {
    let session = app
        .store
        .update(&id, |session| session.toggle_seat(&body.id))?;
    Ok(Json(session.view()))
}

async fn begin(
    State(app): State<App>,
    UrlPath(id): UrlPath<String>,
) -> Result<Json<View>, ApiError> {
    Ok(Json(app.store.update(&id, session::Session::begin)?.view()))
}

async fn hear(
    State(app): State<App>,
    UrlPath(id): UrlPath<String>,
    Json(body): Json<HearBody>,
) -> Result<Json<View>, ApiError> {
    let session = app
        .store
        .update(&id, |session| session.hear(&body.choice))?;
    Ok(Json(session.view()))
}

async fn advance(
    State(app): State<App>,
    UrlPath(id): UrlPath<String>,
) -> Result<Json<View>, ApiError> {
    Ok(Json(
        app.store.update(&id, session::Session::advance)?.view(),
    ))
}

async fn replay(
    State(app): State<App>,
    UrlPath(id): UrlPath<String>,
) -> Result<Json<View>, ApiError> {
    Ok(Json(
        app.store.update(&id, session::Session::replay)?.view(),
    ))
}

async fn ready(
    State(app): State<App>,
    UrlPath(id): UrlPath<String>,
) -> Result<Json<View>, ApiError> {
    Ok(Json(app.store.update(&id, session::Session::ready)?.view()))
}

impl axum::response::IntoResponse for ApiError {
    fn into_response(self) -> axum::response::Response {
        let (status, error) = match self {
            ApiError::NotFound => (StatusCode::NOT_FOUND, "not found"),
            ApiError::Conflict(error) => (StatusCode::CONFLICT, error),
            ApiError::Unprocessable(error) => (StatusCode::UNPROCESSABLE_ENTITY, error),
            ApiError::BadRequest(error) => (StatusCode::BAD_REQUEST, error),
            ApiError::Store => (StatusCode::INTERNAL_SERVER_ERROR, "store failed"),
        };
        (status, Json(serde_json::json!({ "error": error }))).into_response()
    }
}
