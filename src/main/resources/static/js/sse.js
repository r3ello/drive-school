// Server-Sent Events client — real-time updates from the backend
const SSE = {
    eventSource: null,
    debounceTimers: {},

    // Events that require a calendar reload
    SLOT_EVENTS: [
        'SLOT_CREATED', 'SLOT_GENERATED', 'SLOT_BOOKED',
        'SLOT_CANCELLED', 'SLOT_FREED', 'SLOT_REPLACED',
        'SLOT_RESCHEDULED', 'SLOT_BLOCKED', 'SLOT_UNBLOCKED'
    ],

    // Events that require a student/waitlist reload
    STUDENT_EVENTS: [
        'STUDENT_CREATED', 'STUDENT_UPDATED', 'STUDENT_DEACTIVATED'
    ],

    connect() {
        if (this.eventSource) {
            this.eventSource.close();
        }

        const token = (typeof Auth !== 'undefined') ? Auth.getAccessToken() : null;
        const url = '/api/v1/stream' + (token ? '?token=' + encodeURIComponent(token) : '');
        console.log('[SSE] Connecting to /api/v1/stream…');
        this.eventSource = new EventSource(url);

        this.eventSource.onopen = () => {
            console.log('[SSE] Connected');
            this.setStatus('connected');
        };

        this.eventSource.onerror = (e) => {
            // EventSource reconnects automatically; just reflect the state
            console.warn('[SSE] Connection error / reconnecting', e);
            this.setStatus('disconnected');
        };

        this.SLOT_EVENTS.forEach(type => {
            this.eventSource.addEventListener(type, (e) => {
                this.onSlotEvent(type, e);
            });
        });

        this.STUDENT_EVENTS.forEach(type => {
            this.eventSource.addEventListener(type, (e) => {
                this.onStudentEvent(type, e);
            });
        });
    },

    onSlotEvent(type, e) {
        console.log('[SSE] Slot event received:', type, e.data);
        // Reload calendar — debounced so bulk generate (N slots) causes one reload
        this.debounce('calendar', () => {
            if (App.currentView === 'calendar') {
                Calendar.load();
            }
            // If user is on another view, Calendar.load() will be called when they switch back
            // because App.loadViewData() always calls Calendar.load()
        }, 500);

        // SLOT_BLOCKED / SLOT_UNBLOCKED may reflect a Block being created or deleted
        if (type === 'SLOT_BLOCKED' || type === 'SLOT_UNBLOCKED') {
            this.debounce('blocks', () => {
                if (App.currentView === 'blocks') {
                    Blocks.load();
                }
            }, 500);
        }
    },

    onStudentEvent(type, e) {
        console.log('[SSE] Student event received:', type, e.data);
        // Keep the calendar's student dropdown cache fresh
        Calendar.loadStudentsCache();

        // Reload the students table if it is visible
        this.debounce('students', () => {
            if (App.currentView === 'students') {
                Students.load();
            }
        }, 300);

        // Student names appear in the waitlist table too
        this.debounce('waitlist', () => {
            if (App.currentView === 'waitlist') {
                Waitlist.load();
            }
        }, 300);
    },

    // Collapses rapid-fire events into a single UI refresh
    debounce(key, fn, delay) {
        clearTimeout(this.debounceTimers[key]);
        this.debounceTimers[key] = setTimeout(fn, delay);
    },

    reconnect() {
        this.connect();
    },

    setStatus(status) {
        const dot = document.getElementById('sseStatusDot');
        if (!dot) return;
        dot.className = 'sse-dot sse-dot--' + status;
        dot.title = status === 'connected'
            ? 'Live updates: connected'
            : 'Live updates: reconnecting…';
    }
};
