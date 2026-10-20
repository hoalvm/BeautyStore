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

test('return modal closes after a fast mutation during its opening animation', () => {
    const {context, document, run} = load('admin-returns.js');
    const events = new Map();
    const modal = document.getElementById('returnModal');
    modal.addEventListener = (event, callback) => events.set(event, callback);
    modal.removeEventListener = event => events.delete(event);
    context.fakeModal = {opening: true, visible: true, hide() {if (!this.opening) this.visible = false;}};
    run('returnModal = fakeModal');
    context.hideReturnModal();
    assert.equal(context.fakeModal.visible, true);
    context.fakeModal.opening = false;
    events.get('shown.bs.modal')();
    assert.equal(context.fakeModal.visible, false);
    events.get('hidden.bs.modal')();
    assert.equal(events.has('shown.bs.modal'), false, 'no stale listener closes a later reopened modal');
});
