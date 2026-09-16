let supportSessionId = null;
let supportPollTimer = null;
let supportLoading = false;

document.addEventListener('DOMContentLoaded', () => {
    document.getElementById('openSupportChat')?.addEventListener('click', openSupportChat);
    document.getElementById('supportChatForm')?.addEventListener('submit', sendSupportMessage);
    document.getElementById('supportChatModal')?.addEventListener('hidden.bs.modal', () => {
        window.clearInterval(supportPollTimer);
        supportPollTimer = null;
    });
});

function supportHeaders() {
    const headers = {'Content-Type': 'application/json'};
    const token = localStorage.getItem('authToken');
    if (token) headers.Authorization = `Bearer ${token}`;
    return headers;
}

async function supportRequest(url, options = {}) {
    const response = await fetch(url, {
        ...options,
        credentials: 'same-origin',
        headers: {...supportHeaders(), ...(options.headers || {})}
    });
    if (response.status === 401) {
        throw new Error('Vui lòng đăng nhập lại để tiếp tục hỗ trợ.');
    }
    if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        throw new Error(body.message || `Yêu cầu thất bại (${response.status})`);
    }
    if (response.status === 204) return null;
    return response.json();
}

function openSupportChat() {
    const modalElement = document.getElementById('supportChatModal');
    if (!modalElement || typeof bootstrap === 'undefined') return;
    bootstrap.Modal.getOrCreateInstance(modalElement).show();
    initializeSupportSession();
}

async function initializeSupportSession() {
    renderCustomerSupportPanel('light', 'fa-circle-notch fa-spin', 'Đang kết nối',
        'BeautyStore đang mở phiên hỗ trợ an toàn cho bạn.');
    try {
        const session = await supportRequest('/api/support/session', {
            method: 'POST',
            body: JSON.stringify({})
        });
        supportSessionId = session.sessionId;
        await loadSupportMessages(true);
        window.clearInterval(supportPollTimer);
        supportPollTimer = window.setInterval(() => loadSupportMessages(false), 3000);
    } catch (error) {
        const authenticated = Boolean(localStorage.getItem('authToken'));
        renderCustomerSupportPanel('danger', 'fa-exclamation-triangle', 'Không thể kết nối',
            error.message, authenticated ? 'Đăng nhập lại' : 'Thử lại',
            authenticated ? () => { window.location.href = '/login'; } : initializeSupportSession);
    }
}

async function loadSupportMessages(showError = false) {
    if (!supportSessionId || supportLoading) return;
    supportLoading = true;
    try {
        const messages = await supportRequest(
            `/api/support/session/${encodeURIComponent(supportSessionId)}/messages`);
        const area = document.getElementById('supportMessages');
        if (!area) return;
        const wasNearBottom = area.scrollHeight - area.scrollTop - area.clientHeight < 80;
        area.replaceChildren();
        const messageList = Array.isArray(messages) ? messages : [];
        if (messageList.length === 0) {
            renderCustomerSupportEmptyState();
        } else {
            messageList.forEach(message => area.appendChild(createCustomerMessage(message)));
        }
        if (wasNearBottom) scrollSupportToBottom();
        markSupportAsRead();
    } catch (error) {
        if (showError) {
            renderCustomerSupportPanel('danger', 'fa-exclamation-triangle',
                'Không thể tải hội thoại', error.message);
        }
    } finally {
        supportLoading = false;
    }
}

function createCustomerMessage(message) {
    const senderType = message.senderType === 'USER' ? 'USER' : 'ADMIN';
    const wrapper = document.createElement('div');
    wrapper.className = `message-customer ${senderType.toLowerCase()}`;
    const bubble = document.createElement('div');
    bubble.className = 'message-bubble-customer';
    const sender = document.createElement('div');
    sender.className = 'message-sender-customer';
    sender.textContent = senderType === 'USER' ? 'Bạn' : 'Chuyên viên BeautyStore';
    const messageText = document.createElement('div');
    messageText.className = 'message-text-customer';
    messageText.textContent = String(message.message || '');
    const time = document.createElement('div');
    time.className = 'message-time-customer';
    time.textContent = formatSupportTime(message.createdAt);
    bubble.append(sender, messageText, time);
    wrapper.appendChild(bubble);
    return wrapper;
}

function formatSupportTime(value) {
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? '' : date.toLocaleTimeString('vi-VN', {
        hour: '2-digit',
        minute: '2-digit'
    });
}

async function sendSupportMessage(event) {
    event.preventDefault();
    const input = document.getElementById('supportMessageInput');
    const message = input ? input.value.trim() : '';
    if (!message || !supportSessionId) return;
    input.disabled = true;
    try {
        await supportRequest(`/api/support/session/${encodeURIComponent(supportSessionId)}/messages`, {
            method: 'POST',
            body: JSON.stringify({message})
        });
        input.value = '';
        await loadSupportMessages(true);
    } catch (error) {
        window.alert(error.message);
    } finally {
        input.disabled = false;
        input.focus();
    }
}

async function markSupportAsRead() {
    if (!supportSessionId) return;
    try {
        await supportRequest(`/api/support/session/${encodeURIComponent(supportSessionId)}/read`, {
            method: 'POST'
        });
    } catch (_) {
        // The next poll retries without logging message or identity data.
    }
}

function scrollSupportToBottom() {
    const area = document.getElementById('supportMessages');
    if (area) area.scrollTop = area.scrollHeight;
}

function renderCustomerSupportPanel(tone, iconClass, title, detail, buttonLabel = null, action = null) {
    const area = document.getElementById('supportMessages');
    if (!area) return;
    const panel = document.createElement('div');
    panel.className = `alert alert-${tone} m-3`;
    const icon = document.createElement('i');
    icon.className = `fas ${iconClass}`;
    const heading = document.createElement('strong');
    heading.textContent = title;
    const description = document.createElement('small');
    description.textContent = String(detail || '');
    panel.append(icon, document.createTextNode(' '), heading, document.createElement('br'), description);
    if (buttonLabel && action) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'btn btn-sm btn-primary mt-2';
        button.textContent = buttonLabel;
        button.addEventListener('click', action);
        panel.append(document.createElement('br'), button);
    }
    area.replaceChildren(panel);
}

function renderCustomerSupportEmptyState() {
    const area = document.getElementById('supportMessages');
    if (!area) return;
    const empty = document.createElement('div');
    empty.className = 'text-center py-5 text-muted';
    const icon = document.createElement('i');
    icon.className = 'fas fa-comments fa-3x mb-3';
    const title = document.createElement('p');
    title.textContent = 'Bạn đang trò chuyện với đội ngũ BeautyStore';
    const detail = document.createElement('small');
    detail.textContent = 'Hãy gửi câu hỏi, chuyên viên sẽ phản hồi sớm nhất có thể.';
    empty.append(icon, title, detail);
    area.replaceChildren(empty);
}
