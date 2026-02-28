CREATE TABLE users (
    id                             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email                          VARCHAR(200) NOT NULL UNIQUE,
    password_hash                  VARCHAR(255) NOT NULL,
    full_name                      VARCHAR(200) NOT NULL,
    role                           VARCHAR(20)  NOT NULL CHECK (role IN ('TEACHER', 'ADMIN', 'STUDENT')),
    status                         VARCHAR(30)  NOT NULL DEFAULT 'PENDING_CONFIRMATION'
                                       CHECK (status IN ('PENDING_CONFIRMATION', 'ACTIVE', 'INACTIVE')),
    student_id                     UUID REFERENCES students(id) ON DELETE SET NULL,
    password_change_required       BOOLEAN NOT NULL DEFAULT FALSE,
    confirmation_token             VARCHAR(255) UNIQUE,
    confirmation_token_expires_at  TIMESTAMP WITH TIME ZONE,
    created_at                     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at                     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_email               ON users(email);
CREATE INDEX idx_users_student_id          ON users(student_id);
CREATE INDEX idx_users_confirmation_token  ON users(confirmation_token);

CREATE TABLE refresh_tokens (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash   VARCHAR(255) NOT NULL UNIQUE,
    expires_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked      BOOLEAN NOT NULL DEFAULT FALSE,
    device_info  VARCHAR(500),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user_id    ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens(token_hash);
