pub mod pages;
pub mod push;

use axum::extract::{DefaultBodyLimit, Path, Query, State};
use axum::http::{HeaderMap, StatusCode};
use axum::response::Html;
use axum::routing::{get, put};
use axum::{Json, Router};
use serde::{Deserialize, Serialize};
use serde_json::{Value, json};
use sha2::{Digest, Sha256};
use sqlx::SqlitePool;
use sqlx::sqlite::{SqliteConnectOptions, SqliteJournalMode, SqlitePoolOptions};
use std::path::PathBuf;
use std::str::FromStr;
use std::sync::Arc;
use std::time::{SystemTime, UNIX_EPOCH};

pub const PACKAGE: &str = "net.mildhope.perixd";
const PAGE: i64 = 500;
const MAX_BLOB: usize = 64 * 1024;
/// A space no device has used for this long is deleted.
pub const MAX_IDLE_MS: i64 = 365 * 24 * 60 * 60 * 1000;

pub struct Config {
    pub bind: String,
    pub data_dir: PathBuf,
    pub max_spaces: i64,
    pub max_records: i64,
    /// Path to the Firebase service account key; push is off without it.
    pub fcm_service_account: Option<PathBuf>,
    /// Path to the google-services.json whose client settings the app uses for push.
    pub fcm_client: Option<PathBuf>,
    /// Email address or URL shown on the privacy and deletion pages.
    pub contact: String,
}

impl Config {
    pub fn from_env() -> Config {
        let var = |name: &str| std::env::var(name).ok().filter(|v| !v.is_empty());
        Config {
            bind: var("PERIXD_BIND").unwrap_or_else(|| "0.0.0.0:8080".into()),
            data_dir: var("PERIXD_DATA").unwrap_or_else(|| "data".into()).into(),
            max_spaces: var("PERIXD_MAX_SPACES")
                .and_then(|v| v.parse().ok())
                .unwrap_or(50),
            max_records: var("PERIXD_MAX_RECORDS")
                .and_then(|v| v.parse().ok())
                .unwrap_or(20_000),
            fcm_service_account: var("PERIXD_FCM_SERVICE_ACCOUNT").map(Into::into),
            fcm_client: var("PERIXD_FCM_CLIENT").map(Into::into),
            contact: var("PERIXD_CONTACT")
                .unwrap_or_else(|| "https://github.com/AallynReed/Perixd/issues".into()),
        }
    }
}

/// The Firebase settings the app needs to receive pushes, read from google-services.json.
#[derive(Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct FcmClient {
    pub project_id: String,
    pub app_id: String,
    pub api_key: String,
    pub sender_id: String,
}

impl FcmClient {
    pub fn parse(json: &str) -> Result<FcmClient, String> {
        let v: Value = serde_json::from_str(json).map_err(|e| e.to_string())?;
        let client = v["client"]
            .as_array()
            .into_iter()
            .flatten()
            .find(|c| c["client_info"]["android_client_info"]["package_name"] == PACKAGE)
            .ok_or_else(|| format!("google-services.json has no Android app {PACKAGE}"))?;
        let field = |value: &Value, what: &str| {
            value
                .as_str()
                .map(String::from)
                .ok_or_else(|| format!("google-services.json lacks {what}"))
        };
        Ok(FcmClient {
            project_id: field(&v["project_info"]["project_id"], "project_id")?,
            app_id: field(
                &client["client_info"]["mobilesdk_app_id"],
                "mobilesdk_app_id",
            )?,
            api_key: field(&client["api_key"][0]["current_key"], "api_key")?,
            sender_id: field(&v["project_info"]["project_number"], "project_number")?,
        })
    }
}

#[derive(Clone)]
pub struct AppState {
    pub db: SqlitePool,
    pub config: Arc<Config>,
    pub fcm_client: Option<FcmClient>,
    pub push: Option<Arc<push::Push>>,
    writes: Arc<tokio::sync::Mutex<()>>,
}

