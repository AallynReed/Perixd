use perixd_server::{AppState, Config, app, purge_idle};

/// The Perixd sync server. Settings come from PERIXD_* environment variables; see the README.
#[tokio::main]
async fn main() {
    tracing_subscriber::fmt::init();
    let state = AppState::open(Config::from_env())
        .await
        .unwrap_or_else(|e| {
            eprintln!("{e}");
            std::process::exit(1);
        });
    let bind = state.config.bind.clone();
    let listener = tokio::net::TcpListener::bind(&bind)
        .await
        .unwrap_or_else(|e| panic!("cannot bind {bind}: {e}"));
    tracing::info!(
        "listening on {bind}, push {}",
        if state.push.is_some() { "on" } else { "off" }
    );
    let db = state.db.clone();
    tokio::spawn(async move {
        loop {
            match purge_idle(&db).await {
                Ok(0) => {}
                Ok(n) => tracing::info!("deleted {n} idle spaces"),
                Err(e) => tracing::warn!("idle space cleanup failed: {e}"),
            }
            tokio::time::sleep(std::time::Duration::from_secs(60 * 60)).await;
        }
    });
    axum::serve(listener, app(state))
        .with_graceful_shutdown(async {
            let _ = tokio::signal::ctrl_c().await;
        })
        .await
        .expect("server stopped");
}
