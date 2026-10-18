# Perixd sync server

Lets a person's devices share their Perixd logs and settings. Everything is encrypted on the device with a key the
server never sees: it stores opaque record ids, change times and AES-GCM ciphertext, and keeps the newest version of
each record. Devices pair by scanning a QR code that carries the server address and the shared secret.

## Settings

| Variable | Default | |
|---|---|---|
| `PERIXD_BIND` | `0.0.0.0:8080` | Listen address |
| `PERIXD_DATA` | `data` | Folder for the SQLite database |
| `PERIXD_MAX_SPACES` | `50` | Most sync spaces the server accepts |
| `PERIXD_MAX_RECORDS` | `20000` | Most records per space |
| `PERIXD_FCM_SERVICE_ACCOUNT` | | Firebase service account key; enables push |
| `PERIXD_FCM_CLIENT` | | The project's `google-services.json`, handed to the app so it can receive pushes |

Without the Firebase settings sync still works: devices sync when opened, at reminder time, and every few minutes
while a reminder is showing. With them, a dose marked on one device clears the others within seconds. Pushes carry
only a sequence number.

## Firebase

1. Create a project at https://console.firebase.google.com (Google Analytics off).
2. Add an Android app with the package name `net.mildhope.perixd` and download `google-services.json`.
3. Under Project settings, Service accounts, generate a private key. Keep it secret.

## API

All space calls take `Authorization: Bearer <token>`; the first `PUT` creates the space with that token.

- `GET /v1/config`: push settings for the app, or `null`
- `PUT /v1/spaces/{space}`, `DELETE /v1/spaces/{space}`
- `GET /v1/spaces/{space}/records?since=<seq>`: changed records, 500 at a time
- `POST /v1/spaces/{space}/records`: `{"device", "records": [{"id", "t", "blob"}]}`
- `PUT /v1/spaces/{space}/devices/{device}`: `{"token"}` for push, `DELETE` to leave

## Build

```powershell
cargo test
docker build -t perixd-server .
```
