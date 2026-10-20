const test = require('node:test');
const assert = require('node:assert/strict');
const {load} = require('./admin-harness.cjs');

test('review refresh handler is wired and duplicate moderation suppressed', async () => {
    let calls = 0;
    const {context} = load('admin-reviews.js', {fetch: () => { calls++; return new Promise(() => {}); }});
    assert.equal(typeof context.refreshReviews, 'function');
    context.moderateReview(1, 'APPROVED');
    await context.moderateReview(1, 'REJECTED');
    assert.equal(calls, 1);
});

test('review pagination stays bounded for large catalogs', () => {
    const {context, document, run} = load('admin-reviews.js');
    run('currentPage = 500');
    context.updatePagination({totalElements: 1000000, totalPages: 100000});
    assert.equal(document.getElementById('paginationControls').children.length, 5);
    assert.match(document.getElementById('paginationInfo').textContent, /bộ lọc trên trang/);
});

test('return refund and restock mutations cannot overlap', async () => {
    let calls = 0;
    const {context} = load('admin-returns.js', {fetch: () => { calls++; return new Promise(() => {}); }});
    context.mutate('/api/admin/returns/1/status', 'PATCH', {status: 'REFUNDED'});
    await context.mutate('/api/admin/returns/1/restock', 'POST', {returnItemIds: [1]});
    assert.equal(calls, 1);
});
