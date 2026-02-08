-- Veriscope 사용자 관리 데이터베이스 스키마

-- 데이터베이스 생성
CREATE DATABASE IF NOT EXISTS veriscope_db;
USE veriscope_db;

-- 사용자 테이블
CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    email_verified BOOLEAN DEFAULT FALSE,
    verification_token VARCHAR(255) NULL,
    reset_token VARCHAR(255) NULL,
    reset_token_expires TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_email (email),
    INDEX idx_verification_token (verification_token),
    INDEX idx_reset_token (reset_token)
);

-- 뉴스 평가 기록 테이블 
CREATE TABLE news_evaluations (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    news_url VARCHAR(2048) NOT NULL,
    evaluation_score DECIMAL(3,2) NOT NULL,
    evaluation_result TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_created_at (created_at)
);

-- 세션 테이블 (선택사항)
CREATE TABLE user_sessions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    session_token VARCHAR(255) UNIQUE NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_session_token (session_token),
    INDEX idx_user_id (user_id),
    INDEX idx_expires_at (expires_at)
);

-- 초기 테스트 데이터 (개발용)
INSERT INTO users (name, email, password, email_verified) VALUES 
('데모 사용자', 'demo@example.com', 'demo123', TRUE);

-- 권한 설정 (실제 배포시 조정 필요)
-- CREATE USER 'veriscope_user'@'localhost' IDENTIFIED BY 'your_secure_password';
-- GRANT SELECT, INSERT, UPDATE, DELETE ON veriscope_db.* TO 'veriscope_user'@'localhost';
-- FLUSH PRIVILEGES;