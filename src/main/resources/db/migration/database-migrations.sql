-- ========================================
-- V1__Create_users_table.sql
-- ========================================

CREATE TABLE users (
    telegram_id BIGINT PRIMARY KEY,
    username VARCHAR(100),
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    subscribed BOOLEAN NOT NULL DEFAULT FALSE,
    registered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_active TIMESTAMP
);

CREATE INDEX idx_users_subscribed ON users(subscribed);
CREATE INDEX idx_users_registered_at ON users(registered_at);
CREATE INDEX idx_users_last_active ON users(last_active);

COMMENT ON TABLE users IS 'Таблица пользователей Telegram бота';

-- ========================================
-- V2__Create_moderators_table.sql
-- ========================================

CREATE TABLE moderators (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    telegram_id BIGINT UNIQUE NOT NULL,
    username VARCHAR(100),
    first_name VARCHAR(100),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    added_by BIGINT,
    moderation_count INT NOT NULL DEFAULT 0
);

CREATE INDEX idx_moderators_telegram_id ON moderators(telegram_id);
CREATE INDEX idx_moderators_active ON moderators(active);
CREATE INDEX idx_moderators_added_at ON moderators(added_at);

COMMENT ON TABLE moderators IS 'Таблица модераторов';

-- ========================================
-- V3__Create_wolf_images_table.sql
-- ========================================

CREATE TABLE wolf_images (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    file_data LONGBLOB NOT NULL,
    file_size BIGINT,
    mime_type VARCHAR(100),
    uploaded_by BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    moderated_at TIMESTAMP,
    moderated_by BIGINT,
    moderation_reason VARCHAR(500),
    send_count INT NOT NULL DEFAULT 0,
    last_sent TIMESTAMP,
    
    CONSTRAINT fk_images_user FOREIGN KEY (uploaded_by) REFERENCES users(telegram_id) ON DELETE CASCADE,
    CONSTRAINT fk_images_moderator FOREIGN KEY (moderated_by) REFERENCES moderators(id) ON DELETE SET NULL,
    CONSTRAINT chk_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'BLOCKED'))
);

CREATE INDEX idx_images_status ON wolf_images(status);
CREATE INDEX idx_images_uploaded_by ON wolf_images(uploaded_by);
CREATE INDEX idx_images_uploaded_at ON wolf_images(uploaded_at);
CREATE INDEX idx_images_moderated_at ON wolf_images(moderated_at);
CREATE INDEX idx_images_last_sent ON wolf_images(last_sent);
CREATE INDEX idx_images_send_optimization ON wolf_images(status, send_count, last_sent);

COMMENT ON TABLE wolf_images IS 'Таблица изображений волков';

-- ========================================
-- V4__Create_schedules_table.sql
-- ========================================

CREATE TABLE schedules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    cron_expression VARCHAR(100) NOT NULL,
    description VARCHAR(200),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_executed TIMESTAMP,
    execution_count INT NOT NULL DEFAULT 0,
    
    CONSTRAINT fk_schedules_user FOREIGN KEY (user_id) REFERENCES users(telegram_id) ON DELETE CASCADE
);

CREATE INDEX idx_schedules_user_id ON schedules(user_id);
CREATE INDEX idx_schedules_active ON schedules(active);
CREATE INDEX idx_schedules_created_at ON schedules(created_at);
CREATE INDEX idx_schedules_last_executed ON schedules(last_executed);

COMMENT ON TABLE schedules IS 'Таблица расписаний отправки изображений';

-- ========================================
-- V5__Create_indexes.sql
-- ========================================

-- Дополнительные индексы для оптимизации
CREATE INDEX IF NOT EXISTS idx_images_approved_last_sent 
ON wolf_images(status, last_sent) 
WHERE status = 'APPROVED';

CREATE INDEX IF NOT EXISTS idx_schedules_active_user 
ON schedules(active, user_id, cron_expression) 
WHERE active = TRUE;

CREATE INDEX IF NOT EXISTS idx_users_subscribed_active 
ON users(subscribed, last_active) 
WHERE subscribed = TRUE;
