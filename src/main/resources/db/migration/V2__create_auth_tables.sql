CREATE TABLE account (
     id               uuid         PRIMARY KEY,
     email            varchar(254) NOT NULL,
     name             varchar(150) NOT NULL,
     password_hash    varchar(255) NOT NULL,
     role             varchar(20)  NOT NULL,
     status           varchar(20)  NOT NULL,
     cpf_encrypted    bytea        NOT NULL,
     cpf_key_version  smallint     NOT NULL,
     cpf_hmac         bytea        NOT NULL,
     created_at       timestamptz  NOT NULL DEFAULT now(),
     updated_at       timestamptz  NOT NULL DEFAULT now(),
     CONSTRAINT uq_account_email UNIQUE (email),
     CONSTRAINT uq_account_cpf_hmac UNIQUE (cpf_hmac),
     CONSTRAINT ck_account_email_lowercase CHECK (email = lower(email)),
     CONSTRAINT ck_account_role CHECK (role IN ('BUYER', 'ORGANIZER', 'ADMIN')),
     CONSTRAINT ck_account_status CHECK (status IN ('PENDING_EMAIL', 'ACTIVE', 'SUSPENDED'))
);

CREATE TABLE email_verification_token (
    id          uuid        PRIMARY KEY,
    account_id  uuid        NOT NULL REFERENCES account (id),
    token_hash  bytea       NOT NULL,
    expires_at  timestamptz NOT NULL,
    used_at     timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_email_verification_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_email_verification_token_account ON email_verification_token (account_id);

CREATE TABLE organizer_profile (
    account_id        uuid         PRIMARY KEY REFERENCES account (id),
    public_name       varchar(150) NOT NULL,
    status            varchar(20)  NOT NULL,
    decided_by        uuid         REFERENCES account (id),
    decided_at        timestamptz,
    rejection_reason  varchar(500),
    created_at        timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_organizer_profile_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_organizer_profile_decision CHECK (
    (status = 'PENDING' AND decided_by IS NULL AND decided_at IS NULL)
    OR (status <> 'PENDING' AND decided_by IS NOT NULL AND decided_at IS NOT NULL)
    )
);

CREATE TABLE refresh_token (
    id          uuid        PRIMARY KEY,
    account_id  uuid        NOT NULL REFERENCES account (id),
    family_id   uuid        NOT NULL,
    token_hash  bytea       NOT NULL,
    expires_at  timestamptz NOT NULL,
    used_at     timestamptz,
    revoked_at  timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_token_account ON refresh_token (account_id);
CREATE INDEX idx_refresh_token_family ON refresh_token (family_id);