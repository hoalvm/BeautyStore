const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

function load(fetch) {
    const window = {};
    vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../../main/resources/static/js/beauty-ui.js'), 'utf8'),
        {window, fetch, FormData: class {append() {}}, URL});
    return window.BeautyUI;
}

test('evidence rejects invalid selection before uploading any earlier valid file', async () => {
    let requests = 0;
    const ui = load(async () => {requests++;});
    for (const bad of [{type: 'image/svg+xml', size: 12}, {type: 'image/png', size: 0}, {type: 'image/png', size: 10485761}]) {
        await assert.rejects(ui.uploadEvidence([{type: 'image/jpeg', size: 20}, bad], 'review', 1, {}));
    }
    assert.equal(requests, 0);
});

test('evidence retries retain successful uploads without exhausting pending quota', async () => {
    let requests = 0;
    const ui = load(async () => {
        requests++;
        return requests === 2 ? {ok: false, json: async () => ({message: 'Try again'})}
            : {ok: true, json: async () => ({url: `https://image.test/${requests}`})};
    });
    const files = [{type: 'image/jpeg', size: 20}, {type: 'image/png', size: 20}];
    const cache = new Map();
    await assert.rejects(ui.uploadEvidence(files, 'review', 7, {}, cache), /Try again/);
    const urls = await ui.uploadEvidence(files, 'review', 7, {}, cache);
    assert.deepEqual(Array.from(urls), ['https://image.test/1', 'https://image.test/3']);
    assert.equal(requests, 3);
    await ui.uploadEvidence(files, 'return', 7, {}, cache);
    assert.equal(requests, 5, 'cached uploads never cross evidence kind or order item');
});
