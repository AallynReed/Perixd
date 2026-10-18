use axum::body::Body;
use axum::http::{Request, StatusCode};
use http_body_util::BodyExt;
use perixd_server::{AppState, Config, FcmClient, app, purge_idle};
use serde_json::{Value, json};
use tower::ServiceExt;

const SPACE: &str = "0123456789abcdef0123456789abcdef";
const TOKEN: &str = "a-long-enough-random-token-for-the-space-0001";

async fn state(dir: &tempfile::TempDir) -> AppState {
    AppState::open(Config {
        bind: String::new(),
        data_dir: dir.path().into(),
        max_spaces: 2,
        max_records: 5,
        fcm_service_account: None,
        fcm_client: None,
        contact: "privacy@example.net".into(),
    })
    .await
    .unwrap()
}

async fn call(
    state: &AppState,
    method: &str,
    path: &str,
    token: Option<&str>,
    body: Option<Value>,
) -> (StatusCode, Value) {
    let mut request = Request::builder().method(method).uri(path);
    if let Some(t) = token {
        request = request.header("authorization", format!("Bearer {t}"));
    }
    let request = match body {
        Some(b) => request
            .header("content-type", "application/json")
            .body(Body::from(b.to_string()))
            .unwrap(),
        None => request.body(Body::empty()).unwrap(),
    };
    let response = app(state.clone()).oneshot(request).await.unwrap();
    let status = response.status();
    let bytes = response.into_body().collect().await.unwrap().to_bytes();
    (
        status,
        serde_json::from_slice(&bytes).unwrap_or(Value::Null),
    )
}

fn record(id: &str, t: i64, blob: &str) -> Value {
    json!({ "id": id, "t": t, "blob": blob })
}

#[tokio::test]
async fn spaces_are_created_once_and_guarded_by_their_token() {
    let dir = tempfile::tempdir().unwrap();
    let s = state(&dir).await;
    let path = format!("/v1/spaces/{SPACE}");
    assert_eq!(
        call(&s, "PUT", &path, Some(TOKEN), None).await.0,
        StatusCode::CREATED
    );
    assert_eq!(
        call(&s, "PUT", &path, Some(TOKEN), None).await.0,
        StatusCode::OK
    );
    assert_eq!(
        call(
            &s,
            "PUT",
            &path,
            Some("another-long-enough-token-that-is-wrong-000"),
            None
        )
        .await
        .0,
        StatusCode::FORBIDDEN
    );
    assert_eq!(
        call(&s, "GET", &format!("{path}/records"), None, None)
            .await
            .0,
        StatusCode::UNAUTHORIZED
    );
    assert_eq!(
        call(&s, "PUT", "/v1/spaces/not-a-space", Some(TOKEN), None)
            .await
            .0,
        StatusCode::BAD_REQUEST
    );
}

#[tokio::test]
async fn newest_record_wins_and_pulls_page_by_sequence() {
    let dir = tempfile::tempdir().unwrap();
    let s = state(&dir).await;
    let path = format!("/v1/spaces/{SPACE}");
    call(&s, "PUT", &path, Some(TOKEN), None).await;
    let records = format!("{path}/records");

    let (status, body) = call(&s, "POST", &records, Some(TOKEN), Some(json!({ "device": "phone-0001", "records": [record("record-id-aaaaaaaa", 10, "a1"), record("record-id-bbbbbbbb", 10, "b1")] }))).await;
    assert_eq!(status, StatusCode::OK);
    assert_eq!(body["seq"], 2);

    let (_, body) = call(&s, "POST", &records, Some(TOKEN), Some(json!({ "device": "tablet-0001", "records": [record("record-id-aaaaaaaa", 5, "stale"), record("record-id-bbbbbbbb", 20, "b2")] }))).await;
    assert_eq!(body["changed"], 1);
    assert_eq!(body["seq"], 3);

    let (_, body) = call(&s, "GET", &format!("{records}?since=0"), Some(TOKEN), None).await;
    let blobs: Vec<&str> = body["records"]
        .as_array()
        .unwrap()
        .iter()
        .map(|r| r["blob"].as_str().unwrap())
        .collect();
    assert_eq!(blobs, ["a1", "b2"]);
    let (_, body) = call(&s, "GET", &format!("{records}?since=2"), Some(TOKEN), None).await;
    assert_eq!(body["records"].as_array().unwrap().len(), 1);
    assert_eq!(body["more"], false);
}

