const test = require('node:test');
const assert = require('node:assert/strict');
const {load} = require('./admin-harness.cjs');

test('admin password policy matches backend constraints', () => {
    const {context} = load('admin-accounts.js');
    assert.equal(context.validAccountPassword('Valid123!'), true);
    for (const value of ['Ab1!', 'password123!', 'PASSWORD123!', 'Password!', 'Password123', 'Valid 123!', 'A1!' + 'a'.repeat(126)]) {
        assert.equal(context.validAccountPassword(value), false, value);
    }
});

test('account error responses are not rendered as successful empty lists', async () => {
    const {context} = load('admin-accounts.js');
    await assert.rejects(context.readAccountResponse({ok: false, json: async () => ({message: 'Session expired'})}), /Session expired/);
});

test('duplicate account submissions are ignored while pending', async () => {
    let calls = 0;
    const {context} = load('admin-accounts.js', {fetch: () => { calls++; return new Promise(() => {}); }});
    context.createAccountData({name: 'A'});
    context.createAccountData({name: 'A'});
    assert.equal(calls, 1);
});
