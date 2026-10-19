const test = require('node:test');
const assert = require('node:assert/strict');
const {load} = require('./admin-harness.cjs');

test('switching sessions is not blocked by previous session poll', async () => {
    const requests = [];
    const responses = [];
    const {context, run} = load('admin-support.js', {fetch: url => {
        requests.push(url); return new Promise(resolve => responses.push(resolve));
    }});
    run("currentSessionId = 'A'");
    const first = context.loadMessages('A');
    run("currentSessionId = 'B'");
    const second = context.loadMessages('B');
    assert.deepEqual(requests, ['/api/support/session/A/messages', '/api/support/session/B/messages']);
    run("currentSessionId = 'C'");
    responses.forEach(resolve => resolve({ok: true, status: 200, json: async () => []}));
    await Promise.all([first, second]);
});

test('support prevents duplicate sends and preserves another session draft', async () => {
    let resolveResponse;
    let calls = 0;
    const {context, run, document} = load('admin-support.js', {fetch: () => {
        calls++; return new Promise(resolve => { resolveResponse = resolve; });
    }});
    context.loadSessions = () => {};
    context.loadMessages = async () => {};
    run("currentSessionId = 'A'");
    document.getElementById('messageInput').value = 'Hello A';
    const first = context.sendMessage({preventDefault() {}});
    await context.sendMessage({preventDefault() {}});
    assert.equal(calls, 1);
    run("currentSessionId = 'B'");
    document.getElementById('messageInput').value = 'Draft B';
    resolveResponse({ok: true, status: 200, json: async () => ({})});
    await first;
    assert.equal(document.getElementById('messageInput').value, 'Draft B');
    assert.equal(document.getElementById('messageInput').disabled, false);
});
