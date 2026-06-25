-- ============================================================
-- navbharat-cts5  |  Full DB Schema  (drop-and-recreate)
-- ============================================================

-- Drop in reverse FK order
DROP TABLE IF EXISTS public.micr_repair      CASCADE;
DROP TABLE IF EXISTS public.inward_cheque    CASCADE;
DROP TABLE IF EXISTS public.inward_batch     CASCADE;

-- ── inward_batch ──────────────────────────────────────────────────────────
CREATE TABLE public.inward_batch (
    id                 BIGSERIAL     NOT NULL,
    batch_id           VARCHAR(255)  NOT NULL,
    batch_status       VARCHAR(50)   NOT NULL DEFAULT 'Pending',
    created_at         TIMESTAMP     NOT NULL DEFAULT NOW(),
    total_cheques      INTEGER,
    success_count      INTEGER,
    micr_repair_count  INTEGER,

    CONSTRAINT inward_batch_pkey          PRIMARY KEY (id),
    CONSTRAINT uk_inward_batch_batch_id   UNIQUE      (batch_id),
    CONSTRAINT chk_batch_status           CHECK       (batch_status IN
        ('Pending', 'At_Micr_Service', 'At_Checker_Queue', 'Cleared'))
) TABLESPACE pg_default;

-- ── inward_cheque ─────────────────────────────────────────────────────────
CREATE TABLE public.inward_cheque (
    id                 BIGSERIAL        NOT NULL,
    batch_id           BIGINT           NOT NULL,

    cheque_no          VARCHAR(255)     NOT NULL,
    micr_code          VARCHAR(255),
    amount             NUMERIC(15, 2),
    amount_in_words    VARCHAR(255),
    account_no         VARCHAR(255),
    ifsc_code          VARCHAR(255),
    presenting_bank    VARCHAR(255),
    branch_name        VARCHAR(255),
    payee_name         VARCHAR(255),
    drawer_name        VARCHAR(255),
    transaction_code   VARCHAR(255),

    cheque_status      VARCHAR(50)      NOT NULL DEFAULT 'Normal',
    error_reason       VARCHAR(255),
    front_image_path   VARCHAR(255),
    rear_image_path    VARCHAR(255),
    cbs_validation     VARCHAR(50),

    cheque_date        TIMESTAMP,
    clearing_date      TIMESTAMP,
    created_at         TIMESTAMP        DEFAULT NOW(),

    CONSTRAINT inward_cheque_pkey              PRIMARY KEY (id),
    CONSTRAINT uk_inward_cheque_cheque_no      UNIQUE      (cheque_no),
    CONSTRAINT fk_inward_cheque_batch          FOREIGN KEY (batch_id)
                                               REFERENCES public.inward_batch (id),
    CONSTRAINT chk_cheque_status               CHECK (cheque_status  IN ('Normal', 'Micr_error')),
    CONSTRAINT chk_cbs_validation              CHECK (cbs_validation IN ('Valid',  'Invalid'))
) TABLESPACE pg_default;

CREATE INDEX IF NOT EXISTS idx_cheque_batch   ON public.inward_cheque USING btree (batch_id)    TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS idx_cheque_no      ON public.inward_cheque USING btree (cheque_no)   TABLESPACE pg_default;
CREATE INDEX IF NOT EXISTS idx_cheque_micr    ON public.inward_cheque USING btree (micr_code)   TABLESPACE pg_default;

-- ── micr_repair ───────────────────────────────────────────────────────────
CREATE TABLE public.micr_repair (
    repair_id           BIGSERIAL    NOT NULL,
    inward_cheque_id    BIGINT       NOT NULL,

    original_micr       VARCHAR(255),
    corrected_micr      VARCHAR(255),
    original_cheque_no  VARCHAR(255),
    corrected_cheque_no VARCHAR(255),
    original_bank_code  VARCHAR(255),
    corrected_bank_code VARCHAR(255),
    original_branch_code  VARCHAR(255),
    corrected_branch_code VARCHAR(255),

    CONSTRAINT micr_repair_pkey              PRIMARY KEY (repair_id),
    CONSTRAINT uk_micr_repair_cheque_id      UNIQUE      (inward_cheque_id),
    CONSTRAINT fk_micr_repair_inward_cheque  FOREIGN KEY (inward_cheque_id)
                                             REFERENCES public.inward_cheque (id)
) TABLESPACE pg_default;

-- ── ALTER TABLE equivalents (if tables already exist, run these instead) ──
-- ALTER TABLE public.inward_batch
--     ADD COLUMN IF NOT EXISTS batch_status VARCHAR(50) NOT NULL DEFAULT 'Pending',
--     ADD CONSTRAINT chk_batch_status CHECK (batch_status IN
--         ('Pending','At_Micr_Service','At_Checker_Queue','Cleared'));

-- ALTER TABLE public.inward_cheque
--     ADD COLUMN IF NOT EXISTS cheque_no         VARCHAR(255),
--     ADD COLUMN IF NOT EXISTS micr_code         VARCHAR(255),
--     ADD COLUMN IF NOT EXISTS amount            NUMERIC(15,2),
--     ADD COLUMN IF NOT EXISTS amount_in_words   VARCHAR(255),
--     ADD COLUMN IF NOT EXISTS account_no        VARCHAR(255),
--     ADD COLUMN IF NOT EXISTS ifsc_code         VARCHAR(255),
--     ADD COLUMN IF NOT EXISTS presenting_bank   VARCHAR(255),
--     ADD COLUMN IF NOT EXISTS branch_name       VARCHAR(255),
--     ADD COLUMN IF NOT EXISTS payee_name        VARCHAR(255),
--     ADD COLUMN IF NOT EXISTS drawer_name       VARCHAR(255),
--     ADD COLUMN IF NOT EXISTS transaction_code  VARCHAR(255),
--     ADD COLUMN IF NOT EXISTS cheque_status     VARCHAR(50) NOT NULL DEFAULT 'Normal',
--     ADD COLUMN IF NOT EXISTS error_reason      VARCHAR(255),
--     ADD COLUMN IF NOT EXISTS front_image_path  VARCHAR(255),
--     ADD COLUMN IF NOT EXISTS rear_image_path   VARCHAR(255),
--     ADD COLUMN IF NOT EXISTS cbs_validation    VARCHAR(50),
--     ADD COLUMN IF NOT EXISTS cheque_date       TIMESTAMP,
--     ADD COLUMN IF NOT EXISTS clearing_date     TIMESTAMP,
--     ADD COLUMN IF NOT EXISTS created_at        TIMESTAMP DEFAULT NOW(),
--     ADD CONSTRAINT chk_cheque_status  CHECK (cheque_status  IN ('Normal','Micr_error')),
--     ADD CONSTRAINT chk_cbs_validation CHECK (cbs_validation IN ('Valid','Invalid'));
