-- 테스트(H2, PostgreSQL 모드) 스키마. 스프링 부트가 내장 DB 에서 자동으로 실행한다.
-- 같은 메모리 DB 를 쓰는 스프링 컨텍스트가 여럿 뜨면 이 파일이 컨텍스트마다 다시 돈다. 그래서 모두 IF NOT EXISTS 로 만든다.
-- users 는 ERD 초안을 따른다. H2 에는 PostgreSQL ENUM 타입이 없어 auth_provider·user_status 를 VARCHAR 도메인으로 둔다.

CREATE DOMAIN IF NOT EXISTS auth_provider AS VARCHAR(10);
CREATE DOMAIN IF NOT EXISTS user_status AS VARCHAR(10);

-- 1. 회원
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    provider auth_provider NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    email VARCHAR(320),
    nickname VARCHAR(50) NOT NULL,
    profile_image_url TEXT,
    status user_status NOT NULL DEFAULT 'ACTIVE',
    withdrawn_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (provider, provider_subject)
);

-- 20. Android 푸시 토큰(API-095·096). 노션 ERD DDL 과 같다(H2 에 TIMESTAMPTZ 가 없어 풀어 쓴다).
CREATE TABLE IF NOT EXISTS user_push_tokens (
    token TEXT PRIMARY KEY CHECK (btrim(token) <> ''),
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    platform TEXT NOT NULL CHECK (platform = 'ANDROID'),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_user_push_tokens_user ON user_push_tokens (user_id);
