const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
function setup() {
    const nodes = new Map();
    const node = () => ({ dataset: {}, style: {}, events: {}, attributes: {}, children: [], focusCount: 0,
        classList: { add() {}, remove() {} }, focus() { this.focusCount++; }, appendChild(child) { this.children.push(child); },
        setAttribute(k, v) { this.attributes[k] = v; }, addEventListener(k, fn) { this.events[k] = fn; },
        hasChildNodes() { return this.children.length > 0; }, querySelector() { return node(); }, remove() {} });
    const get = id => { if (!nodes.has(id)) nodes.set(id, node()); return nodes.get(id); };
    const timers = new Set(); let timer = 0;
    const context = { console, setTimeout() {}, localStorage: { getItem() {} },
        document: { hidden: false, getElementById: get, createElement: node, addEventListener() {} },
        window: { addEventListener() {}, setInterval() { const id = ++timer; timers.add(id); return id; }, clearInterval(id) { timers.delete(id); } },
        fetch: async () => ({ ok: true, json: async () => ({ sessionId: 'session-test' }) }) };
    vm.createContext(context);
    vm.runInContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/chatbot.js'), 'utf8'), context);
    return { context, get, timers };
}
test('chat widget does not steal initial keyboard focus; close restores focus', () => {
    const { context, get } = setup();
    assert.equal(get('chatbot-toggle').focusCount, 0);
    context.window._beautystoreChatbot.openBubble();
    get('chatbot-close').events.click({ type: 'click' });
    assert.equal(get('chatbot-toggle').focusCount, 1);
    assert.equal(get('chatbot-bubble').attributes['aria-hidden'], 'true');
});
test('closing during support connect cannot restart polling or mark hidden messages read', async () => {
    const { context, get, timers } = setup();
    const requests = []; let resolve;
    context.fetch = url => { requests.push(url); return new Promise(r => { resolve = r; }); };
    context.window._beautystoreChatbot.openBubble();
    const connect = get('chatbot-human-support').events.click();
    context.window._beautystoreChatbot.closeBubble();
    resolve({ ok: true, json: async () => ({ sessionId: 'session-test' }) });
    await connect;
    assert.equal(timers.size, 0);
    assert.deepEqual(requests, ['/api/support/session']);
});
test('suggestions use native button and collapsed children are inert', () => {
    const { get } = setup();
    assert.match(get('chatbot-root').innerHTML, /<button[^>]+id="suggestions-toggle"/);
    assert.equal(get('chatbot-suggestions').inert, true);
    get('suggestions-toggle').events.click();
    assert.equal(get('chatbot-suggestions').inert, false);
    assert.equal(get('suggestions-toggle').attributes['aria-expanded'], 'true');
});
