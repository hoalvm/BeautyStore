const test = require('node:test');
const assert = require('node:assert/strict');
const {load} = require('./admin-harness.cjs');

test('delivery photo validation handles missing, empty, HEIC and oversized files', () => {
    const {context} = load('shipper-dashboard.js');
    assert.match(context.deliveryPhotoError(null), /chọn ảnh/);
    assert.match(context.deliveryPhotoError({size: 0, type: 'image/png'}), /chọn ảnh/);
    assert.match(context.deliveryPhotoError({size: 123, type: 'image/heic'}), /HEIC/);
    assert.match(context.deliveryPhotoError({size: 5 * 1024 * 1024 + 1, type: 'image/jpeg'}), /5 MB/);
    for (const type of ['image/jpeg', 'image/png', 'image/webp']) {
        assert.equal(context.deliveryPhotoError({size: 5 * 1024 * 1024, type}), '');
    }
});

test('releasing a preview removes its source and revokes the object URL exactly once', () => {
    const revoked = [];
    const {context, document, run} = load('shipper-dashboard.js', {
        URL: {revokeObjectURL: value => revoked.push(value)}
    });
    run('deliveryProofState.previewUrl = "blob:local-proof"');
    document.getElementById('deliveryProofPreview').src = 'blob:local-proof';
    context.releaseDeliveryPreview();
    context.releaseDeliveryPreview();
    assert.deepEqual(revoked, ['blob:local-proof']);
    assert.equal(document.getElementById('deliveryProofPreview').src, undefined);
    assert.equal(document.getElementById('deliveryProofPreview').hidden, true);
});
