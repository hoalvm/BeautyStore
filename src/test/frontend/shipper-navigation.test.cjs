const test = require('node:test');
const assert = require('node:assert/strict');
const {load} = require('./admin-harness.cjs');

test('tab navigation works without nonstandard global event', () => {
    const {context, document} = load('shipper-dashboard.js');
    let loaded = false;
    context.loadActiveOrders = () => { loaded = true; };
    assert.doesNotThrow(() => context.showTab('active'));
    assert.equal(document.getElementById('activeTab').style.display, 'block');
    assert.equal(loaded, true);
});

test('history filter is chosen before its asynchronous load starts', () => {
    const {context, run} = load('shipper-dashboard.js');
    let filter;
    context.loadHistory = () => { filter = run('currentFilter'); };
    context.showTabWithFilter('history', 'FAILED');
    assert.equal(filter, 'FAILED');
});

test('delivery mutations are serialized per order', async () => {
    let calls = 0;
    const {context} = load('shipper-dashboard.js', {fetch: () => { calls++; return new Promise(() => {}); }});
    context.acceptOrder(4);
    await context.completeOrder(4);
    assert.equal(calls, 1);
});
