-- Students table
CREATE TABLE students (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name VARCHAR(200) NOT NULL,
    phone VARCHAR(50),
    email VARCHAR(200),
    notes VARCHAR(2000),
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_students_active ON students(active);
CREATE INDEX idx_students_full_name ON students(full_name);
CREATE INDEX idx_students_created_at ON students(created_at);

-- Slots table
CREATE TABLE slots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    start_at TIMESTAMP WITH TIME ZONE NOT NULL,
    end_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'FREE',
    student_id UUID REFERENCES students(id),
    notes VARCHAR(2000),
    version INTEGER NOT NULL DEFAULT 0,
    block_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT slots_start_at_key UNIQUE (start_at),
    CONSTRAINT slots_status_check CHECK (status IN ('FREE', 'BOOKED', 'CANCELLED', 'BLOCKED'))
);

CREATE INDEX idx_slots_start_at ON slots(start_at);
CREATE INDEX idx_slots_status ON slots(status);
CREATE INDEX idx_slots_student_id ON slots(student_id);
CREATE INDEX idx_slots_block_id ON slots(block_id);
CREATE INDEX idx_slots_start_at_status ON slots(start_at, status);

-- Blocks table
CREATE TABLE blocks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    from_at TIMESTAMP WITH TIME ZONE NOT NULL,
    to_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reason VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_blocks_from_at ON blocks(from_at);
CREATE INDEX idx_blocks_to_at ON blocks(to_at);
CREATE INDEX idx_blocks_range ON blocks(from_at, to_at);

-- Slot events table (audit log)
CREATE TABLE slot_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slot_id UUID NOT NULL,
    type VARCHAR(20) NOT NULL,
    at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    old_student_id UUID,
    new_student_id UUID,
    meta TEXT,
    CONSTRAINT slot_events_type_check CHECK (type IN ('CREATED', 'GENERATED', 'BOOKED', 'CANCELLED', 'FREED', 'REPLACED', 'RESCHEDULED', 'BLOCKED', 'UNBLOCKED', 'NOTES_UPDATED'))
);

CREATE INDEX idx_slot_events_slot_id ON slot_events(slot_id);
CREATE INDEX idx_slot_events_at ON slot_events(at);
CREATE INDEX idx_slot_events_type ON slot_events(type);
CREATE INDEX idx_slot_events_at_type ON slot_events(at, type);

-- Waitlist items table
CREATE TABLE waitlist_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id UUID NOT NULL REFERENCES students(id),
    preferred_days VARCHAR(200),
    preferred_time_ranges VARCHAR(500),
    notes VARCHAR(2000),
    priority INTEGER NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_waitlist_items_student_id ON waitlist_items(student_id);
CREATE INDEX idx_waitlist_items_active ON waitlist_items(active);
CREATE INDEX idx_waitlist_items_priority ON waitlist_items(priority);
CREATE INDEX idx_waitlist_items_created_at ON waitlist_items(created_at);
