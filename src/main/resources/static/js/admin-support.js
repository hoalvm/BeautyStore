let currentSessionId = null;
let currentUserName = '';
let currentUserEmail = '';
let messagesLoading = false;

document.addEventListener('DOMContentLoaded', () => {
    if (!localStorage.getItem('authToken')) {
        window.location.href = '/login';
        return;
    }
    loadSessions();
    window.setInterval(loadSessions, 15000);
    window.setInterval(() => {
        if (currentSessionId) loadMessages(currentSessionId, false);
    }, 3000);
});

function getAuthHeaders() {
    return {
        Authorization: `Bearer ${localStorage.getItem('authToken') || ''}`,
        'Content-Type': 'application/json'
    };
}

async function apiRequest(url, options = {}) {
    const response = await fetch(url, {
        ...options,
        headers: {...getAuthHeaders(), ...(options.headers || {})}
    });
    if (response.status === 401) {
        window.location.href = '/login';
        throw new Error('Phiên đăng nhập đã hết hạn');
    }
    if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        throw new Error(body.message || `Yêu cầu thất bại (${response.status})`);
    }
    if (response.status === 204) return null;
    return response.json();
}

async function loadSessions() {
    try {
        const sessions = await apiRequest('/api/support/admin/sessions');
        displaySessions(sessions);
    } catch (error) {
        renderAdminSupportPanel('danger', 'fa-exclamation-triangle',
            'Không thể tải danh sách', error.message, 'Thử lại', 'btn-outline-danger', loadSessions);
    }
}

function refreshSessions() {
    loadSessions();
    if (currentSessionId) loadMessages(currentSessionId);
}

function displaySessions(sessions) {
    const list = document.getElementById('sessionsList');
    if (!list) return;
    list.replaceChildren();
    if (!Array.isArray(sessions) || sessions.length === 0) {
        const empty = document.createElement('div');
        empty.className = 'text-center py-5 text-muted';
        const icon = document.createElement('i');
        icon.className = 'fas fa-inbox fa-3x mb-3';
        const message = document.createElement('p');
        message.textContent = 'Chưa có khách hàng nào cần hỗ trợ';
        empty.append(icon, message);
        list.appendChild(empty);
        return;
    }

    const fragment = document.createDocumentFragment();
    sessions.forEach(session => {
        const nameText = cleanText(session.userName, 'Khách hàng');
        const emailText = cleanText(session.userEmail, 'Chưa cập nhật email');
        const item = document.createElement('button');
        item.type = 'button';
        item.className = 'session-item border-0 w-100 text-start bg-white';
        if (String(session.sessionId) === String(currentSessionId)) item.classList.add('active');
        item.dataset.sessionId = String(session.sessionId || '');
        item.addEventListener('click', () => selectSession(session.sessionId, nameText, emailText));

        const layout = document.createElement('div');
        layout.className = 'd-flex justify-content-between align-items-start';
        const content = document.createElement('div');
        content.className = 'flex-grow-1 overflow-hidden';
        const name = document.createElement('div');
        name.className = 'session-name';
        name.textContent = nameText;
        const email = document.createElement('div');
        email.className = 'session-email';
        email.textContent = emailText;
        const preview = document.createElement('div');
        preview.className = 'session-last-message';
        preview.textContent = cleanText(session.lastMessage, 'Chưa có tin nhắn');
        content.append(name, email, preview);
        layout.appendChild(content);
        const unreadCount = Number(session.unreadCount) || 0;
        if (unreadCount > 0) {
            const unread = document.createElement('span');
            unread.className = 'badge bg-danger ms-2';
            unread.textContent = String(unreadCount);
            layout.appendChild(unread);
        }
        item.appendChild(layout);
        fragment.appendChild(item);
    });
    list.appendChild(fragment);
}

function cleanText(value, fallback = '') {
    const valueText = value == null ? '' : String(value).trim();
    return valueText && !['null', 'undefined'].includes(valueText.toLowerCase()) ? valueText : fallback;
}

