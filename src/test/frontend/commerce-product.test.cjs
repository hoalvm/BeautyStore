const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

function load(value = '1') {
    let source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/product-detail.js'), 'utf8');
    source = source.replace(/\}\)\(\);\s*$/, 'globalThis.testApi = {submitCart, toggleFavorite, updateGallery, setProduct(p, v) {product=p; selectedVariant=v;} };})();');
    const node = () => ({ children: [], style: {}, attributes: {}, className: '', append(...children) {this.children.push(...children);}, appendChild(child) {this.children.push(child);}, replaceChildren(...children) {this.children=children;}, setAttribute(k,v) {this.attributes[k]=v;}, addEventListener() {}, remove() {}, focus() {}, querySelectorAll() {return [];} });
    const nodes = {quantityInput: {...node(), value}, productThumbnails: node(), mainProductImage: node()};
    const context = { console, URL, URLSearchParams, setTimeout() {}, localStorage: {getItem() {return 'token';}},
        document: {addEventListener() {}, getElementById(id) {return nodes[id];}, createElement: node, createTextNode: value => value, body: node()},
        window: {}, BeautyUI: {text: value => String(value || ''), safeUrl: value => value, productImage: () => '/fallback'},
        fetch: async () => {throw new Error('Unexpected fetch');} };
    vm.createContext(context); vm.runInContext(source, context);
    context.testApi.setProduct({id: 2, name: 'Cream'}, {id: 3, availableStock: 4});
    return {context, nodes, node};
}

for (const value of ['1.5', '', '5', '-2', 'NaN']) {
    test(`product rejects invalid quantity ${JSON.stringify(value)} before API`, async () => {
        const {context} = load(value);
        let requests = 0;
        context.fetch = async () => {requests++;};
        await context.testApi.submitCart(false);
        assert.equal(requests, 0);
    });
}

test('favorite button survives asynchronous event cleanup and reflects server removal', async () => {
    const {context, node} = load(); const button = node();
    let resolve; context.fetch = () => new Promise(r => {resolve=r;});
    const event = {currentTarget: button};
    const request = context.testApi.toggleFavorite(event);
    event.currentTarget = null;
    resolve({ok: true, json: async () => ({favorite: false})});
    await request;
    assert.equal(button.textContent, '♡');
    assert.equal(button.attributes['aria-pressed'], 'false');
    assert.equal(button.disabled, false);
});

test('gallery shows selected variant then common images, never another shade', () => {
    const {context, nodes} = load();
    context.testApi.setProduct({name: 'Lipstick', gallery: [{variantId: 2, url: '/other'}, {variantId: null, url: '/common'}, {variantId: 3, url: '/selected'}]}, {id: 3});
    context.testApi.updateGallery();
    assert.deepEqual(nodes.productThumbnails.children.map(button => button.children[0].src), ['/selected', '/common']);
});
