const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
function setup(search = '') {
    const elements = new Map();
    function node() { return { children: [], style: {}, textContent: '', append(...items) { this.children.push(...items); }, appendChild(item) { this.children.push(item); }, replaceChildren(...items) { this.children = items; }, addEventListener() {}, setAttribute() {} }; }
    const get = id => { if (!elements.has(id)) elements.set(id, node()); return elements.get(id); };
    const context = { URLSearchParams, AbortController, setTimeout, clearTimeout,
        document: { addEventListener() {}, getElementById: get, createElement: node, querySelectorAll: () => [] },
        window: { location: { search, pathname: '/products' }, addEventListener() {} },
        history: { pushState() {}, replaceState() {} },
        BeautyUI: { setBusy(node, value) { node.busy = value; } } };
    let source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/products.js'), 'utf8');
    source = source.replace(/\}\)\(\);\s*$/, 'globalThis.api = {state, readUrl, loadProducts, resetFilters};})();');
    vm.createContext(context); vm.runInContext(source, context);
    return { context, get };
}
test('URL filters reset on back navigation and reject fractional pages', () => {
    const { context } = setup('?page=1.5&ingredient=niacinamide&hairConcern=dry&finish=matte');
    context.api.readUrl();
    assert.equal(context.api.state.page, 0);
    assert.equal(context.api.state.ingredient, 'niacinamide');
    context.window.location.search = '';
    context.api.readUrl();
    assert.equal(context.api.state.ingredient, '');
    assert.equal(context.api.state.hairConcern, '');
});
test('late filter response cannot replace the latest results or busy state', async () => {
    const { context, get } = setup();
    const pending = [];
    context.fetch = (url, options) => new Promise(resolve => pending.push({ url, options, resolve }));
    const old = context.api.loadProducts();
    context.api.state.search = 'rose';
    const latest = context.api.loadProducts();
    assert.equal(pending[0].options.signal.aborted, true);
    pending[1].resolve({ ok: true, json: async () => ({ content: [], totalPages: 0, totalElements: 0 }) });
    await latest;
    pending[0].resolve({ ok: true, json: async () => ({ content: [], totalPages: 0, totalElements: 99 }) });
    await old;
    assert.equal(get('productCount').textContent, '0');
    assert.equal(get('productsContainer').busy, false);
});
test('all supported cosmetic facet filters reach the API', async () => {
    const { context } = setup('?ingredient=niacinamide&hairConcern=dry&finish=matte');
    context.api.readUrl();
    let request;
    context.fetch = async url => { request = new URL(url, 'https://store.example'); return { ok: true, json: async () => ({ content: [], totalPages: 0 }) }; };
    await context.api.loadProducts();
    assert.equal(request.searchParams.get('ingredient'), 'niacinamide');
    assert.equal(request.searchParams.get('hairConcern'), 'dry');
    assert.equal(request.searchParams.get('finish'), 'matte');
});
