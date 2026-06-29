-- ================================================================
-- Inward CTS — create cts_users table + seed login users
-- Run ONCE in the inward Supabase project (ldrkwtfmtwbtbhxvbrpe)
-- ================================================================

CREATE TABLE IF NOT EXISTS cts_users (
    id               BIGSERIAL PRIMARY KEY,
    username         VARCHAR(50)  NOT NULL UNIQUE,
    password_hash    VARCHAR(255) NOT NULL,
    full_name        VARCHAR(100) NOT NULL,
    role_label       VARCHAR(50),
    email            VARCHAR(100),
    mobile           VARCHAR(15),
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                       CHECK (status IN ('PENDING','ACTIVE','REJECTED',
                                         'INACTIVE','LOCKED','DISABLED','TERMINATED')),
    role_id          BIGINT,
    failed_attempts  INT          NOT NULL DEFAULT 0,
    locked_until     TIMESTAMP,
    last_login       TIMESTAMP,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_users_username ON cts_users(username);
CREATE INDEX IF NOT EXISTS idx_users_status   ON cts_users(status);

-- ── Seed the 3 inward login users (strong passwords) ──────────────
--   maker_in / Maker@2026#   → MAKER
--   tv1_in   / Tv1@2026#     → TV1
--   tv2_in   / Tv2@2026#     → TV2
INSERT INTO cts_users
    (username, password_hash, full_name, role_label, status, failed_attempts)
VALUES
    ('maker_in',
     '$2a$10$oLcbPfUGdiYVIfPe5/Hgs.RBu5wzikubyV.NFN90Lo1fgXAqj4iV.',
     'Inward Maker User', 'MAKER', 'ACTIVE', 0),
    ('tv1_in',
     '$2a$10$QriMwKkoCpqcRlcuw.qKPuRF2SER58KdsVZij8PHt92GhwUNIgb5S',
     'Inward TV1 Checker', 'TV1', 'ACTIVE', 0),
    ('tv2_in',
     '$2a$10$0SPiU7NGp8MjrymMkR/y2OMr.DDzNDArtufSizNP4eWZR5Q2Hh.b.',
     'Inward TV2 Checker', 'TV2', 'ACTIVE', 0)
ON CONFLICT (username) DO UPDATE
SET password_hash   = EXCLUDED.password_hash,
    role_label      = EXCLUDED.role_label,
    status          = 'ACTIVE',
    failed_attempts = 0;