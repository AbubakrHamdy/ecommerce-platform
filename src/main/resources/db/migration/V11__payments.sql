ALTER TABLE payments
    ADD COLUMN currency            VARCHAR(3),
    ADD COLUMN provider_session_id VARCHAR(255),
    ADD COLUMN checkout_url        VARCHAR(1000),
    ADD COLUMN expires_at          TIMESTAMP,
    ADD COLUMN review_note         VARCHAR(500);

UPDATE payments SET currency = 'EGP';
ALTER TABLE payments ALTER COLUMN currency SET NOT NULL;

-- Webhooks identify the payment by the provider's session id.
CREATE UNIQUE INDEX uq_payments_provider_session ON payments (provider, provider_session_id)
    WHERE provider_session_id IS NOT NULL;

-- Providers retry and sometimes duplicate webhooks; each event is applied at most once.
CREATE TABLE processed_webhook_events (
    provider    VARCHAR(20)  NOT NULL,
    event_id    VARCHAR(255) NOT NULL,
    received_at TIMESTAMP    NOT NULL,
    PRIMARY KEY (provider, event_id)
);
