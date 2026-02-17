// Calendar View Management
const Calendar = {
    currentWeekStart: null,
    currentDate: null,       // used for day/month views
    currentMonth: null,      // { year, month } for month view
    viewMode: 'week',        // 'day' | 'week' | 'month'
    slots: [],
    students: [],
    startHour: 7,
    endHour: 20,

    init() {
        const today = new Date();
        this.currentWeekStart = Utils.getWeekStart(today);
        this.currentDate = new Date(today);
        this.currentMonth = { year: today.getFullYear(), month: today.getMonth() };
        this.setupEventListeners();
        this.loadStudentsCache();
    },

    setupEventListeners() {
        document.getElementById('prevPeriod').addEventListener('click', () => this.navigatePeriod(-1));
        document.getElementById('nextPeriod').addEventListener('click', () => this.navigatePeriod(1));
        document.getElementById('todayBtn').addEventListener('click', () => this.goToToday());
        document.getElementById('generateSlotsBtn').addEventListener('click', () => this.showGenerateSlotsModal());
        document.getElementById('quickAddSlot').addEventListener('click', () => this.showQuickAddSlotModal());

        // View mode switcher
        document.querySelectorAll('.view-mode-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                const mode = btn.dataset.mode;
                this.switchViewMode(mode);
            });
        });
    },

    switchViewMode(mode) {
        this.viewMode = mode;

        // Update button states
        document.querySelectorAll('.view-mode-btn').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.mode === mode);
        });

        // Toggle grid visibility
        const calendarGrid = document.getElementById('calendarGrid');
        const monthGrid = document.getElementById('monthGrid');

        if (mode === 'month') {
            calendarGrid.style.display = 'none';
            monthGrid.style.display = 'grid';
        } else {
            calendarGrid.style.display = 'grid';
            monthGrid.style.display = 'none';
            // Toggle day-mode class for single column
            calendarGrid.classList.toggle('day-mode', mode === 'day');
        }

        this.load();
    },

    goToToday() {
        const today = new Date();
        this.currentWeekStart = Utils.getWeekStart(today);
        this.currentDate = new Date(today);
        this.currentMonth = { year: today.getFullYear(), month: today.getMonth() };
        this.load();
    },

    navigatePeriod(direction) {
        switch (this.viewMode) {
            case 'day':
                this.currentDate = Utils.addDays(this.currentDate, direction);
                break;
            case 'week':
                this.currentWeekStart = Utils.addDays(this.currentWeekStart, direction * 7);
                break;
            case 'month':
                this.currentMonth.month += direction;
                if (this.currentMonth.month > 11) {
                    this.currentMonth.month = 0;
                    this.currentMonth.year++;
                } else if (this.currentMonth.month < 0) {
                    this.currentMonth.month = 11;
                    this.currentMonth.year--;
                }
                break;
        }
        this.load();
    },

    updatePeriodLabel() {
        const label = document.getElementById('currentPeriodLabel');
        switch (this.viewMode) {
            case 'day':
                label.textContent = this.currentDate.toLocaleDateString('en-US', {
                    weekday: 'long', month: 'long', day: 'numeric', year: 'numeric'
                });
                break;
            case 'week': {
                const weekEnd = Utils.getWeekEnd(this.currentWeekStart);
                label.textContent = `${Utils.formatDate(this.currentWeekStart)} - ${Utils.formatDate(weekEnd)}`;
                break;
            }
            case 'month': {
                const date = new Date(this.currentMonth.year, this.currentMonth.month, 1);
                label.textContent = date.toLocaleDateString('en-US', { month: 'long', year: 'numeric' });
                break;
            }
        }
    },

    async loadStudentsCache() {
        try {
            const response = await API.getStudents({ active: true, size: 200 });
            this.students = response.content || [];
        } catch (error) {
            console.error('Failed to load students cache:', error);
            this.students = [];
        }
    },

    async load() {
        this.updatePeriodLabel();

        let from, to;
        switch (this.viewMode) {
            case 'day':
                from = Utils.toISOString(Utils.startOfDay(this.currentDate));
                to = Utils.toISOString(Utils.addDays(Utils.startOfDay(this.currentDate), 1));
                break;
            case 'week': {
                const weekEnd = Utils.getWeekEnd(this.currentWeekStart);
                from = Utils.toISOString(this.currentWeekStart);
                to = Utils.toISOString(Utils.addDays(weekEnd, 1));
                break;
            }
            case 'month': {
                const monthStart = new Date(this.currentMonth.year, this.currentMonth.month, 1);
                const monthEnd = new Date(this.currentMonth.year, this.currentMonth.month + 1, 0);
                // Extend to cover the full weeks shown in month view
                const gridStart = Utils.getWeekStart(monthStart);
                const gridEnd = Utils.getWeekEnd(monthEnd);
                from = Utils.toISOString(gridStart);
                to = Utils.toISOString(Utils.addDays(gridEnd, 1));
                break;
            }
        }

        try {
            const response = await API.getSlots(from, to);
            this.slots = response.content || [];
            this.render();
        } catch (error) {
            console.error('Failed to load calendar:', error);
            Toast.error('Failed to load calendar: ' + error.message);
        }
    },

    render() {
        switch (this.viewMode) {
            case 'day':
                this.renderDayView();
                break;
            case 'week':
                this.renderWeekView();
                break;
            case 'month':
                this.renderMonthView();
                break;
        }
    },

    // =========================================
    // WEEK VIEW
    // =========================================
    renderWeekView() {
        const grid = document.getElementById('calendarGrid');
        grid.innerHTML = '';
        grid.classList.remove('day-mode');

        // Header row
        grid.appendChild(this.createCornerCell());
        for (let i = 0; i < 7; i++) {
            const day = Utils.addDays(this.currentWeekStart, i);
            grid.appendChild(this.createDayHeader(day));
        }

        // Time column
        const timeColumn = document.createElement('div');
        timeColumn.className = 'time-column';
        for (let hour = this.startHour; hour < this.endHour; hour++) {
            const timeLabel = document.createElement('div');
            timeLabel.className = 'time-slot-label';
            timeLabel.textContent = `${hour.toString().padStart(2, '0')}:00`;
            timeColumn.appendChild(timeLabel);
        }
        grid.appendChild(timeColumn);

        // Day columns
        for (let i = 0; i < 7; i++) {
            const day = Utils.addDays(this.currentWeekStart, i);
            grid.appendChild(this.createDayColumn(day));
        }
    },

    // =========================================
    // DAY VIEW
    // =========================================
    renderDayView() {
        const grid = document.getElementById('calendarGrid');
        grid.innerHTML = '';
        grid.classList.add('day-mode');

        // Header row
        grid.appendChild(this.createCornerCell());
        grid.appendChild(this.createDayHeader(this.currentDate));

        // Time column
        const timeColumn = document.createElement('div');
        timeColumn.className = 'time-column';
        for (let hour = this.startHour; hour < this.endHour; hour++) {
            const timeLabel = document.createElement('div');
            timeLabel.className = 'time-slot-label';
            timeLabel.textContent = `${hour.toString().padStart(2, '0')}:00`;
            timeColumn.appendChild(timeLabel);
        }
        grid.appendChild(timeColumn);

        // Single day column
        grid.appendChild(this.createDayColumn(this.currentDate));
    },

    // =========================================
    // MONTH VIEW
    // =========================================
    renderMonthView() {
        const grid = document.getElementById('monthGrid');
        grid.innerHTML = '';

        const today = new Date();
        const monthStart = new Date(this.currentMonth.year, this.currentMonth.month, 1);
        const monthEnd = new Date(this.currentMonth.year, this.currentMonth.month + 1, 0);
        const gridStart = Utils.getWeekStart(monthStart);

        // Day-of-week headers
        const dayNames = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
        dayNames.forEach(name => {
            const header = document.createElement('div');
            header.className = 'month-day-header';
            header.textContent = name;
            grid.appendChild(header);
        });

        // Calendar cells (6 weeks max)
        let current = new Date(gridStart);
        for (let i = 0; i < 42; i++) {
            const cellDate = new Date(current);
            const isOtherMonth = cellDate.getMonth() !== this.currentMonth.month;
            const isToday = cellDate.toDateString() === today.toDateString();

            const cell = document.createElement('div');
            cell.className = 'month-cell';
            if (isOtherMonth) cell.classList.add('other-month');
            if (isToday) cell.classList.add('is-today');

            // Date number
            const dateDiv = document.createElement('div');
            dateDiv.className = 'month-cell-date';
            dateDiv.textContent = cellDate.getDate();
            cell.appendChild(dateDiv);

            // Slots for this day
            const daySlots = this.slots.filter(slot => {
                const slotDate = new Date(slot.startAt);
                return slotDate.toDateString() === cellDate.toDateString();
            });

            if (daySlots.length > 0) {
                // Show colored dots (max 8, then "+N")
                const dotsDiv = document.createElement('div');
                dotsDiv.style.lineHeight = '1';
                const maxDots = 8;
                daySlots.slice(0, maxDots).forEach(slot => {
                    const dot = document.createElement('span');
                    dot.className = `month-slot-dot dot-${slot.status.toLowerCase()}`;
                    dotsDiv.appendChild(dot);
                });
                if (daySlots.length > maxDots) {
                    const more = document.createElement('span');
                    more.className = 'month-slot-summary';
                    more.textContent = ` +${daySlots.length - maxDots}`;
                    dotsDiv.appendChild(more);
                }
                cell.appendChild(dotsDiv);

                // Count by status
                const counts = {};
                daySlots.forEach(s => {
                    counts[s.status] = (counts[s.status] || 0) + 1;
                });
                const countDiv = document.createElement('div');
                countDiv.className = 'month-slot-count';
                for (const [status, count] of Object.entries(counts)) {
                    const span = document.createElement('span');
                    const dot = document.createElement('span');
                    dot.className = `month-slot-dot dot-${status.toLowerCase()}`;
                    span.appendChild(dot);
                    span.appendChild(document.createTextNode(count));
                    countDiv.appendChild(span);
                }
                cell.appendChild(countDiv);
            }

            // Click to switch to day view
            cell.addEventListener('click', () => {
                this.currentDate = new Date(cellDate);
                this.currentWeekStart = Utils.getWeekStart(cellDate);
                this.switchViewMode('day');
            });

            grid.appendChild(cell);
            current = Utils.addDays(current, 1);
        }
    },

    // =========================================
    // SHARED HELPERS
    // =========================================
    createCornerCell() {
        const cell = document.createElement('div');
        cell.className = 'calendar-header';
        cell.textContent = '';
        return cell;
    },

    createDayHeader(date) {
        const header = document.createElement('div');
        header.className = 'calendar-header';
        const today = new Date();
        if (date.toDateString() === today.toDateString()) {
            header.classList.add('is-today');
        }
        header.innerHTML = `
            <small>${Utils.getShortDayName(date.getDay())}</small>
            <span class="day-number">${date.getDate()}</span>
        `;
        return header;
    },

    createDayColumn(date) {
        const column = document.createElement('div');
        column.className = 'day-column';

        // Create hour rows
        for (let hour = this.startHour; hour < this.endHour; hour++) {
            const row = document.createElement('div');
            row.className = 'calendar-hour-row';
            row.dataset.hour = hour;
            row.dataset.date = Utils.formatDateInput(date);

            // Allow clicking empty space to create slot
            row.addEventListener('click', (e) => {
                if (e.target === row || e.target.classList.contains('calendar-hour-row')) {
                    this.showQuickAddSlotModal(date, hour);
                }
            });

            column.appendChild(row);
        }

        // Add slots for this day
        const daySlots = this.slots.filter(slot => {
            const slotDate = new Date(slot.startAt);
            return slotDate.toDateString() === date.toDateString();
        });

        daySlots.forEach(slot => {
            const slotElement = this.createSlotElement(slot);
            const slotDate = new Date(slot.startAt);
            const hour = slotDate.getHours();
            const hourRow = column.querySelector(`[data-hour="${hour}"]`);
            if (hourRow) {
                hourRow.appendChild(slotElement);
            }
        });

        return column;
    },

    createSlotElement(slot) {
        const element = document.createElement('div');
        element.className = `calendar-slot slot-${slot.status.toLowerCase()}`;

        const startTime = Utils.formatTime(slot.startAt);
        const endTime = Utils.formatTime(slot.endAt);

        element.innerHTML = `
            <div class="slot-time">${startTime} - ${endTime}</div>
            ${slot.student ? `<div class="slot-student">${slot.student.fullName}</div>` : ''}
            <div class="slot-status">${slot.status}</div>
        `;

        element.addEventListener('click', (e) => {
            e.stopPropagation();
            this.showSlotDetails(slot);
        });

        return element;
    },

    async showSlotDetails(slot) {
        try {
            // Fetch fresh slot data and events
            const [freshSlot, eventsResponse] = await Promise.all([
                API.getSlot(slot.id),
                API.getSlotEvents(slot.id)
            ]);

            const events = eventsResponse.content;

            let actionsHtml = '';
            switch (freshSlot.status) {
                case 'FREE':
                    actionsHtml = `
                        <button class="btn btn-success" id="bookSlotBtn">Book</button>
                        <button class="btn btn-danger" id="deleteSlotBtn">Delete</button>
                    `;
                    break;
                case 'BOOKED':
                    actionsHtml = `
                        <button class="btn btn-warning" id="cancelSlotBtn">Cancel</button>
                        <button class="btn btn-secondary" id="replaceSlotBtn">Replace Student</button>
                        <button class="btn btn-primary" id="rescheduleSlotBtn">Reschedule</button>
                        <button class="btn btn-secondary" id="freeSlotBtn">Free</button>
                    `;
                    break;
                case 'CANCELLED':
                    actionsHtml = `
                        <button class="btn btn-success" id="freeSlotBtn">Free</button>
                        <button class="btn btn-secondary" id="replaceSlotBtn">Replace & Rebook</button>
                    `;
                    break;
                case 'BLOCKED':
                    actionsHtml = `<p style="color: var(--text-tertiary); font-size: 0.875rem;">This slot is blocked.</p>`;
                    break;
            }

            const eventsHtml = events.length > 0 ? `
                <div class="event-history">
                    <h4>History</h4>
                    ${events.map(e => `
                        <div class="event-item">
                            <span class="event-type">${e.type}</span>
                            <span class="event-time">${Utils.formatDateTime(e.at)}</span>
                        </div>
                    `).join('')}
                </div>
            ` : '';

            const content = `
                <div class="slot-detail-row">
                    <span class="slot-detail-label">Time</span>
                    <span class="slot-detail-value">${Utils.formatDateTime(freshSlot.startAt)}</span>
                </div>
                <div class="slot-detail-row">
                    <span class="slot-detail-label">Status</span>
                    <span class="slot-detail-value">
                        <span class="badge badge-${this.getStatusBadge(freshSlot.status)}">${freshSlot.status}</span>
                    </span>
                </div>
                ${freshSlot.student ? `
                    <div class="slot-detail-row">
                        <span class="slot-detail-label">Student</span>
                        <span class="slot-detail-value">${freshSlot.student.fullName}</span>
                    </div>
                ` : ''}
                ${freshSlot.notes ? `
                    <div class="slot-detail-row">
                        <span class="slot-detail-label">Notes</span>
                        <span class="slot-detail-value">${freshSlot.notes}</span>
                    </div>
                ` : ''}
                <div class="slot-actions-grid">
                    ${actionsHtml}
                </div>
                ${eventsHtml}
            `;

            Modal.open('Slot Details', content);
            this.setupSlotActionListeners(freshSlot);
        } catch (error) {
            Toast.error('Failed to load slot details: ' + error.message);
        }
    },

    setupSlotActionListeners(slot) {
        const bookBtn = document.getElementById('bookSlotBtn');
        const cancelBtn = document.getElementById('cancelSlotBtn');
        const freeBtn = document.getElementById('freeSlotBtn');
        const replaceBtn = document.getElementById('replaceSlotBtn');
        const rescheduleBtn = document.getElementById('rescheduleSlotBtn');
        const deleteBtn = document.getElementById('deleteSlotBtn');

        if (bookBtn) bookBtn.addEventListener('click', async () => await this.showBookSlotForm(slot));
        if (cancelBtn) cancelBtn.addEventListener('click', () => this.showCancelSlotForm(slot));
        if (freeBtn) freeBtn.addEventListener('click', async () => await this.freeSlot(slot));
        if (replaceBtn) replaceBtn.addEventListener('click', async () => await this.showReplaceSlotForm(slot));
        if (rescheduleBtn) rescheduleBtn.addEventListener('click', async () => await this.showRescheduleSlotForm(slot));
        if (deleteBtn) deleteBtn.addEventListener('click', () => this.deleteSlot(slot));
    },

    getStatusBadge(status) {
        const badges = {
            'FREE': 'success',
            'BOOKED': 'primary',
            'CANCELLED': 'danger',
            'BLOCKED': 'secondary'
        };
        return badges[status] || 'secondary';
    },

    async showBookSlotForm(slot) {
        // Refresh students cache if empty
        if (this.students.length === 0) {
            await this.loadStudentsCache();
        }

        if (this.students.length === 0) {
            Toast.warning('No active students available. Please add students first.');
            return;
        }

        const studentsOptions = this.students.map(s =>
            `<option value="${s.id}">${s.fullName}</option>`
        ).join('');

        const content = `
            <form id="bookSlotForm">
                <div class="form-group">
                    <label>Student</label>
                    <select id="bookStudentId" required>
                        <option value="">Select a student...</option>
                        ${studentsOptions}
                    </select>
                </div>
                <div class="form-group">
                    <label>Notes</label>
                    <textarea id="bookNotes" placeholder="Optional notes..."></textarea>
                </div>
                <div class="modal-actions">
                    <button type="button" class="btn btn-secondary" id="cancelBookBtn">Cancel</button>
                    <button type="submit" class="btn btn-success">Book Slot</button>
                </div>
            </form>
        `;

        Modal.open('Book Slot', content);

        document.getElementById('cancelBookBtn').addEventListener('click', () => Modal.close());
        document.getElementById('bookSlotForm').addEventListener('submit', async (e) => {
            e.preventDefault();
            const studentId = document.getElementById('bookStudentId').value;
            const notes = document.getElementById('bookNotes').value;

            try {
                await API.bookSlot(slot.id, { studentId, notes: notes || undefined });
                Toast.success('Slot booked successfully');
                Modal.close();
                this.load();
            } catch (error) {
                Toast.error('Failed to book slot: ' + error.message);
            }
        });
    },

    showCancelSlotForm(slot) {
        const content = `
            <form id="cancelSlotForm">
                <div class="form-group">
                    <label>Cancelled By</label>
                    <select id="cancelledBy" required>
                        <option value="STUDENT">Student</option>
                        <option value="TEACHER">Teacher</option>
                    </select>
                </div>
                <div class="form-group">
                    <label>Reason</label>
                    <textarea id="cancelReason" placeholder="Optional reason..."></textarea>
                </div>
                <div class="modal-actions">
                    <button type="button" class="btn btn-secondary" id="cancelCancelBtn">Back</button>
                    <button type="submit" class="btn btn-warning">Cancel Slot</button>
                </div>
            </form>
        `;

        Modal.open('Cancel Slot', content);

        document.getElementById('cancelCancelBtn').addEventListener('click', () => Modal.close());
        document.getElementById('cancelSlotForm').addEventListener('submit', async (e) => {
            e.preventDefault();
            const cancelledBy = document.getElementById('cancelledBy').value;
            const reason = document.getElementById('cancelReason').value;

            try {
                await API.cancelSlot(slot.id, { cancelledBy, reason: reason || undefined });
                Toast.success('Slot cancelled successfully');
                Modal.close();
                this.load();
            } catch (error) {
                Toast.error('Failed to cancel slot: ' + error.message);
            }
        });
    },

    async freeSlot(slot) {
        try {
            await API.freeSlot(slot.id, {});
            Toast.success('Slot freed successfully');
            Modal.close();
            this.load();
        } catch (error) {
            Toast.error('Failed to free slot: ' + error.message);
        }
    },

    async showReplaceSlotForm(slot) {
        // Refresh students cache if empty
        if (this.students.length === 0) {
            await this.loadStudentsCache();
        }

        const availableStudents = this.students.filter(s => !slot.student || s.id !== slot.student.id);

        if (availableStudents.length === 0) {
            Toast.warning('No other students available for replacement.');
            return;
        }

        const studentsOptions = availableStudents
            .map(s => `<option value="${s.id}">${s.fullName}</option>`)
            .join('');

        const content = `
            <form id="replaceSlotForm">
                <div class="form-group">
                    <label>New Student</label>
                    <select id="newStudentId" required>
                        <option value="">Select a student...</option>
                        ${studentsOptions}
                    </select>
                </div>
                <div class="form-group">
                    <label>Reason</label>
                    <textarea id="replaceReason" placeholder="Optional reason..."></textarea>
                </div>
                <div class="modal-actions">
                    <button type="button" class="btn btn-secondary" id="cancelReplaceBtn">Cancel</button>
                    <button type="submit" class="btn btn-primary">Replace Student</button>
                </div>
            </form>
        `;

        Modal.open('Replace Student', content);

        document.getElementById('cancelReplaceBtn').addEventListener('click', () => Modal.close());
        document.getElementById('replaceSlotForm').addEventListener('submit', async (e) => {
            e.preventDefault();
            const newStudentId = document.getElementById('newStudentId').value;
            const reason = document.getElementById('replaceReason').value;

            try {
                await API.replaceSlot(slot.id, { newStudentId, reason: reason || undefined });
                Toast.success('Student replaced successfully');
                Modal.close();
                this.load();
            } catch (error) {
                Toast.error('Failed to replace student: ' + error.message);
            }
        });
    },

    async showRescheduleSlotForm(slot) {
        // Get free slots for the current week
        const weekEnd = Utils.getWeekEnd(this.currentWeekStart);
        const from = Utils.toISOString(this.currentWeekStart);
        const to = Utils.toISOString(Utils.addDays(weekEnd, 1));

        try {
            const response = await API.getSlots(from, to, 'FREE');
            const freeSlots = response.content.filter(s => s.id !== slot.id);

            if (freeSlots.length === 0) {
                Toast.warning('No free slots available for rescheduling this week');
                return;
            }

            const slotsOptions = freeSlots.map(s =>
                `<option value="${s.id}">${Utils.formatDateTime(s.startAt)}</option>`
            ).join('');

            const content = `
                <form id="rescheduleSlotForm">
                    <div class="form-group">
                        <label>Target Slot</label>
                        <select id="targetSlotId" required>
                            <option value="">Select a free slot...</option>
                            ${slotsOptions}
                        </select>
                    </div>
                    <div class="form-group">
                        <label>Reason</label>
                        <textarea id="rescheduleReason" placeholder="Optional reason..."></textarea>
                    </div>
                    <div class="modal-actions">
                        <button type="button" class="btn btn-secondary" id="cancelRescheduleBtn">Cancel</button>
                        <button type="submit" class="btn btn-primary">Reschedule</button>
                    </div>
                </form>
            `;

            Modal.open('Reschedule Slot', content);

            document.getElementById('cancelRescheduleBtn').addEventListener('click', () => Modal.close());
            document.getElementById('rescheduleSlotForm').addEventListener('submit', async (e) => {
                e.preventDefault();
                const targetSlotId = document.getElementById('targetSlotId').value;
                const reason = document.getElementById('rescheduleReason').value;

                try {
                    await API.rescheduleSlot(slot.id, { targetSlotId, reason: reason || undefined });
                    Toast.success('Slot rescheduled successfully');
                    Modal.close();
                    this.load();
                } catch (error) {
                    Toast.error('Failed to reschedule slot: ' + error.message);
                }
            });
        } catch (error) {
            Toast.error('Failed to load free slots: ' + error.message);
        }
    },

    async deleteSlot(slot) {
        Modal.confirm(
            'Delete Slot',
            'Are you sure you want to delete this slot?',
            async () => {
                try {
                    await API.deleteSlot(slot.id);
                    Toast.success('Slot deleted successfully');
                    this.load();
                } catch (error) {
                    Toast.error('Failed to delete slot: ' + error.message);
                }
            }
        );
    },

    showQuickAddSlotModal(date = null, hour = null) {
        const now = new Date();
        let defaultDateTime = now;
        let modalTitle = 'Add Slot';

        if (date && hour !== null) {
            defaultDateTime = new Date(date);
            defaultDateTime.setHours(hour, 0, 0, 0);
            modalTitle = `Add Slot - ${Utils.formatDate(date)} at ${hour.toString().padStart(2, '0')}:00`;
        }

        const content = `
            <form id="quickAddSlotForm">
                <div class="form-group">
                    <label>Date & Time</label>
                    <input type="datetime-local" id="slotStartAt" required
                           value="${Utils.formatDateTimeInput(defaultDateTime)}">
                </div>
                <div class="modal-actions">
                    <button type="button" class="btn btn-secondary" id="cancelQuickAddBtn">Cancel</button>
                    <button type="submit" class="btn btn-primary">Create Slot</button>
                </div>
            </form>
        `;

        Modal.open(modalTitle, content);

        document.getElementById('cancelQuickAddBtn').addEventListener('click', () => Modal.close());
        document.getElementById('quickAddSlotForm').addEventListener('submit', async (e) => {
            e.preventDefault();
            const startAt = new Date(document.getElementById('slotStartAt').value).toISOString();

            try {
                await API.createSlot({ startAt, durationMinutes: 60 });
                Toast.success('Slot created successfully');
                Modal.close();
                this.load();
            } catch (error) {
                Toast.error('Failed to create slot: ' + error.message);
            }
        });
    },

    showGenerateSlotsModal() {
        const today = new Date();
        const nextWeek = Utils.addDays(today, 7);

        const content = `
            <form id="generateSlotsForm">
                <div class="form-row">
                    <div class="form-group">
                        <label>From Date</label>
                        <input type="date" id="generateFrom" required value="${Utils.formatDateInput(today)}">
                    </div>
                    <div class="form-group">
                        <label>To Date</label>
                        <input type="date" id="generateTo" required value="${Utils.formatDateInput(nextWeek)}">
                    </div>
                </div>
                <div class="form-group">
                    <label>Weekly Schedule</label>
                    <div id="weeklyRulesContainer">
                        <div class="weekly-rule" data-index="0">
                            <div class="form-row">
                                <select class="rule-day" required>
                                    <option value="MONDAY">Monday</option>
                                    <option value="TUESDAY">Tuesday</option>
                                    <option value="WEDNESDAY">Wednesday</option>
                                    <option value="THURSDAY">Thursday</option>
                                    <option value="FRIDAY">Friday</option>
                                    <option value="SATURDAY">Saturday</option>
                                    <option value="SUNDAY">Sunday</option>
                                </select>
                                <input type="time" class="rule-start" value="09:00" required>
                                <input type="time" class="rule-end" value="17:00" required>
                                <button type="button" class="btn btn-sm btn-danger remove-rule">&times;</button>
                            </div>
                        </div>
                    </div>
                    <button type="button" class="btn btn-sm btn-secondary" id="addRuleBtn" style="margin-top: 8px;">
                        + Add Day
                    </button>
                </div>
                <div class="modal-actions">
                    <button type="button" class="btn btn-secondary" id="cancelGenerateBtn">Cancel</button>
                    <button type="submit" class="btn btn-primary">Generate Slots</button>
                </div>
            </form>
        `;

        Modal.open('Generate Slots', content);

        document.getElementById('cancelGenerateBtn').addEventListener('click', () => Modal.close());
        document.getElementById('addRuleBtn').addEventListener('click', () => this.addWeeklyRule());

        document.querySelectorAll('.remove-rule').forEach(btn => {
            btn.addEventListener('click', (e) => this.removeWeeklyRule(e.target));
        });

        document.getElementById('generateSlotsForm').addEventListener('submit', async (e) => {
            e.preventDefault();
            await this.generateSlots();
        });
    },

    addWeeklyRule() {
        const container = document.getElementById('weeklyRulesContainer');
        const index = container.children.length;

        const ruleHtml = `
            <div class="weekly-rule" data-index="${index}" style="margin-top: 8px;">
                <div class="form-row">
                    <select class="rule-day" required>
                        <option value="MONDAY">Monday</option>
                        <option value="TUESDAY">Tuesday</option>
                        <option value="WEDNESDAY">Wednesday</option>
                        <option value="THURSDAY">Thursday</option>
                        <option value="FRIDAY">Friday</option>
                        <option value="SATURDAY">Saturday</option>
                        <option value="SUNDAY">Sunday</option>
                    </select>
                    <input type="time" class="rule-start" value="09:00" required>
                    <input type="time" class="rule-end" value="17:00" required>
                    <button type="button" class="btn btn-sm btn-danger remove-rule">&times;</button>
                </div>
            </div>
        `;

        container.insertAdjacentHTML('beforeend', ruleHtml);

        const newRemoveBtn = container.lastElementChild.querySelector('.remove-rule');
        newRemoveBtn.addEventListener('click', (e) => this.removeWeeklyRule(e.target));
    },

    removeWeeklyRule(button) {
        const container = document.getElementById('weeklyRulesContainer');
        if (container.children.length > 1) {
            button.closest('.weekly-rule').remove();
        }
    },

    async generateSlots() {
        const from = document.getElementById('generateFrom').value;
        const to = document.getElementById('generateTo').value;

        const rules = [];
        document.querySelectorAll('.weekly-rule').forEach(rule => {
            rules.push({
                dayOfWeek: rule.querySelector('.rule-day').value,
                startTime: rule.querySelector('.rule-start').value,
                endTime: rule.querySelector('.rule-end').value
            });
        });

        try {
            const result = await API.generateSlots({
                from,
                to,
                timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
                weeklyRules: rules,
                slotDurationMinutes: 60
            });

            Toast.success(`Generated ${result.createdCount} slots (${result.skippedCount} skipped)`);
            Modal.close();
            this.load();
        } catch (error) {
            Toast.error('Failed to generate slots: ' + error.message);
        }
    }
};
