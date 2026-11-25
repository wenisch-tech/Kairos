ALTER TABLE notification_provider ADD COLUMN IF NOT EXISTS mattermost_webhook_url VARCHAR(2048);
ALTER TABLE notification_provider ADD COLUMN IF NOT EXISTS mattermost_channel     VARCHAR(255);
ALTER TABLE notification_provider ADD COLUMN IF NOT EXISTS mattermost_username    VARCHAR(255);
ALTER TABLE notification_provider ADD COLUMN IF NOT EXISTS ignore_tls             BOOLEAN DEFAULT FALSE;
