(() => {
    'use strict';
    const button = document.getElementById('refreshMail');
    const list = document.getElementById('mailList');
    const status = document.getElementById('mailStatus');
    const preview = document.getElementById('mailPreview');
    let selectionVersion = 0;
    // srcdoc is not an HTTP navigation: it retains the iframe sandbox without
    // weakening the application's X-Frame-Options: DENY policy. Restore the
    // restrictive mail CSP inside the document because response headers no
    // longer govern content fetched as text.
    const previewPolicy = '<meta http-equiv="Content-Security-Policy" content="default-src \'none\'; style-src \'unsafe-inline\'; img-src \'self\' data:;">';
    async function openMail(id) {
        const selection = ++selectionVersion;
        preview.removeAttribute('src');
        preview.srcdoc = '';
        status.textContent = 'Đang mở thư…';
        try {
            const response = await fetch(`/demo/mail/${encodeURIComponent(id)}`, { cache: 'no-store' });
            if (!response.ok) throw new Error('Thư đã hết hạn hoặc không thể mở. Hãy làm mới hộp thư.');
            const html = await response.text();
            if (selection !== selectionVersion) return;
            preview.srcdoc = previewPolicy + html;
            status.textContent = 'Đã mở thư demo. OTP có hiệu lực trong 5 phút kể từ khi gửi.';
        } catch (error) {
            if (selection !== selectionVersion) return;
            status.textContent = error.message || 'Không thể mở thư. Hãy thử lại.';
        }
    }
    async function refresh() {
        if (button.disabled) return;
        button.disabled = true;
        status.textContent = 'Đang tải thư…';
        try {
            const response = await fetch('/demo/api/mail', { cache: 'no-store' });
            if (!response.ok) throw new Error('Không thể tải hộp thư.');
            const messages = await response.json();
            list.replaceChildren();
            for (const message of messages) {
                const item = document.createElement('li');
                item.className = 'list-group-item';
                const open = document.createElement('button');
                open.type = 'button';
                open.className = 'btn btn-link text-start';
                open.textContent = `${message.subject} — ${message.recipient}`;
                open.addEventListener('click', () => openMail(message.id));
                item.append(open);
                list.append(item);
            }
            status.textContent = messages.length ? `${messages.length} thư. Chọn thư để đọc OTP.` : 'Chưa có thư mới.';
        } catch (error) {
            status.textContent = error.message || 'Không thể tải hộp thư. Hãy thử lại.';
        } finally {
            button.disabled = false;
        }
    }
    button.addEventListener('click', refresh);
    refresh();
})();
