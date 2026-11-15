CREATE TABLE user_credentials(
    user_id  UUID    PRIMARY KEY,
    email    VARCHAR NOT NULL UNIQUE,
    password VARCHAR NOT NULL
);

-- One row per logged-in device. Deleting a user deletes their sessions.
CREATE TABLE jwt_refresh_token(
    jti        UUID        PRIMARY KEY,
    token      VARCHAR     NOT NULL UNIQUE,
    expiration TIMESTAMPTZ NOT NULL,
    last_used  TIMESTAMPTZ NOT NULL,
    user_agent VARCHAR     NOT NULL,
    ip_address VARCHAR     NOT NULL,
    user_id    UUID        NOT NULL REFERENCES user_credentials(user_id) ON DELETE CASCADE
);

CREATE INDEX jwt_refresh_token_user_id_idx ON jwt_refresh_token(user_id);
