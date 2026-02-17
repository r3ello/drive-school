// API Client for Calendar Application
const API = {
    baseUrl: '/api/v1',

    async request(endpoint, options = {}) {
        const url = `${this.baseUrl}${endpoint}`;
        const config = {
            headers: {
                'Content-Type': 'application/json',
                ...options.headers
            },
            ...options
        };

        try {
            const response = await fetch(url, config);

            if (response.status === 204) {
                return null;
            }

            const contentType = response.headers.get('content-type');
            const data = contentType && contentType.includes('application/json')
                ? await response.json()
                : contentType && contentType.includes('application/problem+json')
                    ? await response.json()
                    : null;

            if (!response.ok) {
                const error = new Error(data?.detail || data?.title || 'Request failed');
                error.status = response.status;
                error.data = data;
                throw error;
            }

            return data;
        } catch (error) {
            if (error.status) {
                throw error;
            }
            throw new Error('Network error. Please check your connection.');
        }
    },

    // Students
    async getStudents(params = {}) {
        const queryParams = new URLSearchParams();
        if (params.query) queryParams.append('query', params.query);
        if (params.active !== undefined && params.active !== '') queryParams.append('active', params.active);
        if (params.page !== undefined) queryParams.append('page', params.page);
        if (params.size !== undefined) queryParams.append('size', params.size);
        if (params.sort) queryParams.append('sort', params.sort);

        const queryString = queryParams.toString();
        return this.request(`/students${queryString ? '?' + queryString : ''}`);
    },

    async getStudent(id) {
        return this.request(`/students/${id}`);
    },

    async createStudent(data) {
        return this.request('/students', {
            method: 'POST',
            body: JSON.stringify(data)
        });
    },

    async updateStudent(id, data) {
        return this.request(`/students/${id}`, {
            method: 'PUT',
            body: JSON.stringify(data)
        });
    },

    async deactivateStudent(id) {
        return this.request(`/students/${id}/deactivate`, {
            method: 'PATCH'
        });
    },

    async getStudentSlots(studentId, from, to, status = null) {
        const params = new URLSearchParams({ from, to });
        if (status) params.append('status', status);
        return this.request(`/students/${studentId}/slots?${params}`);
    },

    // Slots
    async getSlots(from, to, status = null) {
        const params = new URLSearchParams({ from, to });
        if (status) params.append('status', status);
        return this.request(`/slots?${params}`);
    },

    async getSlot(id) {
        return this.request(`/slots/${id}`);
    },

    async createSlot(data) {
        return this.request('/slots', {
            method: 'POST',
            body: JSON.stringify(data)
        });
    },

    async deleteSlot(id) {
        return this.request(`/slots/${id}`, {
            method: 'DELETE'
        });
    },

    async generateSlots(data) {
        return this.request('/slots/generate', {
            method: 'POST',
            body: JSON.stringify(data)
        });
    },

    async bookSlot(slotId, data) {
        return this.request(`/slots/${slotId}/book`, {
            method: 'POST',
            body: JSON.stringify(data)
        });
    },

    async cancelSlot(slotId, data) {
        return this.request(`/slots/${slotId}/cancel`, {
            method: 'POST',
            body: JSON.stringify(data)
        });
    },

    async freeSlot(slotId, data = {}) {
        return this.request(`/slots/${slotId}/free`, {
            method: 'POST',
            body: JSON.stringify(data)
        });
    },

    async replaceSlot(slotId, data) {
        return this.request(`/slots/${slotId}/replace`, {
            method: 'POST',
            body: JSON.stringify(data)
        });
    },

    async rescheduleSlot(slotId, data) {
        return this.request(`/slots/${slotId}/reschedule`, {
            method: 'POST',
            body: JSON.stringify(data)
        });
    },

    async getSlotEvents(slotId) {
        return this.request(`/slots/${slotId}/events`);
    },

    // Events
    async getEvents(from, to, type = null, params = {}) {
        const queryParams = new URLSearchParams({ from, to });
        if (type) queryParams.append('type', type);
        if (params.page !== undefined) queryParams.append('page', params.page);
        if (params.size !== undefined) queryParams.append('size', params.size);
        return this.request(`/events?${queryParams}`);
    },

    // Blocks
    async getBlocks(from, to) {
        const params = new URLSearchParams({ from, to });
        return this.request(`/blocks?${params}`);
    },

    async createBlock(data) {
        return this.request('/blocks', {
            method: 'POST',
            body: JSON.stringify(data)
        });
    },

    async deleteBlock(id) {
        return this.request(`/blocks/${id}`, {
            method: 'DELETE'
        });
    },

    // Waitlist
    async getWaitlist(params = {}) {
        const queryParams = new URLSearchParams();
        if (params.active !== undefined) queryParams.append('active', params.active);
        if (params.page !== undefined) queryParams.append('page', params.page);
        if (params.size !== undefined) queryParams.append('size', params.size);
        if (params.sort) queryParams.append('sort', params.sort);

        const queryString = queryParams.toString();
        return this.request(`/waitlist${queryString ? '?' + queryString : ''}`);
    },

    async addToWaitlist(data) {
        return this.request('/waitlist', {
            method: 'POST',
            body: JSON.stringify(data)
        });
    },

    async removeFromWaitlist(id) {
        return this.request(`/waitlist/${id}`, {
            method: 'DELETE'
        });
    }
};

// Utility functions
const Utils = {
    formatDate(dateString) {
        const date = new Date(dateString);
        return date.toLocaleDateString('en-US', {
            weekday: 'short',
            month: 'short',
            day: 'numeric'
        });
    },

    formatTime(dateString) {
        const date = new Date(dateString);
        return date.toLocaleTimeString('en-US', {
            hour: '2-digit',
            minute: '2-digit',
            hour12: false
        });
    },

    formatDateTime(dateString) {
        const date = new Date(dateString);
        return date.toLocaleString('en-US', {
            weekday: 'short',
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
            hour12: false
        });
    },

    formatDateInput(date) {
        return date.toISOString().slice(0, 10);
    },

    formatDateTimeInput(date) {
        // Format as local datetime for datetime-local input (YYYY-MM-DDTHH:MM)
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');
        return `${year}-${month}-${day}T${hours}:${minutes}`;
    },

    getWeekStart(date) {
        const d = new Date(date);
        const day = d.getDay();
        const diff = d.getDate() - day + (day === 0 ? -6 : 1);
        d.setDate(diff);
        d.setHours(0, 0, 0, 0);
        return d;
    },

    getWeekEnd(date) {
        const start = this.getWeekStart(date);
        const end = new Date(start);
        end.setDate(end.getDate() + 6);
        return end;
    },

    addDays(date, days) {
        const result = new Date(date);
        result.setDate(result.getDate() + days);
        return result;
    },

    toISOString(date) {
        return date.toISOString();
    },

    getDayName(dayIndex) {
        const days = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
        return days[dayIndex];
    },

    getShortDayName(dayIndex) {
        const days = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
        return days[dayIndex];
    },

    startOfDay(date) {
        const d = new Date(date);
        d.setHours(0, 0, 0, 0);
        return d;
    }
};
