const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const read = file => fs.readFileSync(path.join(__dirname, '../../main/resources', file), 'utf8');
test('header search is a keyboard and no-JavaScript compatible form', () => {
    const html = read('templates/fragments/header.html');
    assert.match(html, /<form[^>]*role="search"[^>]*th:action="@\{\/products\}"[^>]*method="get"/);
    assert.match(html, /id="searchInput" name="search"/);
    assert.match(html, /type="submit" id="searchBtn"/);
    assert.match(html, /main\.tabIndex = -1/);
});
test('chat layout can shrink within short mobile viewports and respects hidden state', () => {
    const css = read('static/css/chatbot.css');
    assert.match(css, /max-height: calc\(100dvh - 104px\)/);
    assert.match(css, /\.chatbot-suggestions\[hidden\][\s\S]*display: none/);
    assert.match(css, /\.chatbot-input input\[type="text"\]\s*\{\s*min-width: 0/);
});
test('gallery selection styling follows the accessible button state', () => {
    assert.match(read('static/css/style.css'), /button\[aria-current="true"\] > \.product-gallery-thumb/);
});