impl AppState {
    pub async fn open(config: Config) -> Result<AppState, String> {
        std::fs::create_dir_all(&config.data_dir)
            .map_err(|e| format!("can't create {}: {e}", config.data_dir.display()))?;
        let options = SqliteConnectOptions::from_str(&format!(
            "sqlite://{}",
            config.data_dir.join("perixd.db").display()
        ))
        .map_err(|e| e.to_string())?
        .create_if_missing(true)
        .journal_mode(SqliteJournalMode::Wal)
        .foreign_keys(true);
        let db = SqlitePoolOptions::new()
            .max_connections(4)
            .connect_with(options)
            .await
            .map_err(|e| format!("can't open the database: {e}"))?;
        sqlx::migrate!("./migrations")
            .run(&db)
            .await
            .map_err(|e| format!("migration failed: {e}"))?;
        let fcm_client = match &config.fcm_client {
            Some(path) => Some(FcmClient::parse(&read(path)?)?),
            None => None,
        };
        let push = match &config.fcm_service_account {
            Some(path) => Some(Arc::new(push::Push::new(&read(path)?)?)),
            None => None,
        };
        Ok(AppState {
            db,
            config: Arc::new(config),
            fcm_client,
            push,
            writes: Default::default(),
        })
    }
}

fn read(path: &PathBuf) -> Result<String, String> {
    std::fs::read_to_string(path).map_err(|e| format!("can't read {}: {e}", path.display()))
}

pub fn app(state: AppState) -> Router {
    Router::new()
        .route("/", get(|s| page(s, pages::INDEX)))
        .route("/privacy", get(|s| page(s, pages::PRIVACY_EN)))
        .route("/privacy/pt", get(|s| page(s, pages::PRIVACY_PT)))
        .route("/delete", get(|s| page(s, pages::DELETE_EN)))
        .route("/delete/pt", get(|s| page(s, pages::DELETE_PT)))
        .route("/v1/config", get(config))
        .route("/v1/spaces/{space}", put(open_space).delete(delete_space))
        .route("/v1/spaces/{space}/records", get(pull).post(push_records))
        .route(
            "/v1/spaces/{space}/devices/{device}",
            put(register_device).delete(remove_device),
        )
        .layer(DefaultBodyLimit::max(4 * 1024 * 1024))
        .with_state(state)
}

async fn page(State(state): State<AppState>, template: &'static str) -> Html<String> {
    pages::render(template, &state.config.contact)
}

type ApiResult<T> = Result<T, (StatusCode, &'static str)>;

fn internal(e: sqlx::Error) -> (StatusCode, &'static str) {
    tracing::error!("database error: {e}");
    (StatusCode::INTERNAL_SERVER_ERROR, "database error")
}

pub fn now() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap()
        .as_millis() as i64
}

fn hash(token: &str) -> String {
    hex::encode(Sha256::digest(token.as_bytes()))
}

fn bearer(headers: &HeaderMap) -> ApiResult<&str> {
    headers
        .get("authorization")
        .and_then(|v| v.to_str().ok())
        .and_then(|v| v.strip_prefix("Bearer "))
        .filter(|t| t.len() >= 32 && t.len() <= 128)
        .ok_or((StatusCode::UNAUTHORIZED, "missing token"))
}

fn valid(id: &str, min: usize, max: usize) -> bool {
    (min..=max).contains(&id.len())
        && id
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || b == b'-' || b == b'_')
}

/// Checks the space exists and the token is its own.
async fn authorize(state: &AppState, space: &str, headers: &HeaderMap) -> ApiResult<()> {
    if !valid(space, 32, 32) {
        return Err((StatusCode::BAD_REQUEST, "bad space id"));
    }
    let token = bearer(headers)?;
    let stored: Option<String> = sqlx::query_scalar("SELECT auth_hash FROM spaces WHERE id = ?")
        .bind(space)
        .fetch_optional(&state.db)
        .await
        .map_err(internal)?;
    match stored {
        None => Err((StatusCode::NOT_FOUND, "no such space")),
        Some(h) if h == hash(token) => {
            sqlx::query("UPDATE spaces SET used = ? WHERE id = ?")
                .bind(now())
                .bind(space)
                .execute(&state.db)
                .await
                .map_err(internal)?;
            Ok(())
        }
        Some(_) => Err((StatusCode::FORBIDDEN, "wrong token")),
    }
}

