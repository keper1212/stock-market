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
    locked_cash BIGINT NOT NULL DEFAULT 0, -- 미체결 매수 주문으로 잠긴 예수금
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_accounts_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT chk_accounts_locked_cash CHECK (locked_cash >= 0),
    CONSTRAINT chk_accounts_cash_balance CHECK (cash_balance >= 0)
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
    locked_quantity BIGINT NOT NULL DEFAULT 0, -- 미체결 매도 주문으로 잠긴 수량
    average_cost DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_user_stocks_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_user_stocks_code FOREIGN KEY (stock_code) REFERENCES stocks(stock_code) ON DELETE CASCADE,
    CONSTRAINT uq_user_stock_pair UNIQUE (user_id, stock_code), -- 복합 유니크 제약조건 (내부 인덱스 자동 생성)
    CONSTRAINT chk_user_stocks_quantity CHECK (quantity >= 0),
    CONSTRAINT chk_user_stocks_locked_quantity CHECK (locked_quantity >= 0 AND locked_quantity <= quantity)
);

-- 5. Orders (주문 원장 테이블: 접수/부분체결/완전체결/취소 상태 추적)
CREATE TABLE orders (
    order_id UUID PRIMARY KEY, -- 애플리케이션에서 UUID 생성
    user_id BIGINT NOT NULL,
    stock_code VARCHAR(20) NOT NULL,
    client_order_id VARCHAR(100) NOT NULL, -- 멱등키 (사용자별 유니크)
    order_type VARCHAR(4) NOT NULL, -- BUY / SELL
    price BIGINT NOT NULL, -- 지정가 (원)
    quantity BIGINT NOT NULL, -- 원주문 수량
    remaining_quantity BIGINT NOT NULL, -- 미체결 잔량
    status VARCHAR(20) NOT NULL, -- ACCEPTED / PARTIALLY_FILLED / CANCEL_REQUESTED / FILLED / CANCELED / REJECTED
    accepted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), -- 서버 접수 시각
    cancel_client_id VARCHAR(100),
    cancel_requested_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_orders_stock FOREIGN KEY (stock_code) REFERENCES stocks(stock_code),
    CONSTRAINT uq_orders_user_client_order UNIQUE (user_id, client_order_id),
    CONSTRAINT chk_orders_type CHECK (order_type IN ('BUY', 'SELL')),
    CONSTRAINT chk_orders_price CHECK (price > 0),
    CONSTRAINT chk_orders_quantity CHECK (quantity > 0),
    CONSTRAINT chk_orders_remaining_quantity CHECK (remaining_quantity >= 0 AND remaining_quantity <= quantity),
    CONSTRAINT chk_orders_status CHECK (status IN ('ACCEPTED', 'PARTIALLY_FILLED', 'CANCEL_REQUESTED', 'FILLED', 'CANCELED', 'REJECTED'))
);

-- 6. Trades (최종 체결 내역/영수증 테이블)
CREATE TABLE trades (
    trade_id BIGSERIAL PRIMARY KEY,
    stock_code VARCHAR(20) NOT NULL,
    buyer_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    buy_order_id UUID,
    sell_order_id UUID,
    trade_price BIGINT NOT NULL,
    trade_quantity BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_trades_code FOREIGN KEY (stock_code) REFERENCES stocks(stock_code),
    CONSTRAINT fk_trades_buyer FOREIGN KEY (buyer_id) REFERENCES users(user_id),
    CONSTRAINT fk_trades_seller FOREIGN KEY (seller_id) REFERENCES users(user_id),
    CONSTRAINT fk_trades_buy_order FOREIGN KEY (buy_order_id) REFERENCES orders(order_id),
    CONSTRAINT fk_trades_sell_order FOREIGN KEY (sell_order_id) REFERENCES orders(order_id),
    CONSTRAINT chk_trades_price CHECK (trade_price > 0),
    CONSTRAINT chk_trades_quantity CHECK (trade_quantity > 0)
);

-- 7. Outbox_Events (트랜잭션 아웃박스: DB 커밋 이후 Kafka 발행 보장)
CREATE TABLE outbox_events (
    event_id UUID PRIMARY KEY, -- 애플리케이션에서 UUID 생성
    aggregate_type VARCHAR(40) NOT NULL, -- 예: ORDER, TRADE
    aggregate_id VARCHAR(100) NOT NULL, -- 예: order_id
    event_type VARCHAR(80) NOT NULL, -- 예: ORDER_ACCEPTED
    topic VARCHAR(100) NOT NULL, -- 예: order-events
    partition_key VARCHAR(100) NOT NULL, -- Kafka key
    payload JSONB NOT NULL, -- 이벤트 페이로드
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING / SENT / FAILED
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ,
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);

-- 조회/매칭/재시도 성능을 위한 인덱스들
CREATE INDEX idx_orders_user_created_at ON orders (user_id, created_at DESC);
CREATE INDEX idx_orders_stock_status_accepted_at ON orders (stock_code, status, accepted_at);
CREATE INDEX idx_orders_status_accepted_at ON orders (status, accepted_at);

CREATE INDEX idx_trades_buyer ON trades (buyer_id);
CREATE INDEX idx_trades_seller ON trades (seller_id);
CREATE INDEX idx_trades_buy_order ON trades (buy_order_id);
CREATE INDEX idx_trades_sell_order ON trades (sell_order_id);

CREATE INDEX idx_outbox_pending_created_at ON outbox_events (status, created_at);
CREATE INDEX idx_outbox_aggregate ON outbox_events (aggregate_type, aggregate_id);
