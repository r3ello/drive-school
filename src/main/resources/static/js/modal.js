// Modal Management
const Modal = {
    overlay: null,
    modal: null,
    title: null,
    body: null,
    closeBtn: null,

    init() {
        this.overlay = document.getElementById('modalOverlay');
        this.modal = document.getElementById('modal');
        this.title = document.getElementById('modalTitle');
        this.body = document.getElementById('modalBody');
        this.closeBtn = document.getElementById('modalClose');

        this.closeBtn.addEventListener('click', () => this.close());
        this.overlay.addEventListener('click', (e) => {
            if (e.target === this.overlay) {
                this.close();
            }
        });

        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && this.overlay.classList.contains('active')) {
                this.close();
            }
        });
    },

    open(title, content) {
        this.title.textContent = title;
        if (typeof content === 'string') {
            this.body.innerHTML = content;
        } else {
            this.body.innerHTML = '';
            this.body.appendChild(content);
        }
        this.overlay.classList.add('active');
    },

    close() {
        this.overlay.classList.remove('active');
        this.body.innerHTML = '';
    },

    confirm(title, message, onConfirm, confirmText = 'Confirm', confirmClass = 'btn-danger') {
        const content = `
            <p style="margin-bottom: 20px;">${message}</p>
            <div class="modal-actions">
                <button class="btn btn-secondary" id="modalCancelBtn">Cancel</button>
                <button class="btn ${confirmClass}" id="modalConfirmBtn">${confirmText}</button>
            </div>
        `;
        this.open(title, content);

        document.getElementById('modalCancelBtn').addEventListener('click', () => this.close());
        document.getElementById('modalConfirmBtn').addEventListener('click', () => {
            onConfirm();
            this.close();
        });
    }
};

// Toast Notifications
const Toast = {
    container: null,

    init() {
        this.container = document.getElementById('toastContainer');
    },

    show(message, type = 'success', duration = 4000) {
        const toast = document.createElement('div');
        toast.className = `toast ${type}`;
        toast.innerHTML = `
            <span>${type === 'success' ? '✓' : type === 'error' ? '✕' : '⚠'}</span>
            <span>${message}</span>
        `;
        this.container.appendChild(toast);

        setTimeout(() => {
            toast.style.animation = 'slideIn 0.3s ease reverse';
            setTimeout(() => toast.remove(), 300);
        }, duration);
    },

    success(message) {
        this.show(message, 'success');
    },

    error(message) {
        this.show(message, 'error', 6000);
    },

    warning(message) {
        this.show(message, 'warning');
    }
};
