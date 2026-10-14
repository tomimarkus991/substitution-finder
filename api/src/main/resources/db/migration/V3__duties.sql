-- Only the latest upload is kept; deleting it removes its duties.
CREATE TABLE duty_version (
    id          BIGSERIAL PRIMARY KEY,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    uploaded_by BIGINT      NOT NULL REFERENCES app_user (id),
    file_name   TEXT        NOT NULL,
    warnings    TEXT[]      NOT NULL DEFAULT '{}'
);

-- teacher_key is null until the surname is matched (several or no teachers with it).
CREATE TABLE duty (
    id             BIGSERIAL PRIMARY KEY,
    version_id     BIGINT   NOT NULL REFERENCES duty_version (id) ON DELETE CASCADE,
    weekday        SMALLINT NOT NULL CHECK (weekday BETWEEN 1 AND 5),
    period         SMALLINT NOT NULL CHECK (period BETWEEN 1 AND 7),
    kind           TEXT     NOT NULL CHECK (kind IN ('OVT', 'SVT')),
    starts         TIME     NOT NULL,
    ends           TIME     NOT NULL,
    class_group    TEXT     NOT NULL,
    position       TEXT     NOT NULL DEFAULT '',
    raw_name       TEXT     NOT NULL,
    teacher_key    TEXT,
    teacher_name   TEXT,
    candidate_keys TEXT[]   NOT NULL DEFAULT '{}'
);
CREATE INDEX duty_version_weekday ON duty (version_id, weekday);

-- Set on rows that cover an absent teacher's break duty; null for lessons.
ALTER TABLE substitution ADD COLUMN duty_label TEXT;
