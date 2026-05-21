CREATE TABLE users (
    id             UUID         PRIMARY KEY,
    email          VARCHAR(255) NOT NULL UNIQUE,
    username       VARCHAR(255) NOT NULL UNIQUE,
    password       VARCHAR(255),
    nickname       VARCHAR(255) NOT NULL,
    is_blacklisted BOOLEAN      NOT NULL DEFAULT FALSE,
    is_verified    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP    NOT NULL,
    updated_at     TIMESTAMP    NOT NULL
);

CREATE TABLE roles (
    id   BIGSERIAL    PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE
);

CREATE TABLE user_roles (
    user_id UUID   NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE social_accounts (
    id          BIGSERIAL    PRIMARY KEY,
    provider    VARCHAR(255) NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    email       VARCHAR(255),
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE
);
