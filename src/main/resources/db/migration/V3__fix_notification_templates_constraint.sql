-- V3: Fix notification_templates unique constraint
-- The original constraint was on template_key only, but we need it on (template_key, channel, locale)
-- to allow different templates per channel

-- Drop the incorrect unique constraint on template_key alone (auto-generated name)
ALTER TABLE notification_templates DROP CONSTRAINT IF EXISTS notification_templates_template_key_key;

-- Drop any existing index on just template_key
DROP INDEX IF EXISTS notification_templates_template_key_key;

-- Drop the old composite unique index if it exists (we'll recreate as a constraint)
DROP INDEX IF EXISTS idx_notification_templates_key_channel_locale;

-- Add the proper composite unique constraint if it doesn't exist
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'notification_templates_key_channel_locale_unique'
    ) THEN
        ALTER TABLE notification_templates
            ADD CONSTRAINT notification_templates_key_channel_locale_unique
            UNIQUE (template_key, channel, locale);
    END IF;
END $$;

-- Re-insert the default templates that may have failed due to the constraint
-- Use ON CONFLICT to handle any that already exist
INSERT INTO notification_templates (template_key, channel, locale, subject_template, body_template) VALUES
    ('CLASS_SCHEDULED', 'EMAIL', 'en', 'Class Scheduled: {{date}}', 'Hello {{studentName}},\n\nYour class has been scheduled for {{date}} at {{time}}.\n\nBest regards'),
    ('CLASS_SCHEDULED', 'SMS', 'en', NULL, 'Hi {{studentName}}, your class is scheduled for {{date}} at {{time}}.'),
    ('CLASS_SCHEDULED', 'WHATSAPP', 'en', NULL, 'Hi {{studentName}}! Your class is scheduled for {{date}} at {{time}}.'),
    ('CLASS_CANCELLED', 'EMAIL', 'en', 'Class Cancelled: {{date}}', 'Hello {{studentName}},\n\nWe regret to inform you that your class scheduled for {{date}} at {{time}} has been cancelled.\n\nReason: {{reason}}\n\nBest regards'),
    ('CLASS_CANCELLED', 'SMS', 'en', NULL, 'Hi {{studentName}}, your class on {{date}} at {{time}} has been cancelled. Reason: {{reason}}'),
    ('CLASS_CANCELLED', 'WHATSAPP', 'en', NULL, 'Hi {{studentName}}, your class on {{date}} at {{time}} has been cancelled. Reason: {{reason}}'),
    ('CLASS_RESCHEDULED', 'EMAIL', 'en', 'Class Rescheduled', 'Hello {{studentName}},\n\nYour class has been rescheduled from {{oldDate}} to {{newDate}} at {{newTime}}.\n\nReason: {{reason}}\n\nBest regards'),
    ('CLASS_RESCHEDULED', 'SMS', 'en', NULL, 'Hi {{studentName}}, your class has been moved from {{oldDate}} to {{newDate}} at {{newTime}}.'),
    ('CLASS_RESCHEDULED', 'WHATSAPP', 'en', NULL, 'Hi {{studentName}}! Your class has been rescheduled from {{oldDate}} to {{newDate}} at {{newTime}}.'),
    ('CLASS_REMINDER', 'EMAIL', 'en', 'Reminder: Upcoming Class', 'Hello {{studentName}},\n\nThis is a reminder that you have a class scheduled for {{date}} at {{time}}.\n\nSee you soon!'),
    ('CLASS_REMINDER', 'SMS', 'en', NULL, 'Reminder: You have a class on {{date}} at {{time}}.'),
    ('CLASS_REMINDER', 'WHATSAPP', 'en', NULL, 'Reminder: You have a class on {{date}} at {{time}}.')
ON CONFLICT ON CONSTRAINT notification_templates_key_channel_locale_unique DO NOTHING;
