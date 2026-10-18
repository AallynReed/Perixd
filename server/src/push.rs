use jsonwebtoken::{Algorithm, EncodingKey, Header, encode};
use serde::{Deserialize, Serialize};
use serde_json::json;
use sqlx::SqlitePool;
use std::sync::Arc;
use std::time::{Duration, Instant};
use tokio::sync::Mutex;

/// Sends Firebase Cloud Messaging "sync now" nudges; they carry a sequence number and nothing else.
pub struct Push {
    client: reqwest::Client,
    account: ServiceAccount,
    key: EncodingKey,
    token: Mutex<Option<(String, Instant)>>,
}

#[derive(Deserialize)]
struct ServiceAccount {
    project_id: String,
    client_email: String,
    private_key: String,
    token_uri: String,
}

#[derive(Serialize)]
struct Claims<'a> {
    iss: &'a str,
    scope: &'a str,
    aud: &'a str,
    iat: u64,
    exp: u64,
}

#[derive(Deserialize)]
struct AccessToken {
    access_token: String,
    expires_in: u64,
}

pub enum Sent {
    Delivered,
    /// The token no longer belongs to an installed app.
    Gone,
}

impl Push {
    pub fn new(service_account_json: &str) -> Result<Push, String> {
        let account: ServiceAccount = serde_json::from_str(service_account_json)
            .map_err(|e| format!("bad service account key: {e}"))?;
        let key = EncodingKey::from_rsa_pem(account.private_key.as_bytes())
            .map_err(|e| format!("bad service account private key: {e}"))?;
        let client = reqwest::Client::builder()
            .timeout(Duration::from_secs(15))
            .build()
            .map_err(|e| e.to_string())?;
        Ok(Push {
            client,
            account,
            key,
            token: Mutex::new(None),
        })
    }

    async fn access_token(&self) -> Result<String, String> {
        let mut cached = self.token.lock().await;
        if let Some((token, until)) = cached.as_ref()
            && Instant::now() < *until
        {
            return Ok(token.clone());
        }
        let iat = crate::now() as u64 / 1000;
        let claims = Claims {
            iss: &self.account.client_email,
            scope: "https://www.googleapis.com/auth/firebase.messaging",
            aud: &self.account.token_uri,
            iat,
            exp: iat + 3600,
        };
        let assertion = encode(&Header::new(Algorithm::RS256), &claims, &self.key)
            .map_err(|e| e.to_string())?;
        let response = self
            .client
            .post(&self.account.token_uri)
            .form(&[
                ("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer"),
                ("assertion", &assertion),
            ])
            .send()
            .await
            .map_err(|e| e.to_string())?;
        if !response.status().is_success() {
            return Err(format!(
                "token request failed: {}",
                response.text().await.unwrap_or_default()
            ));
        }
        let token: AccessToken = response.json().await.map_err(|e| e.to_string())?;
        *cached = Some((
            token.access_token.clone(),
            Instant::now() + Duration::from_secs(token.expires_in.saturating_sub(120)),
        ));
        Ok(token.access_token)
    }

    pub async fn send(&self, device_token: &str, seq: i64) -> Result<Sent, String> {
        let access = self.access_token().await?;
        let url = format!(
            "https://fcm.googleapis.com/v1/projects/{}/messages:send",
            self.account.project_id
        );
        let message = json!({
            "message": {
                "token": device_token,
                "data": { "seq": seq.to_string() },
                "android": { "priority": "HIGH", "ttl": "3600s" },
            }
        });
        let response = self
            .client
            .post(url)
            .bearer_auth(access)
            .json(&message)
            .send()
            .await
            .map_err(|e| e.to_string())?;
        let status = response.status();
        if status.is_success() {
            return Ok(Sent::Delivered);
        }
        let body = response.text().await.unwrap_or_default();
        if status.as_u16() == 404
            || body.contains("UNREGISTERED")
            || body.contains("registration-token-not-registered")
        {
            return Ok(Sent::Gone);
        }
        Err(format!("FCM {status}: {body}"))
    }
}

/// Nudges every other device in the space to pull, and forgets tokens Firebase no longer knows.
pub async fn notify(db: SqlitePool, push: Arc<Push>, space: String, from: String, seq: i64) {
    let devices: Vec<(String, String)> = match sqlx::query_as(
        "SELECT id, token FROM devices WHERE space = ? AND id != ? AND token IS NOT NULL",
    )
    .bind(&space)
    .bind(&from)
    .fetch_all(&db)
    .await
    {
        Ok(d) => d,
        Err(e) => return tracing::warn!("can't list devices: {e}"),
    };
    for (device, token) in devices {
        match push.send(&token, seq).await {
            Ok(Sent::Delivered) => {}
            Ok(Sent::Gone) => {
                let _ = sqlx::query("UPDATE devices SET token = NULL WHERE space = ? AND id = ?")
                    .bind(&space)
                    .bind(&device)
                    .execute(&db)
                    .await;
            }
            Err(e) => tracing::warn!("push failed: {e}"),
        }
    }
}
