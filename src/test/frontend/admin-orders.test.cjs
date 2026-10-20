const test = require('node:test');
const assert = require('node:assert/strict');
const {load} = require('./admin-harness.cjs');

test('order controls only offer valid forward transitions', () => {
    const {context} = load('admin-orders.js');
    assert.deepEqual([...context.allowedOrderTransitions({status: 'PENDING_PAYMENT'})], ['CANCELLED']);
    assert.deepEqual([...context.allowedOrderTransitions({status: 'PROCESSING'})], ['CANCELLED']);
    assert.deepEqual([...context.allowedOrderTransitions({status: 'SHIPPING'})], []);
    assert.match(context.getOrderStatusControl({id: 7, status: 'CONFIRMED'}), /Đã xác nhận/);
    assert.doesNotMatch(context.getOrderStatusControl({id: 7, status: 'PENDING_PAYMENT'}), /value="PENDING"/);
});

test('captured VNPay orders cannot be cancelled directly', () => {
    const {context} = load('admin-orders.js');
    for (const paymentStatus of ['PAID', 'RECONCILIATION_REQUIRED']) {
        assert.deepEqual([...context.allowedOrderTransitions({status: 'CONFIRMED', paymentMethod: 'VNPAY', paymentStatus})], ['PROCESSING']);
    }
});

test('order list includes status parameter once', async () => {
    let requested;
    const {context, run} = load('admin-orders.js', {fetch: async url => {
        requested = url; return {ok: true, json: async () => ({content: [], totalPages: 0})};
    }});
    run("currentStatus = 'PENDING'");
    await context.loadOrders();
    assert.deepEqual(new URL(requested, 'https://beauty.test').searchParams.getAll('status'), ['PENDING']);
});

test('dashboard clears stock alerts after replenishment and retains order alerts', () => {
    const {context, document} = load('admin-dashboard.js');
    document.getElementById('pendingOrderAlert').classList.add('d-none');
    context.updateAlerts({lowStockProducts: 5, outOfStockProducts: 3});
    assert.equal(document.getElementById('noAlerts').classList.contains('d-none'), true);
    context.updateAlerts({lowStockProducts: 0, outOfStockProducts: 0});
    assert.equal(document.getElementById('lowStockAlert').classList.contains('d-none'), true);
    assert.equal(document.getElementById('outOfStockAlert').classList.contains('d-none'), true);
    assert.equal(document.getElementById('noAlerts').classList.contains('d-none'), false);
    document.getElementById('pendingOrderAlert').classList.remove('d-none');
    context.updateNoAlerts();
    assert.equal(document.getElementById('noAlerts').classList.contains('d-none'), true);
});
