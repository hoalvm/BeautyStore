const test = require('node:test');
const assert = require('node:assert/strict');
const {load} = require('./admin-harness.cjs');

test('metadata editing leaves taxonomy untouched', () => {
    const {context} = load('admin-products.js');
    assert.equal(Object.hasOwn(context.productPayload(), 'facetIds'), false);
});

test('saving nondefault hidden variant preserves visibility and default flags', async () => {
    let sent;
    const {context, run} = load('admin-products.js', {fetch: async (url, options) => {
        sent = JSON.parse(options.body); return {ok: true};
    }});
    run('editingVariant = {id: 9, active: false, defaultVariant: false}');
    const payload = context.variantPayload();
    await context.saveExistingVariant(9, payload);
    assert.equal(sent.active, false);
    assert.equal(sent.defaultVariant, false);
});

test('inventory warning requests obey server page-size limit', async () => {
    let requested;
    const {context} = load('admin-products.js', {fetch: async url => {
        requested = url; return {ok: true, json: async () => ({content: [], totalElements: 0})};
    }});
    await context.loadInventoryAlerts('low');
    assert.equal(new URL(requested, 'https://beauty.test').searchParams.get('size'), '100');
});

test('stock filtering within a search page never hides later server pages', () => {
    const {context, run} = load('admin-products.js');
    run("currentStockFilter = 'out-of-stock'");
    const page = context.filterStockPage({content: [{totalAvailableStock: 12}], totalElements: 85, totalPages: 5});
    assert.equal(page.content.length, 0);
    assert.equal(page.totalPages, 5);
    assert.equal(page.totalElements, 85);
});

test('older catalog responses never overwrite a newer search or pagination', async () => {
    const pending = [];
    const {context, run} = load('admin-products.js', {fetch: () => new Promise(resolve => pending.push(resolve))});
    const renders = [];
    context.showTableMessage = () => {};
    context.renderProducts = products => renders.push(products[0]?.id);
    context.updatePagination = () => {};
    const first = context.loadProducts();
    run("currentSearch = 'new'");
    const second = context.loadProducts();
    pending[1]({ok: true, json: async () => ({content: [{id: 2}], totalElements: 1, totalPages: 1})});
    await second;
    pending[0]({ok: true, json: async () => ({content: [{id: 1}], totalElements: 1, totalPages: 1})});
    await first;
    assert.deepEqual(renders, [2]);
});

test('product editor ignores an older product response after selecting another product', async () => {
    const pending = [];
    const {context, run} = load('admin-products.js', {
        fetch: () => new Promise(resolve => pending.push(resolve)),
        bootstrap: {Modal: {getOrCreateInstance: () => ({show() {}})}}
    });
    const edited = [];
    context.fillProductForm = product => edited.push(product.id);
    context.loadVariants = async () => {};
    context.loadGallery = async () => {};
    const first = context.editProduct(1);
    const second = context.editProduct(2);
    pending[1]({ok: true, json: async () => ({id: 2})}); await second;
    pending[0]({ok: true, json: async () => ({id: 1})}); await first;
    assert.deepEqual(edited, [2]);
    assert.equal(run('editingProduct.id'), 2);
});

test('catalog table displays a valid zero sale price instead of original price', () => {
    const {context} = load('admin-products.js');
    const row = context.createProductRow({name: 'Demo', variants: [{price: 50000, discountPrice: 0}]});
    const price = row.children[4];
    assert.equal(price.children[0].textContent, context.formatPrice(0));
    assert.equal(price.children[1].textContent, context.formatPrice(50000));
});

test('voucher scope includes archived catalog entries', async () => {
    const requests = [];
    const {context} = load('admin-voucher-form.js', {fetch: async url => {
        requests.push(url); return {ok: true, json: async () => ({content: [], totalPages: 1})};
    }});
    await context.loadAllProducts();
    assert.match(requests[0], /includeInactive=true/);
});

test('failed voucher initialization cannot submit a destructive empty scope', async () => {
    let calls = 0;
    const {context} = load('admin-voucher-form.js', {fetch: async () => { calls++; }});
    await context.handleSubmit({preventDefault() {}});
    assert.equal(calls, 0);
});

test('voucher toggles do not race and undo each other on double click', () => {
    let calls = 0;
    const {context} = load('admin-vouchers.js', {fetch: () => { calls++; return new Promise(() => {}); }});
    context.toggleStatus(5);
    context.toggleStatus(5);
    assert.equal(calls, 1);
});
