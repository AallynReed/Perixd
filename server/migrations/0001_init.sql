CREATE TABLE spaces (
    id TEXT PRIMARY KEY,
    auth_hash TEXT NOT NULL,
    seq INTEGER NOT NULL DEFAULT 0,
    created INTEGER NOT NULL
);

CREATE TABLE records (
    space TEXT NOT NULL REFERENCES spaces(id) ON DELETE CASCADE,
    id TEXT NOT NULL,
    t INTEGER NOT NULL,
    seq INTEGER NOT NULL,
    blob TEXT NOT NULL,
    PRIMARY KEY (space, id)
);

CREATE INDEX records_by_seq ON records(space, seq);

CREATE TABLE devices (
    space TEXT NOT NULL REFERENCES spaces(id) ON DELETE CASCADE,
    id TEXT NOT NULL,
    token TEXT,
    seen INTEGER NOT NULL,
    PRIMARY KEY (space, id)
);
