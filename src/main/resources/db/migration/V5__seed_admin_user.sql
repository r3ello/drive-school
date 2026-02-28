INSERT INTO users (id, email, password_hash, full_name, role, status, student_id, password_change_required)
VALUES (
    gen_random_uuid(),
    'admin@admin.com',
    '$2a$10$oSXaXjWMfW2RTkhFbgxwMup5KLekWjzhxrKFG7u4ghmvMYvNgHU7e',
    'admin',
    'ADMIN',
    'ACTIVE',
    NULL,
    FALSE
);
