const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const template = name => fs.readFileSync(path.join(__dirname, '../../main/resources/templates', name + '.html'), 'utf8');

test('OTP pages allow direct entry and mobile one-time-code autofill', () => {
    for (const name of ['reset-password', 'verify-otp']) {
        const html = template(name);
        assert.doesNotMatch(html, /readonly/);
        assert.match(html, /inputmode="numeric" pattern="\[0-9\]\{6\}" autocomplete="one-time-code"/);
        assert.match(html, /aria-live="polite"/);
        assert.match(html, /5 phút/);
    }
});

test('password recovery offers resend without leaking account existence', () => {
    assert.match(template('reset-password'), /id="resendResetOtpButton" type="button"/);
    assert.match(template('forgot-password'), /Nếu email tồn tại/);
    for (const name of ['forgot-password', 'reset-password', 'verify-otp']) {
        assert.doesNotMatch(template(name), /tín hiệu|cứu hộ|fa-satellite/);
    }
});
