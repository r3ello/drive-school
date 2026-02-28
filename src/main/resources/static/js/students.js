// Students View Management
const Students = {
    currentPage: 0,
    pageSize: 20,
    totalPages: 0,
    searchTimeout: null,
    inviteInProgress: new Set(),

    init() {
        this.setupEventListeners();
    },

    setupEventListeners() {
        document.getElementById('addStudentBtn').addEventListener('click', () => this.showAddStudentModal());

        document.getElementById('studentSearch').addEventListener('input', (e) => {
            clearTimeout(this.searchTimeout);
            this.searchTimeout = setTimeout(() => {
                this.currentPage = 0;
                this.load();
            }, 300);
        });

        document.getElementById('studentActiveFilter').addEventListener('change', () => {
            this.currentPage = 0;
            this.load();
        });
    },

    async load() {
        const query = document.getElementById('studentSearch').value;
        const active = document.getElementById('studentActiveFilter').value;

        try {
            const response = await API.getStudents({
                query: query || undefined,
                active: active !== '' ? active : undefined,
                page: this.currentPage,
                size: this.pageSize,
                sort: 'fullName,asc'
            });

            this.totalPages = response.totalPages;
            this.renderTable(response.content);
            this.renderPagination(response);
        } catch (error) {
            Toast.error('Failed to load students: ' + error.message);
        }
    },

    renderTable(students) {
        const tbody = document.getElementById('studentsTableBody');

        if (students.length === 0) {
            tbody.innerHTML = `
                <tr>
                    <td colspan="6" class="empty-state">
                        <div class="empty-state-icon">Students</div>
                        <p>No students found</p>
                    </td>
                </tr>
            `;
            return;
        }

        tbody.innerHTML = students.map(student => `
            <tr>
                <td><strong>${this.escapeHtml(student.fullName)}</strong></td>
                <td>${student.phone || '-'}</td>
                <td>${student.email || '-'}</td>
                <td>
                    <span class="badge ${student.active ? 'badge-success' : 'badge-secondary'}">
                        ${student.active ? 'Active' : 'Inactive'}
                    </span>
                </td>
                <td>
                    ${this.getNotificationBadge(student)}
                </td>
                <td>
                    <button class="btn btn-sm btn-secondary" onclick="Students.showEditStudentModal('${student.id}')">Edit</button>
                    <button class="btn btn-sm btn-secondary" onclick="Students.showStudentHistory('${student.id}')">History</button>
                    ${student.canInvite ? `<button class="btn btn-sm btn-primary" onclick="Students.inviteStudent('${student.id}', this)">Invite</button>` : ''}
                    ${student.active ? `
                        <button class="btn btn-sm btn-danger" onclick="Students.deactivateStudent('${student.id}')">Deactivate</button>
                    ` : ''}
                </td>
            </tr>
        `).join('');
    },

    getNotificationBadge(student) {
        if (!student.notificationOptIn) {
            return '<span class="badge badge-secondary">No notifications</span>';
        }
        const channelBadges = {
            'EMAIL': 'badge-primary',
            'SMS': 'badge-success',
            'WHATSAPP': 'badge-success',
            'NONE': 'badge-secondary'
        };
        const badgeClass = channelBadges[student.preferredNotificationChannel] || 'badge-secondary';
        return `<span class="badge ${badgeClass}">${student.preferredNotificationChannel}</span>`;
    },

    renderPagination(response) {
        const pagination = document.getElementById('studentsPagination');

        if (response.totalPages <= 1) {
            pagination.innerHTML = '';
            return;
        }

        let html = '';

        if (this.currentPage > 0) {
            html += `<button onclick="Students.goToPage(${this.currentPage - 1})">Prev</button>`;
        }

        const startPage = Math.max(0, this.currentPage - 2);
        const endPage = Math.min(response.totalPages - 1, this.currentPage + 2);

        for (let i = startPage; i <= endPage; i++) {
            html += `
                <button class="${i === this.currentPage ? 'active' : ''}"
                        onclick="Students.goToPage(${i})">${i + 1}</button>
            `;
        }

        if (this.currentPage < response.totalPages - 1) {
            html += `<button onclick="Students.goToPage(${this.currentPage + 1})">Next</button>`;
        }

        pagination.innerHTML = html;
    },

    goToPage(page) {
        this.currentPage = page;
        this.load();
    },

    showAddStudentModal() {
        const content = `
            <form id="studentForm">
                <div class="form-group">
                    <label>Full Name *</label>
                    <input type="text" id="studentFullName" required minlength="1" maxlength="200">
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label>Phone</label>
                        <input type="tel" id="studentPhone" maxlength="50" placeholder="Display format">
                    </div>
                    <div class="form-group">
                        <label>Email</label>
                        <input type="email" id="studentEmail" maxlength="200">
                    </div>
                </div>
                <div class="form-group">
                    <label>Notes</label>
                    <textarea id="studentNotes" maxlength="2000" placeholder="Optional notes..."></textarea>
                </div>

                <h4 style="margin-top: 20px; margin-bottom: 12px; font-size: 0.9rem; color: var(--text-secondary);">
                    Notification Preferences
                </h4>

                <div class="form-group">
                    <label>
                        <input type="checkbox" id="studentNotificationOptIn">
                        Enable notifications
                    </label>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label>Preferred Channel</label>
                        <select id="studentNotificationChannel">
                            <option value="NONE">None</option>
                            <option value="EMAIL">Email</option>
                            <option value="SMS">SMS</option>
                            <option value="WHATSAPP">WhatsApp</option>
                        </select>
                    </div>
                    <div class="form-group">
                        <label>Phone (E.164)</label>
                        <input type="tel" id="studentPhoneE164" maxlength="20" placeholder="+12025551234">
                    </div>
                </div>

                <div class="modal-actions">
                    <button type="button" class="btn btn-secondary" id="cancelStudentBtn">Cancel</button>
                    <button type="submit" class="btn btn-primary">Create Student</button>
                </div>
            </form>
        `;

        Modal.open('Add Student', content);

        document.getElementById('cancelStudentBtn').addEventListener('click', () => Modal.close());
        document.getElementById('studentForm').addEventListener('submit', async (e) => {
            e.preventDefault();
            await this.createStudent();
        });
    },

    async createStudent() {
        const data = {
            fullName: document.getElementById('studentFullName').value,
            phone: document.getElementById('studentPhone').value || undefined,
            email: document.getElementById('studentEmail').value || undefined,
            notes: document.getElementById('studentNotes').value || undefined,
            notificationOptIn: document.getElementById('studentNotificationOptIn').checked,
            preferredNotificationChannel: document.getElementById('studentNotificationChannel').value,
            phoneE164: document.getElementById('studentPhoneE164').value || undefined
        };

        try {
            await API.createStudent(data);
            Toast.success('Student created successfully');
            Modal.close();
            this.load();
            Calendar.loadStudentsCache();
        } catch (error) {
            Toast.error('Failed to create student: ' + error.message);
        }
    },

    async showEditStudentModal(studentId) {
        try {
            const student = await API.getStudent(studentId);

            const content = `
                <form id="editStudentForm">
                    <div class="form-group">
                        <label>Full Name *</label>
                        <input type="text" id="editStudentFullName" required minlength="1" maxlength="200"
                               value="${this.escapeHtml(student.fullName)}">
                    </div>
                    <div class="form-row">
                        <div class="form-group">
                            <label>Phone</label>
                            <input type="tel" id="editStudentPhone" maxlength="50"
                                   value="${student.phone || ''}" placeholder="Display format">
                        </div>
                        <div class="form-group">
                            <label>Email</label>
                            <input type="email" id="editStudentEmail" maxlength="200"
                                   value="${student.email || ''}">
                        </div>
                    </div>
                    <div class="form-group">
                        <label>Notes</label>
                        <textarea id="editStudentNotes" maxlength="2000">${student.notes || ''}</textarea>
                    </div>
                    <div class="form-group">
                        <label>
                            <input type="checkbox" id="editStudentActive" ${student.active ? 'checked' : ''}>
                            Active
                        </label>
                    </div>

                    <h4 style="margin-top: 20px; margin-bottom: 12px; font-size: 0.9rem; color: var(--text-secondary);">
                        Notification Preferences
                    </h4>

                    <div class="form-group">
                        <label>
                            <input type="checkbox" id="editStudentNotificationOptIn" ${student.notificationOptIn ? 'checked' : ''}>
                            Enable notifications
                        </label>
                        ${student.notificationOptInAt ? `<small style="display: block; color: var(--text-secondary);">Opted in: ${Utils.formatDateTime(student.notificationOptInAt)}</small>` : ''}
                    </div>
                    <div class="form-row">
                        <div class="form-group">
                            <label>Preferred Channel</label>
                            <select id="editStudentNotificationChannel">
                                <option value="NONE" ${student.preferredNotificationChannel === 'NONE' ? 'selected' : ''}>None</option>
                                <option value="EMAIL" ${student.preferredNotificationChannel === 'EMAIL' ? 'selected' : ''}>Email</option>
                                <option value="SMS" ${student.preferredNotificationChannel === 'SMS' ? 'selected' : ''}>SMS</option>
                                <option value="WHATSAPP" ${student.preferredNotificationChannel === 'WHATSAPP' ? 'selected' : ''}>WhatsApp</option>
                            </select>
                        </div>
                        <div class="form-group">
                            <label>Timezone</label>
                            <input type="text" id="editStudentTimezone" maxlength="50"
                                   value="${student.timezone || 'UTC'}" placeholder="e.g., Europe/London">
                        </div>
                    </div>
                    <div class="form-row">
                        <div class="form-group">
                            <label>Phone (E.164 for SMS)</label>
                            <input type="tel" id="editStudentPhoneE164" maxlength="20"
                                   value="${student.phoneE164 || ''}" placeholder="+12025551234">
                        </div>
                        <div class="form-group">
                            <label>WhatsApp (E.164)</label>
                            <input type="tel" id="editStudentWhatsappE164" maxlength="20"
                                   value="${student.whatsappNumberE164 || ''}" placeholder="If different from phone">
                        </div>
                    </div>
                    <div class="form-row">
                        <div class="form-group">
                            <label>Quiet Hours Start</label>
                            <input type="time" id="editStudentQuietStart"
                                   value="${student.quietHoursStart || ''}">
                        </div>
                        <div class="form-group">
                            <label>Quiet Hours End</label>
                            <input type="time" id="editStudentQuietEnd"
                                   value="${student.quietHoursEnd || ''}">
                        </div>
                    </div>

                    <div class="modal-actions">
                        <button type="button" class="btn btn-secondary" id="cancelEditStudentBtn">Cancel</button>
                        <button type="submit" class="btn btn-primary">Save Changes</button>
                    </div>
                </form>
            `;

            Modal.open('Edit Student', content);

            document.getElementById('cancelEditStudentBtn').addEventListener('click', () => Modal.close());
            document.getElementById('editStudentForm').addEventListener('submit', async (e) => {
                e.preventDefault();
                await this.updateStudent(studentId);
            });
        } catch (error) {
            Toast.error('Failed to load student: ' + error.message);
        }
    },

    async updateStudent(studentId) {
        const data = {
            fullName: document.getElementById('editStudentFullName').value,
            phone: document.getElementById('editStudentPhone').value || null,
            email: document.getElementById('editStudentEmail').value || null,
            notes: document.getElementById('editStudentNotes').value || null,
            active: document.getElementById('editStudentActive').checked,
            notificationOptIn: document.getElementById('editStudentNotificationOptIn').checked,
            preferredNotificationChannel: document.getElementById('editStudentNotificationChannel').value,
            phoneE164: document.getElementById('editStudentPhoneE164').value || null,
            whatsappNumberE164: document.getElementById('editStudentWhatsappE164').value || null,
            timezone: document.getElementById('editStudentTimezone').value || null,
            quietHoursStart: document.getElementById('editStudentQuietStart').value || null,
            quietHoursEnd: document.getElementById('editStudentQuietEnd').value || null
        };

        try {
            await API.updateStudent(studentId, data);
            Toast.success('Student updated successfully');
            Modal.close();
            this.load();
            Calendar.loadStudentsCache();
        } catch (error) {
            Toast.error('Failed to update student: ' + error.message);
        }
    },

    deactivateStudent(studentId) {
        Modal.confirm(
            'Deactivate Student',
            'Are you sure you want to deactivate this student?',
            async () => {
                try {
                    await API.deactivateStudent(studentId);
                    Toast.success('Student deactivated');
                    this.load();
                    Calendar.loadStudentsCache();
                } catch (error) {
                    Toast.error('Failed to deactivate student: ' + error.message);
                }
            },
            'Deactivate',
            'btn-warning'
        );
    },

    async showStudentHistory(studentId) {
        try {
            const student = await API.getStudent(studentId);
            const today = new Date();
            const threeMonthsAgo = new Date(today);
            threeMonthsAgo.setMonth(threeMonthsAgo.getMonth() - 3);

            const from = Utils.formatDateInput(threeMonthsAgo);
            const to = Utils.formatDateInput(today);

            const response = await API.getStudentSlots(studentId, from, to);
            const slots = response.content;

            const slotsHtml = slots.length > 0 ? `
                <table class="data-table" style="font-size: 0.875rem;">
                    <thead>
                        <tr>
                            <th>Date</th>
                            <th>Time</th>
                            <th>Status</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${slots.map(slot => `
                            <tr>
                                <td>${Utils.formatDate(slot.startAt)}</td>
                                <td>${Utils.formatTime(slot.startAt)} - ${Utils.formatTime(slot.endAt)}</td>
                                <td><span class="badge badge-${Calendar.getStatusBadge(slot.status)}">${slot.status}</span></td>
                            </tr>
                        `).join('')}
                    </tbody>
                </table>
            ` : '<p class="empty-state">No slots found for this period</p>';

            const content = `
                <div class="slot-detail-row">
                    <span class="slot-detail-label">Student</span>
                    <span class="slot-detail-value">${this.escapeHtml(student.fullName)}</span>
                </div>
                <div class="slot-detail-row">
                    <span class="slot-detail-label">Period</span>
                    <span class="slot-detail-value">Last 3 months</span>
                </div>
                <div style="margin-top: 16px;">
                    <h4 style="font-size: 0.875rem; color: var(--text-secondary); margin-bottom: 8px;">Slot History</h4>
                    ${slotsHtml}
                </div>
                <div class="modal-actions">
                    <button type="button" class="btn btn-secondary" id="closeHistoryBtn">Close</button>
                </div>
            `;

            Modal.open('Student Slot History', content);
            document.getElementById('closeHistoryBtn').addEventListener('click', () => Modal.close());
        } catch (error) {
            Toast.error('Failed to load student history: ' + error.message);
        }
    },

    async inviteStudent(studentId, btn) {
        // Guard against concurrent clicks on the same student
        if (this.inviteInProgress.has(studentId)) return;
        this.inviteInProgress.add(studentId);

        if (btn) {
            btn.disabled = true;
            btn.textContent = 'Sending…';
        }

        try {
            await API.inviteStudent(studentId);
            Toast.success('Invitation sent successfully');
            this.load(); // reload table — button disappears once user account exists
        } catch (error) {
            Toast.error('Failed to send invitation: ' + error.message);
            if (btn) {
                btn.disabled = false;
                btn.textContent = 'Invite';
            }
            this.inviteInProgress.delete(studentId);
        }
    },

    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
};
