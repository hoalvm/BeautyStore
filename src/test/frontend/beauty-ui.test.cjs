const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const window = { location: { origin: 'https://store.example' } };
vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/beauty-ui.js'), 'utf8'), { window, URL });
const ui = window.BeautyUI;

test('image URLs allow web assets only, not same-origin blob URLs', () => {
    for (const value of ['javascript:alert(1)', 'data:image/svg+xml,evil', 'blob:https://store.example/asset']) {
        assert.equal(ui.safeUrl(value, '/fallback'), '/fallback');
    }
    assert.equal(ui.safeUrl('/image.webp'), 'https://store.example/image.webp');
});
test('variant gallery falls back to common images, never another shade', () => {
    const product = { gallery: [{ variantId: 2, primary: true, url: '/red.webp' }, { variantId: null, url: '/common.webp' }] };
    assert.equal(ui.productImage(product, { id: 1 }), 'https://store.example/common.webp');
    assert.equal(ui.productImage(product, { id: 2 }), 'https://store.example/red.webp');
});
test('zero sale price stays zero and missing sale does not mean free', () => {
    assert.equal(ui.currentPrice({}, { price: 50000, discountPrice: 0 }), 0);
    assert.equal(ui.currentPrice({}, { price: 50000 }), 50000);
    assert.equal(ui.currentPrice({}, { price: 'invalid' }), 0);
});
test('variant labels support canonical size fields and legacy aliases', () => {
    assert.equal(ui.variantLabel({ shadeName: 'Rose', sizeValue: 10, sizeUnit: 'ml' }), 'Rose · 10 ml');
    assert.equal(ui.variantLabel({ volume: 5, volumeUnit: 'g' }), '5 g');
    assert.equal(ui.brandName({ brand: {} }), 'BeautyStore');
});
test('archived catalog and exhausted variants cannot be quick-added', () => {
    const product = { variants: [{ id: 1, active: true, availableStock: 2 }] };
    assert.equal(ui.isAvailable(product), true);
    assert.equal(ui.isAvailable({ ...product, active: false }), false);
    assert.equal(ui.isAvailable({ ...product, categoryBreadcrumb: [{ active: false }] }), false);
    assert.equal(ui.isAvailable({ variants: [{ availableStock: 0 }] }), false);
});
test('business dates preserve Vietnam timezone independent of browser locale', () => {
    assert.equal(ui.parseBusinessDate('2026-10-19T10:30:00').toISOString(), '2026-10-19T03:30:00.000Z');
    assert.equal(ui.parseBusinessDate('2026-10-19T03:30:00Z').toISOString(), '2026-10-19T03:30:00.000Z');
    assert.equal(Number.isNaN(ui.parseBusinessDate(null).getTime()), true);
});
test('API error reader never displays serialized JSON or an HTML error page', async () => {
    assert.equal(await ui.errorMessage({ json: async () => ({ message: 'Hết hàng' }) }), 'Hết hàng');
    assert.equal(await ui.errorMessage({ json: async () => { throw new Error(); } }, 'Thử lại'), 'Thử lại');
});
