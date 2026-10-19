const test = require('node:test');
const assert = require('node:assert/strict');
const {harness, response, element} = require('./auth-harness.cjs');
const ids = ['profileInfo', 'updateProfileForm', 'profileName', 'profilePhone', 'profileAddress', 'profileMessage',
    'changePasswordForm', 'changePasswordModal', 'currentPassword', 'newPassword', 'confirmPassword', 'passwordMessage', 'logoutButton'];
const member = {authToken: 'token', authEmail: 'user@example.test'};

test('auth and profile together load profile once and attach one update/logout handler', async () => {
    const app = harness(ids, {profile: true, local: member, fetch: async () => response({email: 'user@example.test', roles: ['ROLE_USER']})});
    await app.ready();
    assert.equal(app.calls.length, 1);
    assert.equal(app.calls[0].url, '/api/auth/profile');
    assert.equal(app.nodes.updateProfileForm.listeners.submit.length, 1);
    assert.equal(app.nodes.logoutButton.listeners.click.length, 1);
});

test('profile renders user input as text and displays roles array', async () => {
    const name = '<img src=x onerror=alert(1)>';
    const app = harness(ids, {profile: true, local: member, fetch: async () => response({name, roles: ['ROLE_ADMIN']})});
    await app.ready();
    assert.equal(app.nodes.profileInfo.children[0].textContent, name);
    assert.equal(app.nodes.profileInfo.children[1].children.at(-1).textContent, 'Quản trị viên');
});

test('password change clears revoked JWT and redirects to sign in', async () => {
    const app = harness(ids, {profile: true, local: member});
    await app.ready();
    app.nodes.currentPassword.value = 'Current123!';
    app.nodes.newPassword.value = app.nodes.confirmPassword.value = 'Changed123!';
    await app.nodes.changePasswordForm.fire('submit');
    assert.equal(app.localStorage.getItem('authToken'), null);
    assert.equal(app.window.location.href, '/login?passwordChanged=1');
    assert.ok(app.calls.some(call => call.url.endsWith('/logout')));
});

test('same password is rejected before issuing change request', async () => {
    const app = harness(ids, {profile: true, local: member});
    await app.ready();
    app.nodes.currentPassword.value = app.nodes.newPassword.value = app.nodes.confirmPassword.value = 'Current123!';
    await app.nodes.changePasswordForm.fire('submit');
    assert.equal(app.calls.length, 1);
    assert.match(app.nodes.passwordMessage.textContent, /khác/);
});

test('password visibility works without browser-global event and announces state', () => {
    const app = harness(ids, {profile: true});
    const button = element();
    app.window.togglePassword('currentPassword', button);
    assert.equal(app.nodes.currentPassword.type, 'text');
    assert.equal(button.attributes['aria-pressed'], 'true');
    app.window.togglePassword('currentPassword', button);
    assert.equal(app.nodes.currentPassword.type, 'password');
});

test('expired profile session is cleared and sent to login', async () => {
    const app = harness(ids, {
        profile: true, local: member,
        fetch: async url => response({}, url.endsWith('/profile') ? 401 : 200)
    });
    await app.ready();
    assert.equal(app.localStorage.getItem('authToken'), null);
    assert.equal(app.window.location.href, '/login?error=unauthorized');
});

test('blank optional profile fields are sent as null instead of failing backend validation', async () => {
    const app = harness(ids, {profile: true, local: member});
    await app.ready();
    await app.nodes.updateProfileForm.fire('submit');
    const update = app.calls.find(call => call.config.method === 'PUT');
    assert.deepEqual(JSON.parse(update.config.body), {name: null, phone: null, address: ''});
});
