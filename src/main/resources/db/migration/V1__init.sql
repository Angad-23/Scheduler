CREATE TABLE users (
  id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  full_name     VARCHAR(100) NOT NULL,
  email         VARCHAR(190) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  role          VARCHAR(20)  NOT NULL,
  enabled       BIT(1)       NOT NULL,
  created_at    DATETIME(6)  NOT NULL,
  CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE verification_tokens (
  id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
  token      VARCHAR(64) NOT NULL,
  user_id    BIGINT      NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  CONSTRAINT uk_tokens_token UNIQUE (token),
  CONSTRAINT fk_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE proctors (
  id        BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  full_name VARCHAR(100) NOT NULL,
  email     VARCHAR(190) NOT NULL,
  active    BIT(1)       NOT NULL
);

CREATE TABLE exam_sessions (
  id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  student_id       BIGINT       NOT NULL,
  title            VARCHAR(150) NOT NULL,
  starts_at        DATETIME(6)  NOT NULL,   -- always UTC
  time_zone        VARCHAR(60)  NOT NULL,   -- the student's zone, for display
  duration_minutes INT          NOT NULL,
  notes            VARCHAR(500) NULL,
  status           VARCHAR(20)  NOT NULL,
  proctor_id       BIGINT       NULL,
  created_at       DATETIME(6)  NOT NULL,
  CONSTRAINT fk_sessions_student FOREIGN KEY (student_id) REFERENCES users (id),
  CONSTRAINT fk_sessions_proctor FOREIGN KEY (proctor_id) REFERENCES proctors (id)
);
CREATE INDEX ix_sessions_starts_at ON exam_sessions (starts_at);
CREATE INDEX ix_sessions_status ON exam_sessions (status);

CREATE TABLE notifications (
  id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  recipient_id BIGINT       NOT NULL,
  message      VARCHAR(300) NOT NULL,
  is_read      BIT(1)       NOT NULL,
  created_at   DATETIME(6)  NOT NULL,
  CONSTRAINT fk_notifications_user FOREIGN KEY (recipient_id) REFERENCES users (id)
);
CREATE INDEX ix_notifications_recipient ON notifications (recipient_id, is_read);
