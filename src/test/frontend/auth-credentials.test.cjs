const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const template = name => fs.readFileSync(path.join(__dirname, '../../main/resources/templates', name + '.html'), 'utf8');

test('login supports password managers and live errors', () => {
    const html = template('login');
    assert.match(html, /id="loginEmail"[^>]*autocomplete="username"/);
    assert.match(html, /id="loginPassword"[^>]*autocomplete="current-password"/);
    assert.match(html, /id="loginMessage"[^>]*aria-live="polite"/);
    assert.match(html, /<h1 /);
});

test('registration documents and bounds the current password policy', () => {
    const html = template('register');
    assert.equal((html.match(/autocomplete="new-password"/g) || []).length, 2);
    assert.equal((html.match(/minlength="8" maxlength="128"/g) || []).length, 2);
    assert.match(html, /id="registerPasswordHelp"/);
    assert.match(html, /id="registerMessage"[^>]*aria-live="polite"/);
});
