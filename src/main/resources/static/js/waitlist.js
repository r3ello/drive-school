// Waitlist View Management
const Waitlist = {
    currentPage: 0,
    pageSize: 20,
    totalPages: 0,
    students: [],

    init() {
        this.setupEventListeners();
    },

    setupEventListeners() {
        document.getElementById('addWaitlistBtn').addEventListener('click', () => this.showAddWaitlistModal());

        document.getElementById('waitlistActiveFilter').addEventListener('change', () => {
            this.currentPage = 0;
            this.load();
        });
    },

    async loadStudents() {
        try {
            const response = await API.getStudents({ active: true, size: 200 });
            this.students = response.content;
        } catch (error) {
            console.error('Failed to load students:', error);
        }
    },

    async load() {
        const active = document.getElementById('waitlistActiveFilter').value;

        try {
            const response = await API.getWaitlist({
                active: active,
                page: this.currentPage,
                size: this.pageSize,
                sort: 'priority,desc'
            });

            this.totalPages = response.totalPages;
            this.renderTable(response.content);
            this.renderPagination(response);
        } catch (error) {
            Toast.error('Failed to load waitlist: ' + error.message);
        }
    },

    renderTable(items) {
        const tbody = document.getElementById('waitlistTableBody');

        if (items.length === 0) {
            tbody.innerHTML = `
                <tr>
                    <td colspan="6" class="empty-state">
                        <div class="empty-state-icon">📋</div>
                        <p>Waitlist is empty</p>
                    </td>
                </tr>
            `;
            return;
        }

        tbody.innerHTML = items.map(item => `
            <tr>
                <td><strong>${this.escapeHtml(item.student.fullName)}</strong></td>
                <td>${item.preferredDays ? item.preferredDays.join(', ') : '-'}</td>
                <td>${this.formatTimeRanges(item.preferredTimeRanges)}</td>
                <td><span class="badge badge-primary">${item.priority}</span></td>
                <td>${item.notes ? this.escapeHtml(item.notes).substring(0, 50) + '...' : '-'}</td>
                <td>
                    ${item.active ? `
                        <button class="btn btn-sm btn-danger" onclick="Waitlist.removeFromWaitlist('${item.id}')">Remove</button>
                    ` : '<span class="badge badge-secondary">Inactive</span>'}
                </td>
            </tr>
        `).join('');
    },

    formatTimeRanges(ranges) {
        if (!ranges || ranges.length === 0) return '-';
        return ranges.map(r => `${r.from}-${r.to}`).join(', ');
    },

    renderPagination(response) {
        const pagination = document.getElementById('waitlistPagination');

        if (response.totalPages <= 1) {
            pagination.innerHTML = '';
            return;
        }

        let html = '';

        if (this.currentPage > 0) {
            html += `<button onclick="Waitlist.goToPage(${this.currentPage - 1})">←</button>`;
        }

        const startPage = Math.max(0, this.currentPage - 2);
        const endPage = Math.min(response.totalPages - 1, this.currentPage + 2);

        for (let i = startPage; i <= endPage; i++) {
            html += `
                <button class="${i === this.currentPage ? 'active' : ''}"
                        onclick="Waitlist.goToPage(${i})">${i + 1}</button>
            `;
        }

        if (this.currentPage < response.totalPages - 1) {
            html += `<button onclick="Waitlist.goToPage(${this.currentPage + 1})">→</button>`;
        }

        pagination.innerHTML = html;
    },

    goToPage(page) {
        this.currentPage = page;
        this.load();
    },

    async showAddWaitlistModal() {
        await this.loadStudents();

        const studentsOptions = this.students.map(s =>
            `<option value="${s.id}">${s.fullName}</option>`
        ).join('');

        const daysCheckboxes = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']
            .map(day => `
                <label class="day-checkbox">
                    <input type="checkbox" name="preferredDays" value="${day}">
                    ${day.charAt(0) + day.slice(1).toLowerCase()}
                </label>
            `).join('');

        const content = `
            <form id="waitlistForm">
                <div class="form-group">
                    <label>Student *</label>
                    <select id="waitlistStudentId" required>
                        <option value="">Select a student...</option>
                        ${studentsOptions}
                    </select>
                </div>
                <div class="form-group">
                    <label>Preferred Days</label>
                    <div class="days-checkbox-group">
                        ${daysCheckboxes}
                    </div>
                </div>
                <div class="form-group">
                    <label>Preferred Time Range</label>
                    <div class="form-row">
                        <input type="time" id="waitlistTimeFrom" placeholder="From">
                        <input type="time" id="waitlistTimeTo" placeholder="To">
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label>Priority</label>
                        <input type="number" id="waitlistPriority" value="0" min="0">
                    </div>
                </div>
                <div class="form-group">
                    <label>Notes</label>
                    <textarea id="waitlistNotes" maxlength="2000" placeholder="Optional notes..."></textarea>
                </div>
                <div class="modal-actions">
                    <button type="button" class="btn btn-secondary" id="cancelWaitlistBtn">Cancel</button>
                    <button type="submit" class="btn btn-primary">Add to Waitlist</button>
                </div>
            </form>
        `;

        Modal.open('Add to Waitlist', content);

        document.getElementById('cancelWaitlistBtn').addEventListener('click', () => Modal.close());
        document.getElementById('waitlistForm').addEventListener('submit', async (e) => {
            e.preventDefault();
            await this.addToWaitlist();
        });
    },

    async addToWaitlist() {
        const studentId = document.getElementById('waitlistStudentId').value;
        const priority = parseInt(document.getElementById('waitlistPriority').value) || 0;
        const notes = document.getElementById('waitlistNotes').value;
        const timeFrom = document.getElementById('waitlistTimeFrom').value;
        const timeTo = document.getElementById('waitlistTimeTo').value;

        const preferredDays = Array.from(document.querySelectorAll('input[name="preferredDays"]:checked'))
            .map(cb => cb.value);

        const preferredTimeRanges = (timeFrom && timeTo) ? [{ from: timeFrom, to: timeTo }] : null;

        const data = {
            studentId,
            preferredDays: preferredDays.length > 0 ? preferredDays : null,
            preferredTimeRanges,
            notes: notes || null,
            priority
        };

        try {
            await API.addToWaitlist(data);
            Toast.success('Added to waitlist successfully');
            Modal.close();
            this.load();
        } catch (error) {
            Toast.error('Failed to add to waitlist: ' + error.message);
        }
    },

    removeFromWaitlist(id) {
        Modal.confirm(
            'Remove from Waitlist',
            'Are you sure you want to remove this entry from the waitlist?',
            async () => {
                try {
                    await API.removeFromWaitlist(id);
                    Toast.success('Removed from waitlist');
                    this.load();
                } catch (error) {
                    Toast.error('Failed to remove from waitlist: ' + error.message);
                }
            }
        );
    },

    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
};
