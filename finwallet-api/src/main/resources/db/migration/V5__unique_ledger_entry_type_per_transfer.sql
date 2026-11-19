-- Before deploying to an existing database, check for duplicate entry types:
-- SELECT transfer_id, entry_type, COUNT(*) FROM ledger_entries
-- GROUP BY transfer_id, entry_type HAVING COUNT(*) > 1;
-- Investigate and reconcile any duplicates before migration. This migration
-- fails if duplicates exist and does not change historical ledger rows.
ALTER TABLE ledger_entries
    ADD CONSTRAINT uq_ledger_entries_transfer_type UNIQUE (transfer_id, entry_type);
