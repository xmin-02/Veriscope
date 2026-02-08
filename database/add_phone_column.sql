-- 사용자 테이블에 전화번호 컬럼 추가
ALTER TABLE users ADD COLUMN phone VARCHAR(15) NULL;

-- 전화번호에 인덱스 추가 (검색 성능 향상)
CREATE INDEX idx_phone ON users(phone);