#[tokio::test]
async fn limits_hold() {
    let dir = tempfile::tempdir().unwrap();
    let s = state(&dir).await;
    let path = format!("/v1/spaces/{SPACE}");
    call(&s, "PUT", &path, Some(TOKEN), None).await;
    let many: Vec<Value> = (0..6)
        .map(|i| record(&format!("record-id-{i:08}"), 1, "x"))
        .collect();
    let (status, _) = call(
        &s,
        "POST",
        &format!("{path}/records"),
        Some(TOKEN),
        Some(json!({ "device": "phone-0001", "records": many })),
    )
    .await;
    assert_eq!(status, StatusCode::INSUFFICIENT_STORAGE);
    let huge = "x".repeat(70_000);
    let (status, _) = call(
        &s,
        "POST",
        &format!("{path}/records"),
        Some(TOKEN),
        Some(
            json!({ "device": "phone-0001", "records": [record("record-id-aaaaaaaa", 1, &huge)] }),
        ),
    )
    .await;
    assert_eq!(status, StatusCode::PAYLOAD_TOO_LARGE);
    call(
        &s,
        "PUT",
        "/v1/spaces/11111111111111111111111111111111",
        Some(TOKEN),
        None,
    )
    .await;
    let (status, _) = call(
        &s,
        "PUT",
        "/v1/spaces/22222222222222222222222222222222",
        Some(TOKEN),
        None,
    )
    .await;
    assert_eq!(status, StatusCode::INSUFFICIENT_STORAGE);
}

#[tokio::test]
async fn devices_register_and_deleting_the_space_removes_everything() {
    let dir = tempfile::tempdir().unwrap();
    let s = state(&dir).await;
    let path = format!("/v1/spaces/{SPACE}");
    call(&s, "PUT", &path, Some(TOKEN), None).await;
    assert_eq!(
        call(
            &s,
            "PUT",
            &format!("{path}/devices/phone-0001"),
            Some(TOKEN),
            Some(json!({ "token": "fcm-token" }))
        )
        .await
        .0,
        StatusCode::NO_CONTENT
    );
    call(
        &s,
        "POST",
        &format!("{path}/records"),
        Some(TOKEN),
        Some(json!({ "device": "phone-0001", "records": [record("record-id-aaaaaaaa", 1, "x")] })),
    )
    .await;
    assert_eq!(
        call(&s, "DELETE", &path, Some(TOKEN), None).await.0,
        StatusCode::NO_CONTENT
    );
    assert_eq!(
        call(&s, "GET", &format!("{path}/records"), Some(TOKEN), None)
            .await
            .0,
        StatusCode::NOT_FOUND
    );
    let left: i64 = sqlx::query_scalar(
        "SELECT (SELECT COUNT(*) FROM records) + (SELECT COUNT(*) FROM devices)",
    )
    .fetch_one(&s.db)
    .await
    .unwrap();
    assert_eq!(left, 0);
}

#[tokio::test]
async fn config_reports_push_settings() {
    let dir = tempfile::tempdir().unwrap();
    let s = state(&dir).await;
    assert_eq!(
        call(&s, "GET", "/v1/config", None, None).await.1,
        json!({ "fcm": null })
    );
    let client = FcmClient::parse(&json!({
        "project_info": { "project_number": "1234", "project_id": "perixd-test" },
        "client": [
            { "client_info": { "mobilesdk_app_id": "1:1234:android:other", "android_client_info": { "package_name": "other.app" } }, "api_key": [{ "current_key": "k0" }] },
            { "client_info": { "mobilesdk_app_id": "1:1234:android:abc", "android_client_info": { "package_name": "net.mildhope.perixd" } }, "api_key": [{ "current_key": "k1" }] }
        ]
    }).to_string()).unwrap();
    assert_eq!(
        (
            client.app_id.as_str(),
            client.api_key.as_str(),
            client.sender_id.as_str()
        ),
        ("1:1234:android:abc", "k1", "1234")
    );
}

#[tokio::test]
async fn pages_show_the_contact() {
    let dir = tempfile::tempdir().unwrap();
    let s = state(&dir).await;
    for path in ["/", "/privacy", "/privacy/pt", "/delete", "/delete/pt"] {
        let response = app(s.clone())
            .oneshot(Request::get(path).body(Body::empty()).unwrap())
            .await
            .unwrap();
        assert_eq!(response.status(), StatusCode::OK, "{path}");
        let html = String::from_utf8(
            response
                .into_body()
                .collect()
                .await
                .unwrap()
                .to_bytes()
                .to_vec(),
        )
        .unwrap();
        assert!(!html.contains("{{"), "{path} has an unfilled placeholder");
        if path != "/" {
            assert!(html.contains("mailto:privacy@example.net"), "{path}");
        }
    }
}

#[tokio::test]
async fn idle_spaces_are_purged() {
    let dir = tempfile::tempdir().unwrap();
    let s = state(&dir).await;
    let path = format!("/v1/spaces/{SPACE}");
    call(&s, "PUT", &path, Some(TOKEN), None).await;
    call(
        &s,
        "POST",
        &format!("{path}/records"),
        Some(TOKEN),
        Some(json!({ "device": "phone-0001", "records": [record("record-id-aaaaaaaa", 1, "x")] })),
    )
    .await;
    assert_eq!(purge_idle(&s.db).await.unwrap(), 0);
    sqlx::query("UPDATE spaces SET used = ?")
        .bind(perixd_server::now() - perixd_server::MAX_IDLE_MS - 1)
        .execute(&s.db)
        .await
        .unwrap();
    assert_eq!(purge_idle(&s.db).await.unwrap(), 1);
    let left: i64 = sqlx::query_scalar("SELECT COUNT(*) FROM records")
        .fetch_one(&s.db)
        .await
        .unwrap();
    assert_eq!(left, 0);
}
