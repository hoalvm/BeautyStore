const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
function load() {
    const timers = []; const intervals = []; const nodes = new Map(); const storage = new Map();
    const window = {location: {origin:'https://beauty.test'}, addEventListener() {}};
    const context = {console, Date, Intl, URL, Response, window,
        setTimeout(fn,delay) {timers.push({fn,delay}); return timers.length;}, clearTimeout() {},
        setInterval(fn,delay) {intervals.push({fn,delay}); return intervals.length;}, clearInterval() {},
        localStorage: {getItem() {return null;}}, sessionStorage: {getItem: key => storage.get(key)},
        document: {addEventListener() {}, getElementById(id) {if (!nodes.has(id)) nodes.set(id,{classList:{add(){}}}); return nodes.get(id);}},
        fetch: async () => {throw new Error('Unexpected request');}};
    vm.createContext(context);
    vm.runInContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/beauty-ui.js'),'utf8'),context);
    context.BeautyUI = window.BeautyUI;
    vm.runInContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/payment-pending.js'),'utf8'),context);
    return {context,timers,intervals,nodes,storage};
}
test('already expired reservation does not start a repeating countdown', () => {
    const {context, intervals, nodes} = load();
    context.startStatusPolling({reservationExpiresAt:'2000-01-01T12:15:00'});
    context.startCountdown({reservationExpiresAt:'2000-01-01T12:15:00'});
    assert.equal(intervals.length,0);
    assert.equal(nodes.get('timeRemaining').textContent,'Đã hết hạn');
});
test('valid reservation without createdAt still starts countdown', () => {
    const {context, intervals} = load();
    context.startCountdown({reservationExpiresAt:new Date(Date.now()+600000).toISOString()});
    assert.equal(intervals.length,1);
});
test('stopped polling cannot be revived by an in-flight continuation', () => {
    const {context,timers} = load(); context.startStatusPolling({}); const before = timers.length;
    context.stopStatusPolling(); context.scheduleStatusPoll(0,true);
    assert.equal(timers.length,before);
});
test('reconciliation is terminal even when order status remains pending', () => {
    const {context} = load();
    assert.equal(context.isFinalPaymentState({status:'PENDING_PAYMENT',paymentStatus:'RECONCILIATION_REQUIRED'}),true);
    assert.equal(context.isFinalPaymentState({status:'PENDING_PAYMENT',paymentStatus:'PENDING'}),false);
});
test('unscoped token from a different guest order is not reused', async () => {
    const {context,storage} = load(); storage.set('guestOrderToken','other-order-token');
    assert.equal((await context.fetchOrder('NEW-ORDER')).status,401);
});
