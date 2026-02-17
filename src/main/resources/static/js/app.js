// Main Application
const App = {
    currentView: 'calendar',
    sidebarOpen: true,

    init() {
        // Initialize components
        Modal.init();
        Toast.init();
        Calendar.init();
        Students.init();
        Waitlist.init();
        Blocks.init();

        // Setup navigation & sidebar
        this.setupNavigation();
        this.setupSidebar();

        // Load initial view
        this.switchView('calendar');

        // Handle responsive sidebar on load
        this.handleResize();
        window.addEventListener('resize', () => this.handleResize());
    },

    setupNavigation() {
        document.querySelectorAll('.nav-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                const view = btn.dataset.view;
                this.switchView(view);
                // Close sidebar on mobile after navigating
                if (window.innerWidth <= 1024) {
                    this.closeSidebar();
                }
            });
        });
    },

    setupSidebar() {
        const toggle = document.getElementById('sidebarToggle');
        const closeBtn = document.getElementById('sidebarCloseBtn');
        const overlay = document.getElementById('sidebarOverlay');

        toggle.addEventListener('click', () => this.toggleSidebar());
        closeBtn.addEventListener('click', () => this.closeSidebar());
        overlay.addEventListener('click', () => this.closeSidebar());
    },

    toggleSidebar() {
        if (this.sidebarOpen) {
            this.closeSidebar();
        } else {
            this.openSidebar();
        }
    },

    openSidebar() {
        const sidebar = document.getElementById('sidebar');
        const mainContent = document.getElementById('mainContent');
        const overlay = document.getElementById('sidebarOverlay');

        sidebar.classList.remove('collapsed');
        mainContent.classList.remove('expanded');
        this.sidebarOpen = true;

        // On mobile, show overlay
        if (window.innerWidth <= 1024) {
            sidebar.classList.add('open');
            overlay.classList.add('active');
        }
    },

    closeSidebar() {
        const sidebar = document.getElementById('sidebar');
        const mainContent = document.getElementById('mainContent');
        const overlay = document.getElementById('sidebarOverlay');

        if (window.innerWidth <= 1024) {
            sidebar.classList.remove('open');
            overlay.classList.remove('active');
        } else {
            sidebar.classList.add('collapsed');
            mainContent.classList.add('expanded');
        }
        this.sidebarOpen = false;
    },

    handleResize() {
        const sidebar = document.getElementById('sidebar');
        const mainContent = document.getElementById('mainContent');
        const overlay = document.getElementById('sidebarOverlay');

        if (window.innerWidth <= 1024) {
            // On mobile, sidebar is hidden by default (CSS handles translateX(-100%))
            // Remove desktop collapsed/expanded classes
            sidebar.classList.remove('collapsed');
            mainContent.classList.remove('expanded');
            overlay.classList.remove('active');
            sidebar.classList.remove('open');
            this.sidebarOpen = false;
        } else {
            // On desktop, restore based on sidebarOpen state
            sidebar.classList.remove('open');
            overlay.classList.remove('active');
            if (this.sidebarOpen) {
                sidebar.classList.remove('collapsed');
                mainContent.classList.remove('expanded');
            }
        }
    },

    switchView(viewName) {
        // Update navigation
        document.querySelectorAll('.nav-btn').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.view === viewName);
        });

        // Update views
        document.querySelectorAll('.view').forEach(view => {
            view.classList.remove('active');
        });
        document.getElementById(`${viewName}View`).classList.add('active');

        // Update topbar title
        const titles = {
            calendar: 'Calendar',
            students: 'Students',
            waitlist: 'Waitlist',
            blocks: 'Blocked Times'
        };
        document.getElementById('topbarTitle').textContent = titles[viewName] || viewName;

        // Load view data
        this.currentView = viewName;
        this.loadViewData(viewName);
    },

    loadViewData(viewName) {
        switch (viewName) {
            case 'calendar':
                Calendar.load();
                break;
            case 'students':
                Students.load();
                break;
            case 'waitlist':
                Waitlist.load();
                break;
            case 'blocks':
                Blocks.load();
                break;
        }
    }
};

// Initialize app when DOM is ready
document.addEventListener('DOMContentLoaded', () => {
    App.init();
});
