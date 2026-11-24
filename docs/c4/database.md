<!-- generated from 8b44d9dcceaa, 2026-10-15 -->
<!-- c4-map config abedddd81c0a -->

> **Agent context map.** Dense on purpose and not laid out for viewing; labels may overlap when rendered. Diagrams for people: [docs/architecture/](../architecture/).

# Database schema

Generated from 4 SQL migrations in `api/src/main/resources/db/migration` (latest: `V4__teacher_name_override.sql`). Columns only: no defaults or checks. PK primary key, FK foreign key, UK unique.

```mermaid
erDiagram
  absence {
    bigserial id PK
    date date
    text teacher_key
    text teacher_name
    smallint from_period
    smallint to_period
    bigint created_by FK
    timestamptz created_at
  }
  app_user {
    bigserial id PK
    text google_sub UK
    text email
    text name "nullable"
    timestamptz created_at
  }
  duty {
    bigserial id PK
    bigint version_id FK
    smallint weekday
    smallint period
    text kind
    time starts
    time ends
    text class_group
    text position
    text raw_name
    text teacher_key "nullable"
    text teacher_name "nullable"
    text_array candidate_keys
  }
  duty_version {
    bigserial id PK
    timestamptz uploaded_at
    bigint uploaded_by FK
    text file_name
    text_array warnings
  }
  lesson {
    bigserial id PK
    bigint teacher_id FK
    smallint weekday
    smallint period
    text classes
    text subject
    text room
    text note
  }
  placeholder_flag {
    text teacher_key PK
  }
  substitution {
    bigserial id PK
    bigint absence_id FK
    smallint period
    text classes
    text subject
    text_array substitute_keys
    text substitute_names
    text free_text
    text duty_label "nullable"
  }
  teacher {
    bigserial id PK
    bigint version_id FK
    text key
    text name
  }
  teacher_name_override {
    text teacher_key PK
    text display_name
  }
  timetable_version {
    bigserial id PK
    timestamptz uploaded_at
    bigint uploaded_by FK
    text file_name
    boolean active
    text_array warnings
  }
  absence ||--o{ substitution : "absence_id"
  app_user ||--o{ absence : "created_by"
  app_user ||--o{ duty_version : "uploaded_by"
  app_user ||--o{ timetable_version : "uploaded_by"
  duty_version ||--o{ duty : "version_id"
  teacher ||--o{ lesson : "teacher_id"
  timetable_version ||--o{ teacher : "version_id"
```

Composite unique keys: `absence` (date, teacher_key); `teacher` (version_id, key).
