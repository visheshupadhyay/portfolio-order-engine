CREATE TABLE orders (
    id VARCHAR(100) PRIMARY KEY,
    status VARCHAR(20) NOT NULL CHECK (status IN ('CREATED', 'PAID')),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE order_items (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id VARCHAR(100) NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),

    CONSTRAINT order_items_order_id_fk
        FOREIGN KEY (order_id)
        REFERENCES orders(id)
);

CREATE TABLE outbox_events (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL DEFAULT 'ORDER_PAID',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    attempt_count INTEGER NOT NULL DEFAULT 0
        CHECK (attempt_count >= 0),
    next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP,
    last_error TEXT,

    CONSTRAINT outbox_events_order_id_fk
        FOREIGN KEY (order_id)
        REFERENCES orders(id)
);

CREATE INDEX idx_outbox_events_status_next_attempt_at_id
ON outbox_events (status, next_attempt_at, id);

CREATE INDEX idx_order_items_order_id
ON order_items (order_id);

CREATE INDEX idx_orders_status_id
ON orders (status, id);
