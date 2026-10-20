const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
function setup(file, exports) {
    const node = () => ({ children: [], style: {}, events: {}, append(...items) { this.children.push(...items); }, appendChild(item) { this.children.push(item); }, replaceChildren(...items) { this.children = items; }, setAttribute() {}, addEventListener(name, fn) { this.events[name] = fn; }, remove() {} });
    const nodes = new Map();
    const context = { URL, setTimeout() {}, window: { location: { origin: 'https://store.example' } }, localStorage: { getItem() { return 'token'; } },
        document: { addEventListener() {}, createElement: node, body: node(), getElementById(id) { if (!nodes.has(id)) nodes.set(id, node()); return nodes.get(id); } } };
    vm.createContext(context);
    const dir = path.join(__dirname, '../../main/resources/static/js');
    vm.runInContext(fs.readFileSync(path.join(dir, 'beauty-ui.js'), 'utf8'), context);
    context.BeautyUI = context.window.BeautyUI;
    vm.runInContext(fs.readFileSync(path.join(dir, file), 'utf8').replace(/\}\)\(\);\s*$/, `globalThis.api = {${exports}};})();`), context);
    return { context, node, nodes };
}
const product = { id: 1, active: true, variants: [{ id: 10, price: 50000, availableStock: 5, defaultVariant: true }] };
test('favorite card disables quick-add for archived products with legacy stock', () => {
    const { context } = setup('favorites.js', 'createFavoriteCard');
    const card = context.api.createFavoriteCard({ product: { ...product, active: false, inStock: true } });
    const add = card.children[0].children[1].children.at(-1).children[0];
    assert.equal(add.disabled, true);
    assert.equal(add.textContent, 'Không khả dụng');
});
test('favorite quick-add is single-flight and re-enables after failure', async () => {
    const { context, node } = setup('favorites.js', 'addToCart');
    let resolve, requests = 0;
    context.fetch = () => { requests++; return new Promise(r => { resolve = r; }); };
    const button = node();
    const first = context.api.addToCart(product, product.variants[0], button);
    await context.api.addToCart(product, product.variants[0], button);
    resolve({ ok: false, json: async () => ({ message: 'Hết hàng' }) });
    await first;
    assert.equal(requests, 1);
    assert.equal(button.disabled, false);
});
test('home cards show the whole price range instead of only the default variant', () => {
    const { context } = setup('home.js', 'createProductColumn');
    const card = context.api.createProductColumn({ ...product, variants: [...product.variants, { id: 11, price: 90000, availableStock: 1 }] }, 'col');
    const body = card.children[0].children.at(-1);
    const price = body.children[3].children[0].textContent;
    assert.match(price, /50\.000.*90\.000/);
});
