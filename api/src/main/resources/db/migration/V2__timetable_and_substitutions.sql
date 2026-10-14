CREATE TABLE timetable_version (
    id          BIGSERIAL PRIMARY KEY,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    uploaded_by BIGINT      NOT NULL REFERENCES app_user (id),
    file_name   TEXT        NOT NULL,
    active      BOOLEAN     NOT NULL DEFAULT FALSE,
    warnings    TEXT[]      NOT NULL DEFAULT '{}'
);
-- At most one active version
CREATE UNIQUE INDEX timetable_version_one_active ON timetable_version (active) WHERE active;

CREATE TABLE teacher (
    id         BIGSERIAL PRIMARY KEY,
    version_id BIGINT NOT NULL REFERENCES timetable_version (id) ON DELETE CASCADE,
    key        TEXT   NOT NULL,
    name       TEXT   NOT NULL,
    UNIQUE (version_id, key)
);

CREATE TABLE lesson (
    id         BIGSERIAL PRIMARY KEY,
    teacher_id BIGINT   NOT NULL REFERENCES teacher (id) ON DELETE CASCADE,
    weekday    SMALLINT NOT NULL CHECK (weekday BETWEEN 1 AND 5),
    period     SMALLINT NOT NULL CHECK (period BETWEEN 1 AND 7),
    classes    TEXT     NOT NULL,
    subject    TEXT     NOT NULL,
    room       TEXT     NOT NULL,
    note       TEXT     NOT NULL
);
CREATE INDEX lesson_teacher_day ON lesson (teacher_id, weekday);

CREATE TABLE placeholder_flag (
    teacher_key TEXT PRIMARY KEY
);

CREATE TABLE absence (
    id           BIGSERIAL PRIMARY KEY,
    date         DATE     NOT NULL,
    teacher_key  TEXT     NOT NULL,
    teacher_name TEXT     NOT NULL,
    from_period  SMALLINT NOT NULL CHECK (from_period BETWEEN 1 AND 7),
    to_period    SMALLINT NOT NULL CHECK (to_period BETWEEN 1 AND 7),
    created_by   BIGINT   NOT NULL REFERENCES app_user (id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (date, teacher_key),
    CHECK (from_period <= to_period)
);

CREATE TABLE substitution (
    id               BIGSERIAL PRIMARY KEY,
    absence_id       BIGINT   NOT NULL REFERENCES absence (id) ON DELETE CASCADE,
    period           SMALLINT NOT NULL CHECK (period BETWEEN 1 AND 7),
    classes          TEXT     NOT NULL,
    subject          TEXT     NOT NULL,
    substitute_keys  TEXT[]   NOT NULL DEFAULT '{}',
    substitute_names TEXT     NOT NULL DEFAULT '',
    free_text        TEXT     NOT NULL DEFAULT ''
);
CREATE INDEX substitution_absence ON substitution (absence_id);
