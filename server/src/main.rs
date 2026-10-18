use perixd_server::{AppState, Config, app};

/// The Perixd sync server. Settings come from PERIXD_* environment variables; see the README.
#[tokio::main]
async fn main() {
    tracing_subscriber::fmt::init();
    let state = AppState::open(Config::from_env()).await.unwrap_or_else(|e| {
        eprintln!("{e}");
        std::process::exit(1);
    });
    let bind = state.config.bind.clone();
    let listener = tokio::net::TcpListener::bind(&bind).await.unwrap_or_else(|e| panic!("cannot bind {bind}: {e}"));
    tracing::info!("listening on {bind}, push {}", if state.push.is_some() { "on" } else { "off" });
    axum::serve(listener, app(state))
        .with_graceful_shutdown(async {
            let _ = tokio::signal::ctrl_c().await;
        })
        .await
        .expect("server stopped");
}
