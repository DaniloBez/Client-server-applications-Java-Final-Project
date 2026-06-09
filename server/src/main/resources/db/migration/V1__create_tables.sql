CREATE TYPE user_role AS ENUM ('PLAYER', 'ADMIN');

CREATE TABLE users
(
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255)       NOT NULL,
    role          user_role                          DEFAULT 'PLAYER',
    match_count   INTEGER CHECK ( match_count >= 0 ) DEFAULT 0,
    elo_rating    INTEGER CHECK ( elo_rating > 0 )   DEFAULT 1000,
    is_banned     BOOLEAN                            DEFAULT FALSE,
    created_at    TIMESTAMP WITH TIME ZONE           DEFAULT CURRENT_TIMESTAMP
);

CREATE TYPE match_status AS ENUM ('COMPLETED', 'TECHNICAL_WIN', 'IN_PROGRESS');

CREATE TABLE matches
(
    id            BIGSERIAL PRIMARY KEY,
    player1_id    BIGINT REFERENCES users (id) NOT NULL,
    player2_id    BIGINT REFERENCES users (id) NOT NULL,
    player1_score INTEGER                               DEFAULT 0,
    player2_score INTEGER                               DEFAULT 0,
    winner_id     BIGINT REFERENCES users (id)          DEFAULT NULL,
    status        match_status                 NOT NULL DEFAULT 'IN_PROGRESS',
    started_at    TIMESTAMP WITH TIME ZONE              DEFAULT CURRENT_TIMESTAMP,
    ended_at      TIMESTAMP WITH TIME ZONE              DEFAULT NULL
);