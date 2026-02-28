// Authentication — token storage, refresh, and role helpers
const Auth = {
    KEYS: {
        ACCESS: 'calendar_access_token',
        REFRESH: 'calendar_refresh_token',
        USER: 'calendar_user'
    },

    // Mutex: single in-flight refresh promise shared by all concurrent callers.
    // Prevents the race where N simultaneous 401s each fire their own refresh,
    // causing the rotated refresh token to be consumed by the first and rejected
    // by the rest — which then wipe the fresh tokens the first call just saved.
    _refreshPromise: null,

    // Guard: once a redirect to login is in progress, don't start another.
    _redirecting: false,

    getAccessToken() {
        return localStorage.getItem(this.KEYS.ACCESS);
    },

    getRefreshToken() {
        return localStorage.getItem(this.KEYS.REFRESH);
    },

    getUser() {
        try {
            return JSON.parse(localStorage.getItem(this.KEYS.USER));
        } catch {
            return null;
        }
    },

    isAuthenticated() {
        return !!this.getAccessToken();
    },

    isTeacherOrAdmin() {
        const user = this.getUser();
        return user && (user.role === 'TEACHER' || user.role === 'ADMIN');
    },

    isStudent() {
        const user = this.getUser();
        return user && user.role === 'STUDENT';
    },

    saveTokens(data) {
        localStorage.setItem(this.KEYS.ACCESS, data.accessToken);
        if (data.refreshToken) {
            localStorage.setItem(this.KEYS.REFRESH, data.refreshToken);
        }
    },

    saveUser(user) {
        localStorage.setItem(this.KEYS.USER, JSON.stringify(user));
    },

    clearAll() {
        Object.values(this.KEYS).forEach(key => localStorage.removeItem(key));
    },

    async logout() {
        const refreshToken = this.getRefreshToken();
        if (refreshToken) {
            try {
                await fetch('/api/v1/auth/logout', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'Authorization': 'Bearer ' + this.getAccessToken()
                    },
                    body: JSON.stringify({ refreshToken })
                });
            } catch {
                // Clear tokens regardless of server response
            }
        }
        this.clearAll();
        window.location.href = '/login.html';
    },

    // Redirect to login exactly once, even if called concurrently.
    redirectToLogin() {
        if (this._redirecting) return;
        this._redirecting = true;
        this.clearAll();
        window.location.href = '/login.html';
    },

    async refreshAccessToken() {
        // If a refresh is already running, wait for it instead of firing a second one.
        // All concurrent callers share the same promise, so the refresh token is only
        // consumed once and the resulting new tokens are available to every caller.
        if (this._refreshPromise) {
            return this._refreshPromise;
        }

        this._refreshPromise = (async () => {
            const refreshToken = this.getRefreshToken();
            if (!refreshToken) {
                throw new Error('No refresh token available');
            }

            const response = await fetch('/api/v1/auth/refresh', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ refreshToken })
            });

            if (!response.ok) {
                throw new Error('Token refresh failed');
            }

            const data = await response.json();
            this.saveTokens(data);

            // Reconnect SSE with the fresh token
            if (typeof SSE !== 'undefined') {
                SSE.reconnect();
            }
        })().finally(() => {
            this._refreshPromise = null;
        });

        return this._refreshPromise;
    }
};
