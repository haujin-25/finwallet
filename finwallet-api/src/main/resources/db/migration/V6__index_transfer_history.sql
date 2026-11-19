CREATE INDEX idx_transfers_sender_history ON transfers (sender_wallet_id, created_at DESC, id DESC);
CREATE INDEX idx_transfers_recipient_history ON transfers (recipient_wallet_id, created_at DESC, id DESC);

-- The wallet ID remains the leading column on each replacement index.
DROP INDEX idx_transfers_sender_wallet_id;
DROP INDEX idx_transfers_recipient_wallet_id;
