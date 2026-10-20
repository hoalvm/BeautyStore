/* Shared authentication UI; profile behavior belongs to profile.js. */
(function () {
    'use strict';
    const base = (document.querySelector('meta[name="ctx"]')?.content || '/').replace(/\/$/, '');
    const api = `${base}/api/auth`;
    const passwordHelp = 'Mật khẩu cần 8–128 ký tự, chữ hoa, chữ thường, số, ký tự đặc biệt và không có khoảng trắng.';
    const sessionKeys = ['authToken', 'token', 'authEmail', 'userEmail', 'userRole', 'userName'];
    const field = id => document.getElementById(id);
    const value = id => field(id)?.value || '';
    const token = () => localStorage.getItem('authToken') || localStorage.getItem('token');
    const email = () => localStorage.getItem('authEmail') || localStorage.getItem('userEmail');
    const navigate = path => { window.location.href = base + path; };

    function loginRedirect() {
        const candidate = new URLSearchParams(window.location.search).get('redirect');
        if (!candidate || !candidate.startsWith('/') || candidate.startsWith('//')) return null;
        try {
            const decoded = decodeURIComponent(candidate);
            if (/[\\\s\u0000-\u001f\u007f]/.test(candidate + decoded) || decoded.startsWith('//')) return null;
            const url = new URL(candidate, window.location.origin);
            if (url.origin !== window.location.origin) return null;
            let pathname = decodeURIComponent(url.pathname);
            if (pathname.startsWith('//') || /[\\\s%?#\u0000-\u001f\u007f]/.test(pathname)) return null;
            if (base && pathname.startsWith(base + '/')) pathname = pathname.slice(base.length);
            // Do not navigate back into authentication or to an API after signing in.
            if (/^\/(?:login|register|forgot-password|reset-password|verify-otp|api)(?:\/|;|$)/i.test(pathname)) return null;
            return pathname + url.search + url.hash;
        } catch (_) { return null; }
    }

    function message(id, text, error = false) {
        const node = field(id);
        if (!node) return;
        node.className = 'alert mt-3 ' + (error ? 'alert-danger' : 'alert-success');
        node.setAttribute('role', error ? 'alert' : 'status');
        node.setAttribute('aria-live', error ? 'assertive' : 'polite');
        node.textContent = text;
    }

    async function request(path, body) {
        const response = await fetch(api + path, {
            method: 'POST', credentials: 'same-origin',
            headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body)
        });
        const data = await response.json().catch(() => ({}));
        if (!response.ok) {
            const error = new Error(data.message || (response.status === 429
                ? 'Bạn thao tác quá nhanh. Vui lòng chờ một phút rồi thử lại.'
                : 'Không thể thực hiện yêu cầu. Vui lòng thử lại.'));
            error.status = response.status;
            throw error;
        }
        return data;
    }

    function updateAuthUI() {
        const signedIn = Boolean(token() && email());
        field('authButtons')?.classList.toggle('d-none', signedIn);
        field('userMenu')?.classList.toggle('d-none', !signedIn);
        if (field('userName')) field('userName').textContent = signedIn ? email().split('@')[0] : 'Tài khoản';
        const roles = signedIn ? localStorage.getItem('userRole') || '' : '';
        const admin = roles.includes('ADMIN');
        const shipper = roles.includes('SHIPPER');
        ['adminMenuLink', 'adminDashboardLink'].forEach(id => field(id)?.classList.toggle('d-none', !admin));
        field('shipperDashboardLink')?.classList.toggle('d-none', !shipper);
        field('dashboardMenuDivider')?.classList.toggle('d-none', !(admin || shipper));
    }

    function clearBeautySession() {
        sessionKeys.forEach(key => localStorage.removeItem(key));
        updateAuthUI();
        return fetch(api + '/logout', {method: 'POST', credentials: 'same-origin', keepalive: true})
            .catch(() => undefined);
    }

    async function logout(event) {
        event?.preventDefault();
        await clearBeautySession();
        navigate('/login');
    }

    async function updateCartBadge() {
        const badge = field('cartBadge');
        if (!badge) return;
        try {
            const bearer = token();
            const response = await fetch(base + '/api/cart', {
                credentials: 'same-origin', headers: bearer ? {Authorization: 'Bearer ' + bearer} : {}
            });
            if (response.status === 401 && bearer) {
                // A delayed response from the previous login must not clear a newer session.
                if (token() !== bearer) return updateCartBadge();
                await clearBeautySession();
                return updateCartBadge();
            }
            if (!response.ok) return;
            const cart = await response.json();
            const count = Math.max(0, Number(cart.totalItems) || 0);
            badge.textContent = count > 99 ? '99+' : String(count);
            badge.classList.toggle('d-none', count === 0);
            badge.setAttribute('aria-label', count + ' sản phẩm trong giỏ');
        } catch (_) { /* Badge failures must not interrupt shopping. */ }
    }

    function validatePassword(password, confirm) {
        if (!/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z\d])\S{8,128}$/.test(password)) throw new Error(passwordHelp);
        if (password !== confirm) throw new Error('Mật khẩu xác nhận không khớp.');
    }

    const cooldownKey = (kind, address) => 'beautyOtp:' + kind + ':' + address.trim().toLowerCase();
    const startCooldown = (kind, address) => sessionStorage.setItem(cooldownKey(kind, address), String(Date.now() + 60000));
    const remainingCooldown = (kind, address) => Math.max(0, Math.ceil(
        (Number(sessionStorage.getItem(cooldownKey(kind, address))) - Date.now()) / 1000));

    function bindForm(id, messageId, action) {
        const form = field(id);
        if (!form) return;
        form.addEventListener('submit', async event => {
            event.preventDefault();
            if (form.dataset.submitting === 'true' || !form.reportValidity()) return;
            const submit = form.querySelector('[type="submit"]');
            form.dataset.submitting = 'true';
            form.setAttribute('aria-busy', 'true');
            if (submit) submit.disabled = true;
            try { await action(); }
            catch (error) { message(messageId, error instanceof TypeError ? 'Không thể kết nối. Vui lòng thử lại.' : error.message, true); }
            finally {
                form.dataset.submitting = 'false';
                form.setAttribute('aria-busy', 'false');
                if (submit) submit.disabled = false;
            }
        });
    }

    function bindResend(buttonId, kind, emailId, messageId) {
        const button = field(buttonId);
        if (!button) return;
        const refresh = () => {
            const seconds = remainingCooldown(kind, value(emailId));
            button.disabled = button.dataset.sending === 'true' || seconds > 0;
            button.textContent = seconds > 0 ? 'Gửi lại mã sau ' + seconds + 's' : 'Gửi lại mã OTP';
        };
        refresh();
        const timer = window.setInterval(refresh, 1000);
        window.addEventListener('pagehide', () => window.clearInterval(timer), {once: true});
        field(emailId)?.addEventListener('input', refresh);
        button.addEventListener('click', async () => {
            const address = value(emailId).trim();
            if (button.disabled || !field(emailId).reportValidity()) return;
            button.dataset.sending = 'true';
            refresh();
            try {
                await request(kind === 'activation' ? '/active-account' : '/forgot-password', {email: address});
                startCooldown(kind, address);
                message(messageId, 'Nếu tài khoản phù hợp, mã OTP sẽ được gửi tới email. Mã có hiệu lực trong 5 phút.');
            } catch (error) {
                if (error.status === 429) startCooldown(kind, address);
                message(messageId, error instanceof TypeError ? 'Không thể gửi mã. Vui lòng thử lại.' : error.message, true);
            } finally { button.dataset.sending = 'false'; refresh(); }
        });
    }

    document.addEventListener('DOMContentLoaded', () => {
        updateAuthUI();
        updateCartBadge();
        field('logoutLink')?.addEventListener('click', logout);
        field('logoutButton')?.addEventListener('click', logout);
        ['verifyEmail', 'resetEmail'].forEach(id => {
            if (field(id) && sessionStorage.getItem(id)) field(id).value = sessionStorage.getItem(id);
        });

        bindForm('registerForm', 'registerMessage', async () => {
            const address = value('registerEmail').trim();
            const password = value('registerPassword');
            validatePassword(password, value('registerConfirmPassword'));
            await request('/register', {email: address, password, role: 'ROLE_USER'});
            sessionStorage.setItem('verifyEmail', address);
            startCooldown('activation', address);
            navigate('/verify-otp');
        });
        bindForm('loginForm', 'loginMessage', async () => {
            const address = value('loginEmail').trim();
            let data;
            try { data = await request('/login', {email: address, password: value('loginPassword')}); }
            catch (error) {
                if (!error.message.includes('Account not activated')) throw error;
                sessionStorage.setItem('verifyEmail', address);
                try {
                    await request('/active-account', {email: address});
                    startCooldown('activation', address);
                } catch (sendError) {
                    if (sendError.status === 429) startCooldown('activation', address);
                }
                navigate('/verify-otp');
                return;
            }
            if (!data.token) throw new Error('Không nhận được phiên đăng nhập. Vui lòng thử lại.');
            sessionKeys.forEach(key => localStorage.removeItem(key));
            localStorage.setItem('authToken', data.token);
            localStorage.setItem('authEmail', data.email || address);
            localStorage.setItem('userRole', data.role || 'ROLE_USER');
            navigate(loginRedirect() || (data.role?.includes('ADMIN') ? '/admin' : data.role?.includes('SHIPPER') ? '/shipper' : '/profile'));
        });
        bindForm('forgotForm', 'forgotMessage', async () => {
            const address = value('forgotEmail').trim();
            const seconds = remainingCooldown('reset', address);
            if (seconds > 0) throw new Error('Vui lòng chờ ' + seconds + 's trước khi gửi lại mã.');
            await request('/forgot-password', {email: address});
            sessionStorage.setItem('resetEmail', address);
            startCooldown('reset', address);
            navigate('/reset-password');
        });
        bindForm('verifyForm', 'verifyMessage', async () => {
            const otp = value('verifyOtp').trim();
            if (!/^\d{6}$/.test(otp)) throw new Error('Mã OTP phải gồm đúng 6 chữ số.');
            await request('/verify-account', {email: value('verifyEmail').trim(), otp});
            sessionStorage.removeItem('verifyEmail');
            navigate('/login?verified=1');
        });
        bindForm('resetForm', 'resetMessage', async () => {
            const newPassword = value('resetPassword');
            validatePassword(newPassword, value('resetConfirmPassword'));
            const otp = value('resetOtp').trim();
            if (!/^\d{6}$/.test(otp)) throw new Error('Mã OTP phải gồm đúng 6 chữ số.');
            await request('/reset-password', {email: value('resetEmail').trim(), otp, newPassword});
            sessionStorage.removeItem('resetEmail');
            await clearBeautySession();
            navigate('/login?passwordChanged=1');
        });
        bindResend('resendOtpButton', 'activation', 'verifyEmail', 'verifyMessage');
        bindResend('resendResetOtpButton', 'reset', 'resetEmail', 'resetMessage');
        const params = new URLSearchParams(window.location.search);
        if (params.get('passwordChanged') === '1') message('loginMessage', 'Mật khẩu đã được cập nhật. Vui lòng đăng nhập lại.');
        else if (params.get('verified') === '1') message('loginMessage', 'Tài khoản đã được kích hoạt. Bạn có thể đăng nhập.');
        else if (params.has('error')) message('loginMessage', params.get('error') === 'access_denied'
            ? 'Tài khoản chưa có quyền truy cập trang này.' : 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.', true);
    });

    window.addEventListener('storage', event => {
        if (!event.key || sessionKeys.includes(event.key)) { updateAuthUI(); updateCartBadge(); }
    });
    window.BeautyAuth = Object.freeze({token, message, validatePassword, passwordHelp, navigate});
    window.clearBeautySession = clearBeautySession;
    window.updateCartBadge = updateCartBadge;
    window.updateAuthUI = updateAuthUI;
})();