async fn config(State(state): State<AppState>) -> Json<Value> {
    Json(json!({ "fcm": state.fcm_client }))
}

/// Creates the space on first use; later calls just check the token.
async fn open_space(
    State(state): State<AppState>,
    Path(space): Path<String>,
    headers: HeaderMap,
) -> ApiResult<StatusCode> {
    match authorize(&state, &space, &headers).await {
        Ok(()) => return Ok(StatusCode::OK),
        Err((StatusCode::NOT_FOUND, _)) => {}
        Err(e) => return Err(e),
    }
    let token = bearer(&headers)?;
    let _guard = state.writes.lock().await;
    let count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM spaces")
        .fetch_one(&state.db)
        .await
        .map_err(internal)?;
    if count >= state.config.max_spaces {
        return Err((StatusCode::INSUFFICIENT_STORAGE, "this server is full"));
    }
    sqlx::query("INSERT INTO spaces (id, auth_hash, created, used) VALUES (?, ?, ?, ?)")
        .bind(&space)
        .bind(hash(token))
        .bind(now())
        .bind(now())
        .execute(&state.db)
        .await
        .map_err(internal)?;
    Ok(StatusCode::CREATED)
}

async fn delete_space(
    State(state): State<AppState>,
    Path(space): Path<String>,
    headers: HeaderMap,
) -> ApiResult<StatusCode> {
    authorize(&state, &space, &headers).await?;
    let _guard = state.writes.lock().await;
    sqlx::query("DELETE FROM spaces WHERE id = ?")
        .bind(&space)
        .execute(&state.db)
        .await
        .map_err(internal)?;
    Ok(StatusCode::NO_CONTENT)
}

#[derive(Deserialize)]
struct Since {
    #[serde(default)]
    since: i64,
}

#[derive(Serialize, Deserialize, sqlx::FromRow)]
pub struct Record {
    pub id: String,
    pub t: i64,
    #[serde(default)]
    pub seq: i64,
    pub blob: String,
}

async fn pull(
    State(state): State<AppState>,
    Path(space): Path<String>,
    Query(q): Query<Since>,
    headers: HeaderMap,
) -> ApiResult<Json<Value>> {
    authorize(&state, &space, &headers).await?;
    let seq: i64 = sqlx::query_scalar("SELECT seq FROM spaces WHERE id = ?")
        .bind(&space)
        .fetch_one(&state.db)
        .await
        .map_err(internal)?;
    let records: Vec<Record> = sqlx::query_as(
        "SELECT id, t, seq, blob FROM records WHERE space = ? AND seq > ? ORDER BY seq LIMIT ?",
    )
    .bind(&space)
    .bind(q.since)
    .bind(PAGE)
    .fetch_all(&state.db)
    .await
    .map_err(internal)?;
    let more = records.len() as i64 == PAGE;
    Ok(Json(
        json!({ "seq": seq, "records": records, "more": more }),
    ))
}

#[derive(Deserialize)]
struct Upload {
    device: String,
    records: Vec<Record>,
}

