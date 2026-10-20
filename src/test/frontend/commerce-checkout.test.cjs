const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
function load() {
    const storage = new Map(); const nodes = new Map();
    const node = id => {
        if (!nodes.has(id)) nodes.set(id, {value: 'Customer', style: {}, dataset: {}, classList: {add() {}}, events: {},
            addEventListener(type, handler) {this.events[type] = handler;}, querySelectorAll() {return [];}, checkValidity: () => true});
        return nodes.get(id);
    };
    const context = {console, Intl, URLSearchParams, setTimeout() {}, window: {location: {}},
        localStorage: {getItem: key => storage.get(key) ?? null, setItem: (key,value) => storage.set(key,String(value)), removeItem: key => storage.delete(key)},
        document: {addEventListener() {}, getElementById: node, querySelector: () => ({value:'COD'})},
        fetch: async () => {throw new Error('Unexpected request');}};
    vm.createContext(context); vm.runInContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/checkout.js'), 'utf8'), context);
    context.showNotification = () => {};
    return {context, storage, nodes, node};
}
test('discount never consumes shipping fee and free threshold remains 500000', () => {
    const {context, storage, node} = load();
    storage.set('voucherCode','OLD'); storage.set('voucherDiscount','200000');
    context.updateCheckoutSummary({totalPrice:100000});
    assert.equal(node('totalAmount').textContent, context.formatPrice(30000));
    storage.clear(); context.updateCheckoutSummary({totalPrice:500000});
    assert.equal(node('totalAmount').textContent, context.formatPrice(500000));
});
test('zero amount discount is not guessed as free shipping', () => {
    const {context, storage} = load(); storage.set('voucherCode','UNKNOWN');
    assert.equal(context.getStoredVoucherState().freeShipping, false);
});
test('checkout refreshes voucher amount using authoritative cart validation', async () => {
    const {context, storage} = load(); storage.set('voucherCode','SAVE'); storage.set('voucherDiscount','99999');
    context.fetch = async () => ({ok:true,json:async()=>({valid:true,discountAmount:20000,discountType:'FIXED'})});
    await context.refreshCheckoutVoucher({});
    assert.equal(storage.get('voucherDiscount'),'20000');
    assert.equal(storage.get('voucherFreeShipping'),'false');
});
test('form submit supports keyboard and duplicate submits create one request', async () => {
    const {context, node} = load(); let requests=0; let reject;
    context.fetch = () => {requests++; return new Promise((_,r)=>{reject=r;});};
    context.setupCheckoutButton();
    vm.runInContext('checkoutCartState = {totalItems:1,unavailableItems:0}', context);
    const submit = node('checkoutForm').events.submit;
    const first = submit({preventDefault() {}});
    await submit({preventDefault() {}});
    assert.equal(requests,1);
    reject(new Error('offline')); await first;
    assert.equal(node('placeOrderBtn').disabled,false);
});
