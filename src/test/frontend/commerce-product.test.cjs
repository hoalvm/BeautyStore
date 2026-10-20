const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

function load(value = '1') {
    let source = fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/product-detail.js'), 'utf8');
    source = source.replace(/\}\)\(\);\s*$/, 'globalThis.testApi = {submitCart, submitReview, reviewAccessHeaders, variantLabel, toggleFavorite, updateGallery, setProduct(p, v) {product=p; selectedVariant=v;} };})();');
    const node = () => ({ children: [], style: {}, attributes: {}, className: '', append(...children) {this.children.push(...children);}, appendChild(child) {this.children.push(child);}, replaceChildren(...children) {this.children=children;}, setAttribute(k,v) {this.attributes[k]=v;}, addEventListener() {}, remove() {}, focus() {}, querySelectorAll() {return [];} });
    const nodes = {quantityInput: {...node(), value}, productThumbnails: node(), mainProductImage: node()};
    const context = { console, URL, URLSearchParams, setTimeout() {}, localStorage: {getItem() {return 'token';}},
        document: {addEventListener() {}, getElementById(id) {return nodes[id];}, createElement: node, createTextNode: value => value, body: node()},
        window: {location: {search: ''}}, sessionStorage: {getItem() {return null;}},
        BeautyUI: {text: value => String(value || ''), safeUrl: value => value, productImage: () => '/fallback'},
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

test('guest review uses only the token for the linked order, not a different last-opened order', () => {
    const {context} = load();
    context.localStorage.getItem = () => null;
    const tokens = new Map([['guestOrderToken', 'wrong-order'], ['beautystore:order-token:ORDER-A', 'correct-order']]);
    context.sessionStorage.getItem = key => tokens.get(key);
    assert.equal(context.testApi.reviewAccessHeaders()['X-Order-Token'], undefined);
    context.window.location.search = '?orderItemId=4&orderNumber=ORDER-A';
    assert.equal(context.testApi.reviewAccessHeaders()['X-Order-Token'], 'correct-order');
});

test('product review is single-flight and remains submitted after success', async () => {
    const {context, nodes, node} = load();
    for (const id of ['reviewStars', 'reviewTitle', 'reviewContent', 'reviewSkinType']) nodes[id] = {...node(), value: id === 'reviewStars' ? '5' : 'Good'};
    const button = node(); const form = {reportValidity: () => true, querySelector: () => button, reset() {}};
    let resolveUpload; context.BeautyUI.uploadEvidence = () => new Promise(resolve => {resolveUpload = resolve;});
    let requests = 0; context.fetch = async () => {requests++; return {ok: true};};
    const event = {preventDefault() {}, currentTarget: form};
    const first = context.testApi.submitReview(event, 4);
    await context.testApi.submitReview(event, 4);
    resolveUpload([]); await first;
    await context.testApi.submitReview(event, 4);
    assert.equal(requests, 1);
    assert.equal(button.disabled, true);
    assert.equal(button.textContent, 'Đã gửi, chờ duyệt');
});

test('gallery shows selected variant then common images, never another shade', () => {
    const {context, nodes} = load();
    context.testApi.setProduct({name: 'Lipstick', gallery: [{variantId: 2, url: '/other'}, {variantId: null, url: '/common'}, {variantId: 3, url: '/selected'}]}, {id: 3});
    context.testApi.updateGallery();
    assert.deepEqual(nodes.productThumbnails.children.map(button => button.children[0].src), ['/selected', '/common']);
});
