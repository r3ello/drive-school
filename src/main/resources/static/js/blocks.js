// Blocks View Management
const Blocks = {
    blocks: [],

    init() {
        this.setupEventListeners();
    },

    setupEventListeners() {
        document.getElementById('addBlockBtn').addEventListener('click', () => this.showAddBlockModal());
    },

    async load() {
        if (typeof Auth !== 'undefined' && Auth.isStudent()) {
            App.switchView('calendar');
            return;
        }

        const today = new Date();
        const threeMonthsAgo = new Date(today);
        threeMonthsAgo.setMonth(threeMonthsAgo.getMonth() - 1);
        const threeMonthsAhead = new Date(today);
        threeMonthsAhead.setMonth(threeMonthsAhead.getMonth() + 3);

        const from = threeMonthsAgo.toISOString();
        const to = threeMonthsAhead.toISOString();

        try {
            const response = await API.getBlocks(from, to);
            this.blocks = response.content;
            this.renderTable();
        } catch (error) {
            Toast.error('Failed to load blocks: ' + error.message);
        }
    },

    renderTable() {
        const tbody = document.getElementById('blocksTableBody');

        if (this.blocks.length === 0) {
            tbody.innerHTML = `
                <tr>
                    <td colspan="5" class="empty-state">
                        <div class="empty-state-icon">🚫</div>
                        <p>No blocked time ranges</p>
                    </td>
                </tr>
            `;
            return;
        }

        tbody.innerHTML = this.blocks.map(block => `
            <tr>
                <td>${Utils.formatDateTime(block.from)}</td>
                <td>${Utils.formatDateTime(block.to)}</td>
                <td>${block.reason ? this.escapeHtml(block.reason) : '-'}</td>
                <td>${Utils.formatDateTime(block.createdAt)}</td>
                <td>
                    <button class="btn btn-sm btn-danger" onclick="Blocks.deleteBlock('${block.id}')">Delete</button>
                </td>
            </tr>
        `).join('');
    },

    showAddBlockModal() {
        const now = new Date();
        const tomorrow = new Date(now);
        tomorrow.setDate(tomorrow.getDate() + 1);

        const content = `
            <form id="blockForm">
                <div class="form-group">
                    <label>From *</label>
                    <input type="datetime-local" id="blockFrom" required
                           value="${Utils.formatDateTimeInput(now)}">
                </div>
                <div class="form-group">
                    <label>To *</label>
                    <input type="datetime-local" id="blockTo" required
                           value="${Utils.formatDateTimeInput(tomorrow)}">
                </div>
                <div class="form-group">
                    <label>Reason</label>
                    <textarea id="blockReason" maxlength="500" placeholder="e.g., Vacation, Holiday..."></textarea>
                </div>
                <div class="modal-actions">
                    <button type="button" class="btn btn-secondary" id="cancelBlockBtn">Cancel</button>
                    <button type="submit" class="btn btn-primary">Create Block</button>
                </div>
            </form>
        `;

        Modal.open('Block Time Range', content);

        document.getElementById('cancelBlockBtn').addEventListener('click', () => Modal.close());
        document.getElementById('blockForm').addEventListener('submit', async (e) => {
            e.preventDefault();
            await this.createBlock();
        });
    },

    async createBlock() {
        const from = new Date(document.getElementById('blockFrom').value).toISOString();
        const to = new Date(document.getElementById('blockTo').value).toISOString();
        const reason = document.getElementById('blockReason').value;

        const data = {
            from,
            to,
            reason: reason || undefined
        };

        try {
            await API.createBlock(data);
            Toast.success('Block created successfully');
            Modal.close();
            this.load();
            // Refresh calendar to show blocked slots
            if (document.getElementById('calendarView').classList.contains('active')) {
                Calendar.load();
            }
        } catch (error) {
            Toast.error('Failed to create block: ' + error.message);
        }
    },

    deleteBlock(id) {
        Modal.confirm(
            'Delete Block',
            'Are you sure you want to delete this block? Affected slots will be unblocked.',
            async () => {
                try {
                    await API.deleteBlock(id);
                    Toast.success('Block deleted successfully');
                    this.load();
                    // Refresh calendar to show unblocked slots
                    if (document.getElementById('calendarView').classList.contains('active')) {
                        Calendar.load();
                    }
                } catch (error) {
                    Toast.error('Failed to delete block: ' + error.message);
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
