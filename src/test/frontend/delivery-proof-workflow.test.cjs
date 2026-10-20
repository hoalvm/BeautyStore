const test = require('node:test');
const assert = require('node:assert/strict');
const {load, element} = require('./admin-harness.cjs');

function eventNode() {
    const node = element();
    node.listeners = {};
    node.addEventListener = (name, callback) => { node.listeners[name] = callback; };
    node.reset = () => {};
    node.setCustomValidity = value => { node.validationMessage = value; };
    return node;
}

test('today delivery filter uses deliveredAt instead of checkout date', () => {
    const {context} = load('shipper-dashboard.js');
    const today = new Date().toISOString();
    assert.equal(context.isDeliveredToday({status: 'DELIVERED', deliveredAt: today, createdAt: '2020-01-01'}), true);
    assert.equal(context.isDeliveredToday({status: 'SHIPPING', createdAt: today}), false);
    assert.equal(context.isDeliveredToday({status: 'DELIVERED', createdAt: today}), false);
});

function shipper(fetch) {
    const fields = new Map();
    class FormDataStub {
        constructor() { this.fields = new Map(); }
        append(key, value) { this.fields.set(key, value); }
    }
    let hidden = 0;
    const loaded = load('shipper-dashboard.js', {fetch, FormData: FormDataStub,
        bootstrap: {Modal: {getOrCreateInstance: () => ({show() {}, hide() { hidden++; }})}}});
    loaded.document.getElementById = id => {
        if (!fields.has(id)) fields.set(id, eventNode());
        return fields.get(id);
    };
    loaded.context.loadDashboardStats = () => {};
    loaded.context.loadActiveOrders = () => {};
    loaded.context.showToast = () => {};
    loaded.run('deliveryProofState.orderId = 7');
    const file = {size: 100, type: 'image/png', name: 'proof.png'};
    loaded.document.getElementById('deliveryProofFile').files = [file];
    return {...loaded, file, hidden: () => hidden};
}

test('shipper submits multipart with bearer token, prevents double submit and closes only on success', async () => {
    const requests = [];
    let finish;
    const state = shipper((url, options) => {
        requests.push({url, options});
        return new Promise(resolve => { finish = resolve; });
    });
    const pending = state.context.submitDeliveryProof({preventDefault() {}});
    await state.context.submitDeliveryProof({preventDefault() {}});
    assert.equal(requests.length, 1);
    assert.equal(state.run('deliveryProofState.busy'), true);
    assert.equal(requests[0].url, '/api/shipper/orders/7/complete');
    assert.equal(requests[0].options.method, 'PUT');
    assert.equal(requests[0].options.headers.Authorization, 'Bearer test-token');
    assert.equal(requests[0].options.headers['Content-Type'], undefined);
    assert.equal(requests[0].options.body.fields.get('image'), state.file);
    finish({ok: true});
    await pending;
    assert.equal(state.hidden(), 1);
    assert.equal(state.run('deliveryProofState.busy'), false);
});

test('network failure retains selected photo and allows a safe retry', async () => {
    let calls = 0;
    const state = shipper(async () => {
        if (++calls === 1) throw new Error('offline');
        return {ok: true};
    });
    await state.context.submitDeliveryProof({preventDefault() {}});
    assert.equal(state.hidden(), 0);
    assert.equal(state.document.getElementById('deliveryProofFile').files[0], state.file);
    assert.match(state.document.getElementById('deliveryProofStatus').textContent, /gửi lại an toàn/);
    await state.context.submitDeliveryProof({preventDefault() {}});
    assert.equal(calls, 2);
    assert.equal(state.hidden(), 1);
});

test('API rejection stays visible and invalid files never start a request', async () => {
    let calls = 0;
    const state = shipper(async () => {
        calls++;
        return {ok: false, json: async () => ({message: 'Không có quyền'})};
    });
    await state.context.submitDeliveryProof({preventDefault() {}});
    assert.equal(state.document.getElementById('deliveryProofStatus').textContent, 'Không có quyền');
    assert.equal(state.hidden(), 0);
    state.document.getElementById('deliveryProofFile').files = [{size: 100, type: 'text/html'}];
    await state.context.submitDeliveryProof({preventDefault() {}});
    assert.equal(calls, 1);
});

function viewer(fetch) {
    const revoked = [];
    const loaded = load('delivery-proof.js', {fetch, AbortController,
        URL: {createObjectURL: () => 'blob:private-photo', revokeObjectURL: value => revoked.push(value)}});
    loaded.document.createElement = eventNode;
    return {...loaded, revoked, container: eventNode(), api: loaded.context.window.DeliveryProof};
}

test('private viewer fetches on demand with scoped headers, never exposes tokens in URLs and revokes blobs', async () => {
    const requests = [];
    const state = viewer(async (url, options) => {
        requests.push({url, options});
        return {ok: true, blob: async () => ({type: 'image/jpeg'})};
    });
    state.api.mount(state.container, {id: 7, hasDeliveryProof: true, orderNumber: '<img onerror=alert(1)>'}, 'guest-secret');
    assert.equal(requests.length, 0);
    const card = state.container.children[0];
    const button = card.children[2];
    await button.listeners.click();
    assert.equal(requests[0].url, '/api/delivery-proofs/7');
    assert.equal(requests[0].options.headers['X-Order-Token'], 'guest-secret');
    assert.equal(requests[0].options.headers.Authorization, 'Bearer test-token');
    assert.equal(requests[0].options.cache, 'no-store');
    assert.equal(card.children[3].src, 'blob:private-photo');
    assert.equal(card.children[3].innerHTML, undefined);
    state.api.clear(state.container);
    assert.deepEqual(state.revoked, ['blob:private-photo']);
    assert.equal(requests[0].options.signal.aborted, true);
});

test('viewer refuses non-image responses, explains access denial and supports legacy delivered orders', async () => {
    for (const response of [
        {ok: false, status: 403}, {ok: true, blob: async () => ({type: 'text/html'})}
    ]) {
        const state = viewer(async () => response);
        state.api.mount(state.container, {id: 7, hasDeliveryProof: true});
        const card = state.container.children[0];
        await card.children[2].listeners.click();
        assert.equal(card.children.length, 3);
        assert.match(card.children[1].textContent, /quyền|định dạng/);
    }
    const state = viewer(async () => { throw new Error('Must not fetch legacy image'); });
    state.api.mount(state.container, {id: 8, status: 'DELIVERED', hasDeliveryProof: false});
    assert.match(state.container.children[0].children[1].textContent, /trước khi áp dụng/);
});

test('closing a viewer ignores a late response and does not leak an object URL', async () => {
    let finish;
    const state = viewer(() => new Promise(resolve => { finish = resolve; }));
    state.api.mount(state.container, {id: 7, hasDeliveryProof: true});
    const card = state.container.children[0];
    const loading = card.children[2].listeners.click();
    state.api.clear(state.container);
    finish({ok: true, blob: async () => ({type: 'image/jpeg'})});
    await loading;
    assert.equal(card.children.length, 3);
    assert.deepEqual(state.revoked, []);
});
