-- 1. Users (유저 기본 테이블)
CREATE TABLE users (
    user_id BIGSERIAL PRIMARY KEY,
    email VARCHAR(100) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 2. Accounts (자산/예수금 테이블 - 유저와 1:1 관계)
CREATE TABLE accounts (
    account_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT UNIQUE NOT NULL,
    cash_balance BIGINT NOT NULL DEFAULT 10000000, -- 초기 가상 자산 1천만 원
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_accounts_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- 3. Stocks (상장 주식 마스터 테이블)
CREATE TABLE stocks (
    stock_code VARCHAR(20) PRIMARY KEY,
    stock_name VARCHAR(50) UNIQUE NOT NULL,
    stock_name_en VARCHAR(100) UNIQUE NOT NULL,
    base_price BIGINT NOT NULL,
    is_trading BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 4. User_Stocks (보유 주식 자산 테이블 - 유저 한 명당 종목 중복 방지)
CREATE TABLE user_stocks (
    user_stock_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    stock_code VARCHAR(20) NOT NULL,
    quantity BIGINT NOT NULL DEFAULT 0,
    average_cost DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_user_stocks_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_user_stocks_code FOREIGN KEY (stock_code) REFERENCES stocks(stock_code) ON DELETE CASCADE,
    CONSTRAINT uq_user_stock_pair UNIQUE (user_id, stock_code) -- 복합 유니크 제약조건 (내부 인덱스 자동 생성)
);

-- 5. Trades (최종 체결 내역/영수증 테이블)
CREATE TABLE trades (
    trade_id BIGSERIAL PRIMARY KEY,
    stock_code VARCHAR(20) NOT NULL,
    buyer_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    trade_price BIGINT NOT NULL,
    trade_quantity BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_trades_code FOREIGN KEY (stock_code) REFERENCES stocks(stock_code),
    CONSTRAINT fk_trades_buyer FOREIGN KEY (buyer_id) REFERENCES users(user_id),
    CONSTRAINT fk_trades_seller FOREIGN KEY (seller_id) REFERENCES users(user_id)
);

-- 마이페이지 매수/매도 거래내역 조회를 위한 단일 인덱스들 (Full Scan 방어선)
CREATE INDEX idx_trades_buyer ON trades (buyer_id);
CREATE INDEX idx_trades_seller ON trades (seller_id);