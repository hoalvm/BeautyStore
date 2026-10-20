const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const resources = path.resolve(__dirname, '../../main/resources');
function files(dir) {
    return fs.readdirSync(dir, {withFileTypes: true}).flatMap(entry => {
        const full = path.join(dir, entry.name);
        return entry.isDirectory() ? files(full) : [full];
    });
}

test('all runtime stylesheet and script references are available locally', () => {
    for (const file of files(path.join(resources, 'templates')).filter(name => name.endsWith('.html'))) {
        const source = fs.readFileSync(file, 'utf8');
        assert.doesNotMatch(source, /<(?:script|link)\b[^>]*(?:src|href)=["']https?:\/\//i, file);
        for (const match of source.matchAll(/(?:src|href)="(\/(?:js|css|vendor|fonts)\/[^"?]+)"/g)) {
            assert.ok(fs.existsSync(path.join(resources, 'static', match[1])), `${file}: ${match[1]}`);
        }
    }
    for (const file of files(path.join(resources, 'static/vendor')).filter(name => name.endsWith('.css'))) {
        const source = fs.readFileSync(file, 'utf8');
        for (const match of source.matchAll(/url\(["']?([^"')]+)["']?\)/g)) {
            if (/^(?:data:|https?:)/.test(match[1])) continue;
            assert.ok(fs.existsSync(path.resolve(path.dirname(file), match[1].split(/[?#]/)[0])), `${file}: ${match[1]}`);
        }
    }
});

test('checkout offers real VNPay only when configured and defaults to COD', () => {
    const checkout = fs.readFileSync(path.join(resources, 'templates/checkout.html'), 'utf8');
    assert.match(checkout, /th:if="\$\{vnpayAvailable\}"[^>]*>\s*<input[^>]*value="VNPAY"/);
    assert.match(checkout, /name="paymentMethod"[^>]*value="COD" checked/);
});
