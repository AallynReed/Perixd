FROM rust:1.97-bookworm AS build
WORKDIR /src
COPY Cargo.toml Cargo.lock ./
COPY migrations migrations
COPY pages pages
COPY src src
RUN cargo build --release --locked

FROM debian:bookworm-slim
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --home-dir /data perixd
COPY --from=build /src/target/release/perixd-server /usr/local/bin/perixd-server
USER 10001
ENV PERIXD_BIND=0.0.0.0:8080 PERIXD_DATA=/data
VOLUME /data
EXPOSE 8080
ENTRYPOINT ["perixd-server"]
