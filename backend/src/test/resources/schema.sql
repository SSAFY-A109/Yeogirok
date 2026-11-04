-- 테스트(H2, PostgreSQL 모드) 스키마. 스프링 부트가 내장 DB 에서 자동으로 실행한다.
-- users 는 ERD 초안을 따른다. H2 에는 PostgreSQL ENUM 타입이 없어 auth_provider·user_status 를 VARCHAR 도메인으로 둔다.

CREATE DOMAIN auth_provider AS VARCHAR(10);
CREATE DOMAIN user_status AS VARCHAR(10);

-- 1. 회원
CREATE TABLE users (
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