function renderAdminSupportPanel(tone, iconClass, title, detail, buttonLabel, buttonClass, action) {
    const list = document.getElementById('sessionsList');
    if (!list) return;
    const panel = document.createElement('div');
    panel.className = `alert alert-${tone} m-3`;
    const icon = document.createElement('i');
    icon.className = `fas ${iconClass}`;
    const heading = document.createElement('strong');
    heading.textContent = title;
    const description = document.createElement('small');
    description.textContent = String(detail || '');
    const button = document.createElement('button');
    button.type = 'button';
    button.className = `btn btn-sm ${buttonClass} mt-2`;
    button.textContent = buttonLabel;
    button.addEventListener('click', action);
    panel.append(icon, document.createTextNode(' '), heading, document.createElement('br'),
        description, document.createElement('br'), button);
    list.replaceChildren(panel);
}

function selectSession(sessionId, userName, userEmail) {
    currentSessionId = String(sessionId);
    currentUserName = cleanText(userName, 'Khách hàng');
    currentUserEmail = cleanText(userEmail);
    const name = document.getElementById('currentUserName');
    const email = document.getElementById('currentUserEmail');
    const inputArea = document.getElementById('messageInputArea');
    if (name) name.textContent = currentUserName;
    if (email) {
        email.textContent = currentUserEmail;
        email.hidden = !currentUserEmail;
    }
    if (inputArea) inputArea.style.display = 'block';
    document.querySelectorAll('.session-item').forEach(item => {
        item.classList.toggle('active', item.dataset.sessionId === currentSessionId);
    });
    loadMessages(currentSessionId);
    markAsRead(currentSessionId);
}

async function loadMessages(sessionId, showError = true) {
    if (messagesLoading || String(sessionId) !== String(currentSessionId)) return;
    messagesLoading = true;
    try {
        const messages = await apiRequest(`/api/support/session/${encodeURIComponent(sessionId)}/messages`);
        if (String(sessionId) !== String(currentSessionId)) return;
        const area = document.getElementById('messagesArea');
        if (!area) return;
        const wasNearBottom = area.scrollHeight - area.scrollTop - area.clientHeight < 80;
        area.replaceChildren();
        const messageList = Array.isArray(messages) ? messages : [];
        messageList.forEach(message => area.appendChild(createMessage(message)));
        if (messageList.length === 0) {
            const empty = document.createElement('p');
            empty.className = 'text-center text-muted py-5';
            empty.textContent = 'Chưa có tin nhắn trong hội thoại này.';
            area.appendChild(empty);
        }
        if (wasNearBottom) scrollToBottom();
        markAsRead(sessionId);
    } catch (error) {
        if (showError) window.alert(error.message);
    } finally {
        messagesLoading = false;
    }
}

function createMessage(message) {
    const senderType = message.senderType === 'ADMIN' ? 'ADMIN' : 'USER';
    const wrapper = document.createElement('div');
    wrapper.className = `message ${senderType.toLowerCase()}`;
    const bubble = document.createElement('div');
    bubble.className = 'message-bubble';
    const sender = document.createElement('div');
    sender.className = 'message-sender';
    sender.textContent = senderType === 'ADMIN' ? 'Bạn (Admin)' : cleanText(message.userName, currentUserName);
    const messageText = document.createElement('div');
    messageText.className = 'message-text';
    messageText.textContent = String(message.message || '');
    const time = document.createElement('div');
    time.className = 'message-time';
    time.textContent = formatTime(message.createdAt);
    bubble.append(sender, messageText, time);
    wrapper.appendChild(bubble);
    return wrapper;
}

function formatTime(value) {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? '' : date.toLocaleTimeString('vi-VN', {hour: '2-digit', minute: '2-digit'});
}

async function sendMessage(event) {
    event.preventDefault();
    const input = document.getElementById('messageInput');
    const message = input ? input.value.trim() : '';
    if (!message || !currentSessionId) return;
    input.disabled = true;
    try {
        await apiRequest(`/api/support/admin/session/${encodeURIComponent(currentSessionId)}/messages`, {
            method: 'POST',
            body: JSON.stringify({message})
        });
        input.value = '';
        await loadMessages(currentSessionId);
        loadSessions();
    } catch (error) {
        window.alert(error.message);
    } finally {
        input.disabled = false;
        input.focus();
    }
}

async function markAsRead(sessionId) {
    try {
        await apiRequest(`/api/support/admin/session/${encodeURIComponent(sessionId)}/read`, {method: 'POST'});
    } catch (_) {
        // Polling retries automatically; avoid logging conversation identifiers.
    }
}

function scrollToBottom() {
    const area = document.getElementById('messagesArea');
    if (area) area.scrollTop = area.scrollHeight;
}