/// Stores each record unless the server already has a newer one with the same id.
async fn push_records(
    State(state): State<AppState>,
    Path(space): Path<String>,
    headers: HeaderMap,
    Json(upload): Json<Upload>,
) -> ApiResult<Json<Value>> {
    authorize(&state, &space, &headers).await?;
    if !valid(&upload.device, 8, 64) {
        return Err((StatusCode::BAD_REQUEST, "bad device id"));
    }
    if upload.records.len() > PAGE as usize
        || upload
            .records
            .iter()
            .any(|r| !valid(&r.id, 16, 64) || r.blob.len() > MAX_BLOB)
    {
        return Err((
            StatusCode::PAYLOAD_TOO_LARGE,
            "too many or too large records",
        ));
    }
    let _guard = state.writes.lock().await;
    let mut tx = state.db.begin().await.map_err(internal)?;
    let mut seq: i64 = sqlx::query_scalar("SELECT seq FROM spaces WHERE id = ?")
        .bind(&space)
        .fetch_one(&mut *tx)
        .await
        .map_err(internal)?;
    let count: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM records WHERE space = ?")
        .bind(&space)
        .fetch_one(&mut *tx)
        .await
        .map_err(internal)?;
    if count + upload.records.len() as i64 > state.config.max_records {
        return Err((StatusCode::INSUFFICIENT_STORAGE, "too many records"));
    }
    let mut changed = 0;
    for r in &upload.records {
        let done = sqlx::query(
            "INSERT INTO records (space, id, t, seq, blob) VALUES (?, ?, ?, ?, ?) \
             ON CONFLICT(space, id) DO UPDATE SET t = excluded.t, seq = excluded.seq, blob = excluded.blob WHERE excluded.t > records.t",
        )
        .bind(&space).bind(&r.id).bind(r.t).bind(seq + 1).bind(&r.blob)
        .execute(&mut *tx).await.map_err(internal)?;
        if done.rows_affected() > 0 {
            seq += 1;
            changed += 1;
        }
    }
    sqlx::query("UPDATE spaces SET seq = ? WHERE id = ?")
        .bind(seq)
        .bind(&space)
        .execute(&mut *tx)
        .await
        .map_err(internal)?;
    sqlx::query("UPDATE devices SET seen = ? WHERE space = ? AND id = ?")
        .bind(now())
        .bind(&space)
        .bind(&upload.device)
        .execute(&mut *tx)
        .await
        .map_err(internal)?;
    tx.commit().await.map_err(internal)?;
    if changed > 0
        && let Some(push) = state.push.clone()
    {
        tokio::spawn(push::notify(
            state.db.clone(),
            push,
            space,
            upload.device,
            seq,
        ));
    }
    Ok(Json(json!({ "seq": seq, "changed": changed })))
}

#[derive(Deserialize)]
struct Device {
    token: Option<String>,
}

async fn register_device(
    State(state): State<AppState>,
    Path((space, device)): Path<(String, String)>,
    headers: HeaderMap,
    Json(body): Json<Device>,
) -> ApiResult<StatusCode> {
    authorize(&state, &space, &headers).await?;
    if !valid(&device, 8, 64) || body.token.as_ref().is_some_and(|t| t.len() > 4096) {
        return Err((StatusCode::BAD_REQUEST, "bad device"));
    }
    sqlx::query("INSERT INTO devices (space, id, token, seen) VALUES (?, ?, ?, ?) ON CONFLICT(space, id) DO UPDATE SET token = excluded.token, seen = excluded.seen")
        .bind(&space).bind(&device).bind(&body.token).bind(now())
        .execute(&state.db).await.map_err(internal)?;
    Ok(StatusCode::NO_CONTENT)
}

async fn remove_device(
    State(state): State<AppState>,
    Path((space, device)): Path<(String, String)>,
    headers: HeaderMap,
) -> ApiResult<StatusCode> {
    authorize(&state, &space, &headers).await?;
    sqlx::query("DELETE FROM devices WHERE space = ? AND id = ?")
        .bind(&space)
        .bind(&device)
        .execute(&state.db)
        .await
        .map_err(internal)?;
    Ok(StatusCode::NO_CONTENT)
}

/// Deletes spaces no device has used for [MAX_IDLE_MS], with everything in them.
pub async fn purge_idle(db: &SqlitePool) -> Result<u64, sqlx::Error> {
    Ok(sqlx::query("DELETE FROM spaces WHERE used < ?")
        .bind(now() - MAX_IDLE_MS)
        .execute(db)
        .await?
        .rows_affected())
}
