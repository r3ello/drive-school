-- V2: Add notification support to students and create notifications outbox table

-- ============================================================================
-- STUDENT NOTIFICATION PREFERENCES
-- ============================================================================

-- Preferred notification channel
ALTER TABLE students ADD COLUMN preferred_notification_channel VARCHAR(20) NOT NULL DEFAULT 'NONE';
ALTER TABLE students ADD CONSTRAINT students_notification_channel_check
    CHECK (preferred_notification_channel IN ('NONE', 'EMAIL', 'SMS', 'WHATSAPP'));

-- Opt-in consent tracking
ALTER TABLE students ADD COLUMN notification_opt_in BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE students ADD COLUMN notification_opt_in_at TIMESTAMP WITH TIME ZONE;

-- Normalized contact points (E.164 format for phone numbers)
ALTER TABLE students ADD COLUMN phone_e164 VARCHAR(20);
ALTER TABLE students ADD COLUMN whatsapp_number_e164 VARCHAR(20);

-- User preferences
ALTER TABLE students ADD COLUMN timezone VARCHAR(50) DEFAULT 'UTC';
ALTER TABLE students ADD COLUMN locale VARCHAR(10) DEFAULT 'en';
ALTER TABLE students ADD COLUMN quiet_hours_start TIME;
ALTER TABLE students ADD COLUMN quiet_hours_end TIME;

-- Index for notification queries
CREATE INDEX idx_students_notification_opt_in ON students(notification_opt_in) WHERE notification_opt_in = true;
CREATE INDEX idx_students_notification_channel ON students(preferred_notification_channel) WHERE preferred_notification_channel != 'NONE';

-- ============================================================================
-- NOTIFICATIONS OUTBOX TABLE
-- ============================================================================

CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Target
    student_id UUID NOT NULL REFERENCES students(id),

    -- Channel and type
    channel VARCHAR(20) NOT NULL,
    type VARCHAR(50) NOT NULL,

    -- Content (template-based)
    template_key VARCHAR(100),
    variables JSONB,

    -- Rendered content (optional, for providers that need pre-rendered content)
    rendered_subject VARCHAR(500),
    rendered_body TEXT,

    -- Delivery status
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    -- Retry tracking
    attempts INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    last_attempt_at TIMESTAMP WITH TIME ZONE,
    next_attempt_at TIMESTAMP WITH TIME ZONE,

    -- Provider response
    external_message_id VARCHAR(200),
    error_message TEXT,
    error_code VARCHAR(50),

    -- Metadata
    priority INTEGER NOT NULL DEFAULT 0,
    scheduled_for TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,

    -- Audit
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    sent_at TIMESTAMP WITH TIME ZONE,

    -- Constraints
    CONSTRAINT notifications_channel_check CHECK (channel IN ('EMAIL', 'SMS', 'WHATSAPP')),
    CONSTRAINT notifications_status_check CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'DELIVERED', 'FAILED', 'SKIPPED', 'EXPIRED')),
    CONSTRAINT notifications_type_check CHECK (type IN (
        'CLASS_SCHEDULED', 'CLASS_CANCELLED', 'CLASS_RESCHEDULED',
        'CLASS_REMINDER', 'WAITLIST_AVAILABLE', 'CUSTOM'
    ))
);

-- Indexes for outbox processing
CREATE INDEX idx_notifications_status_next_attempt ON notifications(status, next_attempt_at)
    WHERE status IN ('PENDING', 'PROCESSING');
CREATE INDEX idx_notifications_student_id ON notifications(student_id);
CREATE INDEX idx_notifications_student_created ON notifications(student_id, created_at DESC);
CREATE INDEX idx_notifications_type ON notifications(type);
CREATE INDEX idx_notifications_created_at ON notifications(created_at);
CREATE INDEX idx_notifications_scheduled ON notifications(scheduled_for) WHERE scheduled_for IS NOT NULL;

-- ============================================================================
-- NOTIFICATION TEMPLATES TABLE (for future use)
-- ============================================================================

CREATE TABLE notification_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    template_key VARCHAR(100) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    locale VARCHAR(10) NOT NULL DEFAULT 'en',

    subject_template VARCHAR(500),
    body_template TEXT NOT NULL,

    active BOOLEAN NOT NULL DEFAULT true,

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT notification_templates_channel_check CHECK (channel IN ('EMAIL', 'SMS', 'WHATSAPP')),
    -- Unique constraint on the combination of template_key, channel, and locale
    -- This allows the same template_key to have different content per channel and locale
    CONSTRAINT notification_templates_key_channel_locale_unique UNIQUE (template_key, channel, locale)
);

-- Insert default templates
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
    ('CLASS_REMINDER', 'WHATSAPP', 'en', NULL, 'Reminder: You have a class on {{date}} at {{time}}.');
