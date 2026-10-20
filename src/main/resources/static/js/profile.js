/* Profile-only handlers are scoped so auth.js cannot attach duplicate requests. */
(function () {
    'use strict';
    const field = id => document.getElementById(id);
    const auth = () => window.BeautyAuth;
    const roles = {ROLE_USER: 'Khách hàng', ROLE_ADMIN: 'Quản trị viên', ROLE_SHIPPER: 'Người giao hàng'};

    async function request(path, options = {}) {
        const token = auth().token();
        if (!token) { auth().navigate('/login?error=unauthorized'); return null; }
        const response = await fetch('/api/auth' + path, {
            ...options, credentials: 'same-origin',
            headers: {'Content-Type': 'application/json', Authorization: 'Bearer ' + token}
        });
        if (response.status === 401) {
            await window.clearBeautySession();
            auth().navigate('/login?error=unauthorized');
            return null;
        }
        const data = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(data.message || 'Không thể thực hiện yêu cầu. Vui lòng thử lại.');
        return data;
    }

    function renderProfile(user) {
        const container = field('profileInfo');
        const heading = document.createElement('h2');
        heading.className = 'h4 mb-3 text-break';
        heading.textContent = user.name || 'Khách hàng BeautyStore';
        const details = document.createElement('dl');
        details.className = 'row mb-0 text-break';
        [
            ['Email', user.email], ['Điện thoại', user.phone], ['Địa chỉ', user.address],
            ['Vai trò', (Array.isArray(user.roles) ? user.roles : []).map(role => roles[role] || role).join(', ')]
        ].forEach(([label, value]) => {
            const term = document.createElement('dt');
            term.className = 'col-sm-4';
            term.textContent = label;
            const description = document.createElement('dd');
            description.className = 'col-sm-8';
            description.textContent = value || 'Chưa cập nhật';
            details.append(term, description);
        });
        container.replaceChildren(heading, details);
        field('profileName').value = user.name || '';
        field('profilePhone').value = user.phone || '';
        field('profileAddress').value = user.address || '';
    }

    async function loadProfile() {
        const form = field('updateProfileForm');
        const submit = form.querySelector('[type="submit"]');
        submit.disabled = true;
        field('profileInfo').setAttribute('aria-busy', 'true');
        try {
            const user = await request('/profile');
            if (user) { renderProfile(user); submit.disabled = false; }
        } catch (error) {
            auth().message('profileInfo', 'Không thể tải hồ sơ. Vui lòng tải lại trang để thử lại.', true);
        } finally { field('profileInfo').setAttribute('aria-busy', 'false'); }
    }

    async function submitProfile(event) {
        event.preventDefault();
        const form = event.currentTarget;
        if (form.dataset.submitting === 'true' || !form.reportValidity()) return;
        const submit = form.querySelector('[type="submit"]');
        form.dataset.submitting = 'true';
        form.setAttribute('aria-busy', 'true');
        submit.disabled = true;
        try {
            const user = await request('/profile', {
                method: 'PUT', body: JSON.stringify({
                    name: field('profileName').value.trim() || null,
                    phone: field('profilePhone').value.trim() || null,
                    address: field('profileAddress').value.trim()
                })
            });
            if (user) {
                auth().message('profileMessage', 'Cập nhật thông tin thành công.');
                await loadProfile();
            }
        } catch (error) {
            auth().message('profileMessage', error instanceof TypeError ? 'Không thể kết nối. Vui lòng thử lại.' : error.message, true);
        } finally {
            form.dataset.submitting = 'false';
            form.setAttribute('aria-busy', 'false');
            submit.disabled = false;
        }
    }

    function changePassword() {
        const form = field('changePasswordForm');
        form.reset();
        form.querySelectorAll('input').forEach(input => { input.type = 'password'; });
        form.querySelectorAll('[data-password-toggle]').forEach(button => {
            button.setAttribute('aria-pressed', 'false');
            button.setAttribute('aria-label', 'Hiện mật khẩu');
            button.querySelector('i').className = 'fas fa-eye';
        });
        field('passwordMessage').className = 'd-none';
        bootstrap.Modal.getOrCreateInstance(field('changePasswordModal')).show();
    }

    function togglePassword(inputId, button) {
        const input = field(inputId);
        if (!input || !button) return;
        const shown = input.type === 'password';
        input.type = shown ? 'text' : 'password';
        button.setAttribute('aria-pressed', String(shown));
        button.setAttribute('aria-label', shown ? 'Ẩn mật khẩu' : 'Hiện mật khẩu');
        button.querySelector('i').className = shown ? 'fas fa-eye-slash' : 'fas fa-eye';
    }

    async function submitPassword(event) {
        event.preventDefault();
        const form = event.currentTarget;
        if (form.dataset.submitting === 'true' || !form.reportValidity()) return;
        const currentPassword = field('currentPassword').value;
        const newPassword = field('newPassword').value;
        try {
            auth().validatePassword(newPassword, field('confirmPassword').value);
            if (currentPassword === newPassword) throw new Error('Mật khẩu mới phải khác mật khẩu hiện tại.');
        } catch (error) { auth().message('passwordMessage', error.message, true); return; }
        const submit = form.querySelector('[type="submit"]');
        form.dataset.submitting = 'true';
        form.setAttribute('aria-busy', 'true');
        submit.disabled = true;
        try {
            const result = await request('/change-password', {method: 'POST', body: JSON.stringify({currentPassword, newPassword})});
            if (result) {
                // The backend increments auth_version; this bearer token is now revoked.
                form.reset();
                await window.clearBeautySession();
                auth().navigate('/login?passwordChanged=1');
            }
        } catch (error) {
            auth().message('passwordMessage', error instanceof TypeError ? 'Không thể kết nối. Vui lòng thử lại.' : error.message, true);
        } finally {
            form.dataset.submitting = 'false';
            form.setAttribute('aria-busy', 'false');
            submit.disabled = false;
        }
    }

    document.addEventListener('DOMContentLoaded', () => {
        if (!field('profileInfo')) return;
        field('updateProfileForm').addEventListener('submit', submitProfile);
        field('changePasswordForm').addEventListener('submit', submitPassword);
        field('changePasswordModal').addEventListener('shown.bs.modal', () => field('currentPassword').focus());
        loadProfile();
    });
    window.changePassword = changePassword;
    window.togglePassword = togglePassword;
})();
