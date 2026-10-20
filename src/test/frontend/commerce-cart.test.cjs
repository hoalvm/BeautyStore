const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
function load() {
    const storage = new Map(); const controls = [{id: 'quantity', dataset: {}, disabled: false}, {id: 'checkoutBtn', dataset: {}, disabled: false}];
    const nodes = new Map();
    const context = {console, Intl, URLSearchParams, setTimeout() {}, window: {},
        localStorage: {getItem: key => storage.get(key) ?? null, setItem: (key,value) => storage.set(key,String(value)), removeItem: key => storage.delete(key)},
        document: {addEventListener() {}, querySelectorAll: selector => selector === '[data-cart-was-disabled]' ? controls.filter(c => 'cartWasDisabled' in c.dataset) : controls,
            getElementById(id) {if (!nodes.has(id)) nodes.set(id, {style: {}, dataset: {}, addEventListener() {}, hasAttribute: () => true}); return nodes.get(id);}},
        fetch: async () => {throw new Error('Unexpected request');}};
    vm.createContext(context); vm.runInContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/cart.js'), 'utf8'), context);
    context.showNotification = () => {}; return {context, controls, storage, nodes};
}
test('cart rejects concurrent mutation until server refresh finishes', async () => {
    const {context, controls} = load(); let resolve; let requests=0;
    context.fetch = () => {requests++; return new Promise(r => {resolve=r;});};
    context.loadCart = async () => {};
    const pending = context.updateCartItemQuantity(1, 2);
    assert.equal(controls[0].disabled, true);
    await context.updateCartItemQuantity(1, 3);
    assert.equal(requests, 1);
    resolve({ok:true}); await pending;
    assert.equal(controls[0].disabled, false);
});
test('cart rejects noninteger and out-of-range quantities without request', async () => {
    const {context} = load(); let requests=0; context.fetch = async () => {requests++;};
    for (const value of [NaN, 0, -1, 1.2, 1000]) await context.updateCartItemQuantity(1, value);
    assert.equal(requests, 0);
});
test('cart subtotal changes invalidate stale voucher and zero all totals', () => {
    const {context, storage, nodes} = load();
    storage.set('cartSubtotal', '100000'); storage.set('voucherCode','OLD'); storage.set('voucherDiscount','50000');
    context.updateCartSummary({totalPrice: 0, totalItems: 0});
    assert.equal(storage.has('voucherCode'),false);
    assert.equal(storage.get('cartSubtotal'),'0');
    assert.equal(nodes.get('subtotalPrice').textContent, context.formatPrice(0));
    assert.equal(nodes.get('totalPrice').textContent, context.formatPrice(0));
});
test('cart does not classify an unknown zero-discount voucher as free shipping', () => {
    const {context,storage} = load(); storage.set('voucherCode','UNKNOWN'); storage.set('voucherDiscount','0');
    assert.equal(context.getStoredVoucherState().freeShipping,false);
});
