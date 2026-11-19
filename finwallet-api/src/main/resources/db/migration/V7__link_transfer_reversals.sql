ALTER TABLE transfers
    ADD COLUMN reversal_of_transfer_id BIGINT,
    ALTER COLUMN idempotency_key DROP NOT NULL,
    ADD CONSTRAINT fk_transfers_reversal_original
        FOREIGN KEY (reversal_of_transfer_id) REFERENCES transfers (id),
    ADD CONSTRAINT uq_transfers_reversal_original UNIQUE (reversal_of_transfer_id),
    ADD CONSTRAINT chk_transfers_reversal_shape CHECK (
        (reversal_of_transfer_id IS NULL AND idempotency_key IS NOT NULL)
        OR (reversal_of_transfer_id IS NOT NULL AND idempotency_key IS NULL
            AND reversal_of_transfer_id <> id AND status = 'COMPLETED')
    );
