const test = require('node:test');
const assert = require('node:assert/strict');
const {harness, response} = require('./auth-harness.cjs');

test('password validation matches backend bounds and character classes', () => {
    const {window} = harness([]);
    for (const invalid of ['Aa1!aaa', 'aaaaaaaa', 'AA11!!!!', 'Aa1! aaaa', 'Aa1!\taaaa', 'Aa1!\naaaa', 'Aa1!' + 'a'.repeat(125)]) {
        assert.throws(() => window.BeautyAuth.validatePassword(invalid, invalid));
    }
    assert.doesNotThrow(() => window.BeautyAuth.validatePassword('Strong123!', 'Strong123!'));
    assert.throws(() => window.BeautyAuth.validatePassword('Strong123!', 'Other123!'));
});

test('guest badge uses cookie cart and no bearer header', async () => {
    const app = harness(['cartBadge'], {fetch: async () => response({totalItems: 3})});
    await app.ready();
    assert.equal(app.calls.length, 1);
    assert.equal(app.calls[0].config.credentials, 'same-origin');
    assert.equal(app.calls[0].config.headers.Authorization, undefined);
    assert.equal(app.nodes.cartBadge.textContent, '3');
});

test('logout attaches once and clears every legacy key', async () => {
    const app = harness(['logoutLink'], {local: {authToken: 'old', token: 'older', userRole: 'ROLE_ADMIN'}});
    await app.ready();
    assert.equal(app.nodes.logoutLink.listeners.click.length, 1);
    await app.nodes.logoutLink.fire('click');
    assert.equal(app.calls.filter(call => call.url.endsWith('/logout')).length, 1);
    assert.equal(app.localStorage.getItem('token'), null);
    assert.equal(app.localStorage.getItem('authToken'), null);
    assert.equal(app.localStorage.getItem('userRole'), null);
});

test('login blocks duplicate submit while pending and clears stale role', async () => {
    let finish;
    const app = harness(['loginForm', 'loginEmail', 'loginPassword', 'loginMessage'], {
        local: {userRole: 'ROLE_ADMIN'}, fetch: () => new Promise(resolve => { finish = resolve; })
    });
    await app.ready();
    app.nodes.loginEmail.value = 'user@example.test';
    app.nodes.loginPassword.value = 'Strong123!';
    const first = app.nodes.loginForm.fire('submit');
    await app.nodes.loginForm.fire('submit');
    assert.equal(app.calls.length, 1);
    assert.equal(app.nodes.loginForm.submit.disabled, true);
    finish(response({token: 'new-token'}));
    await first;
    assert.equal(app.localStorage.getItem('userRole'), 'ROLE_USER');
    assert.equal(app.window.location.href, '/profile');
});

test('OTP email survives refresh and cooldown prevents resend', async () => {
    const app = harness(['verifyForm', 'verifyEmail', 'verifyOtp', 'verifyMessage', 'resendOtpButton'], {
        session: {verifyEmail: 'user@example.test', 'beautyOtp:activation:user@example.test': String(Date.now() + 60000)}
    });
    await app.ready();
    assert.equal(app.nodes.verifyEmail.value, 'user@example.test');
    assert.equal(app.sessionStorage.getItem('verifyEmail'), 'user@example.test');
    assert.equal(app.nodes.resendOtpButton.disabled, true);
    await app.nodes.resendOtpButton.fire('click');
    assert.equal(app.calls.length, 0);
});

test('malformed OTP never reaches verification API', async () => {
    const app = harness(['verifyForm', 'verifyEmail', 'verifyOtp', 'verifyMessage']);
    await app.ready();
    app.nodes.verifyOtp.value = 'abcdef';
    await app.nodes.verifyForm.fire('submit');
    assert.equal(app.calls.length, 0);
    assert.match(app.nodes.verifyMessage.textContent, /6 chữ số/);
});

test('revoked bearer clears session before fetching guest badge', async () => {
    const app = harness(['cartBadge'], {
        local: {authToken: 'stale', authEmail: 'user@example.test'},
        fetch: async (url, config) => response(url.endsWith('/cart') ? {totalItems: 2} : {}, config.headers?.Authorization ? 401 : 200)
    });
    await app.ready();
    assert.equal(app.localStorage.getItem('authToken'), null);
    assert.equal(app.calls.length, 3);
    assert.equal(app.nodes.cartBadge.textContent, '2');
});

test('late unauthorized badge response cannot revoke a newer login', async () => {
    let finish;
    const app = harness(['cartBadge'], {
        local: {authToken: 'old'},
        fetch: async (url, config) => config.headers?.Authorization === 'Bearer old'
            ? new Promise(resolve => { finish = resolve; }) : response({totalItems: 1})
    });
    await app.ready();
    app.localStorage.setItem('authToken', 'new');
    finish(response({}, 401));
    await new Promise(resolve => setImmediate(resolve));
    assert.equal(app.localStorage.getItem('authToken'), 'new');
    assert.equal(app.calls.some(call => call.url.endsWith('/logout')), false);
});

test('login returns to the requested same-origin storefront path', async () => {
    const app = harness(['loginForm', 'loginEmail', 'loginPassword', 'loginMessage'], {
        search: '?redirect=' + encodeURIComponent('/favorites?sort=newest#saved'),
        fetch: async () => response({token: 'new', role: 'ROLE_USER'})
    });
    await app.ready();
    await app.nodes.loginForm.fire('submit');
    assert.equal(app.window.location.href, '/favorites?sort=newest#saved');
});

test('login rejects external redirects, encoded separators and authentication loops', async () => {
    for (const target of ['//evil.example', 'https://evil.example', 'javascript:alert(1)', '/\\evil.example',
        '/%2f%2fevil.example', '/%5cevil.example', '/\nevil.example', '/login', '/register',
        '/forgot-password', '/reset-password', '/verify-otp', '/folder/../login', '/%6cogin', '/api/auth/logout',
        '/folder/%2e%2e/%2fevil.example', '/%252fevil.example', '/login%3fredirect=bad']) {
        const app = harness(['loginForm', 'loginEmail', 'loginPassword', 'loginMessage'], {
            search: '?redirect=' + encodeURIComponent(target),
            fetch: async () => response({token: 'new', role: 'ROLE_USER'})
        });
        await app.ready();
        await app.nodes.loginForm.fire('submit');
        assert.equal(app.window.location.href, '/profile', target);
    }
});
