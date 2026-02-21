UPDATE tickets SET updated_at = created_at WHERE updated_at IS NULL;

ALTER TABLE tickets MODIFY updated_at DATETIME(6) NOT NULL;
