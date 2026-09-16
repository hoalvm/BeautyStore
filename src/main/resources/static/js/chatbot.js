(function(){
    // BeautyStore cosmetics assistant
    const rootId = 'chatbot-root';
    const root = document.getElementById(rootId);
    if (!root) return;
    const configuredHotline = String(root.dataset.hotline || '').trim();

    // Templates
    const buttonHtml = `
        <button type="button" class="chatbot-button" id="chatbot-toggle" aria-label="Mở trợ lý BeautyStore" aria-expanded="false" aria-controls="chatbot-bubble">
            <svg width="30" height="30" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M12 2C7 2 3.5 5.2 3.5 9.3C3.5 11.6 4.6 13.7 6.5 15.1V20L10.7 17.9C11.7 18 12.8 18 14 18C19 18 22.5 14.8 22.5 10.7C22.5 6.6 19 2 12 2Z" fill="white"/>
            </svg>
        </button>
    `;

    const bubbleHtml = `
        <div class="chatbot-bubble" id="chatbot-bubble" role="dialog" aria-label="Chat hỗ trợ khách hàng" aria-hidden="true">
            <div class="chatbot-header">
                <div class="avatar" aria-hidden="true">✿</div>
                <div>
                    <div class="title">BeautyStore AI</div>
                    <div class="subtitle">Gợi ý mỹ phẩm theo nhu cầu</div>
                </div>
                <button type="button" class="btn-close btn-close-white ms-auto" id="chatbot-close" aria-label="Đóng trợ lý"></button>
            </div>
            <div class="chatbot-messages" id="chatbot-messages" aria-live="polite"></div>
            <div class="suggestions-toggle collapsed" id="suggestions-toggle">
                <span>Gợi ý nhanh</span>
                <span class="arrow">▼</span>
            </div>
            <div class="chatbot-suggestions collapsed" id="chatbot-suggestions"></div>
            <button type="button" class="chatbot-human-support" id="chatbot-human-support">
                <span aria-hidden="true">●</span> Chuyển sang chuyên viên
            </button>
            <div class="chatbot-input">
                <input type="text" id="chatbot-input" placeholder="Gõ câu hỏi của bạn..." aria-label="Nhập câu hỏi">
                <button class="send" id="chatbot-send">Gửi</button>
            </div>
        </div>
    `;

    root.innerHTML = buttonHtml + bubbleHtml;

    // Elements
    const toggle = document.getElementById('chatbot-toggle');
    const bubble = document.getElementById('chatbot-bubble');
    const messagesEl = document.getElementById('chatbot-messages');
    const suggestionsEl = document.getElementById('chatbot-suggestions');
    const suggestionsToggle = document.getElementById('suggestions-toggle');
    const inputEl = document.getElementById('chatbot-input');
    const sendBtn = document.getElementById('chatbot-send');
    const humanSupportBtn = document.getElementById('chatbot-human-support');
    const headerTitle = bubble.querySelector('.chatbot-header .title');
    const headerSubtitle = bubble.querySelector('.chatbot-header .subtitle');

    // Quick suggestions for cosmetic discovery and store policies
    const suggestions = [
        'Routine cơ bản cho da dầu',
        'Kem chống nắng đang sale?',
        'Gợi ý son dưới 300.000đ',
        'Sản phẩm cho da nhạy cảm',
        'Chính sách đổi trả'
    ];

    // State
    let open = false;
    let conversationId = null;
    let isProcessing = false;
    let suggestionsExpanded = false; // Track suggestion panel state
    let supportMode = false;
    let supportSessionId = null;
    let supportPollTimer = null;
    const seenSupportMessages = new Set();

    function openBubble() {
        bubble.setAttribute('aria-hidden', 'false');
        bubble.style.display = 'flex';
        open = true;
        toggle.setAttribute('aria-expanded', 'true');
        inputEl.focus();
        renderSuggestions();
        // initial greeting
        if (!messagesEl.hasChildNodes()) {
            pushAgentMessage('Chào bạn! Mình là trợ lý BeautyStore. Mình có thể gợi ý mỹ phẩm theo loại da, nhu cầu, thành phần, ngân sách và giải đáp chính sách. Thông tin chỉ mang tính tham khảo; nếu đang kích ứng, mang thai hoặc điều trị da, bạn nên trao đổi với chuyên gia y tế. Bạn đang cần tìm gì?');
        }
    }

    function closeBubble() {
        bubble.setAttribute('aria-hidden', 'true');
        bubble.style.display = 'none';
        open = false;
        toggle.setAttribute('aria-expanded', 'false');
        toggle.focus();
    }

    // Toggle suggestions
    function toggleSuggestions() {
        suggestionsExpanded = !suggestionsExpanded;
        if (suggestionsExpanded) {
            suggestionsEl.classList.remove('collapsed');
            suggestionsEl.classList.add('expanded');
            suggestionsToggle.classList.remove('collapsed');
        } else {
            suggestionsEl.classList.remove('expanded');
            suggestionsEl.classList.add('collapsed');
            suggestionsToggle.classList.add('collapsed');
        }
    }

    // Toggle chatbot bubble
    toggle.addEventListener('click', () => {
        open ? closeBubble() : openBubble();
    });
    document.getElementById('chatbot-close')?.addEventListener('click', closeBubble);
    bubble.addEventListener('keydown', event => {
        if (event.key === 'Escape') closeBubble();
        if (event.key !== 'Tab') return;
        const focusable = [...bubble.querySelectorAll('button:not([disabled]), input:not([disabled]), [tabindex]:not([tabindex="-1"])')];
        if (!focusable.length) return;
        const first = focusable[0];
        const last = focusable.at(-1);
        if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
        else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    });
    toggle.addEventListener('keypress', (e) => { 
        if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            open ? closeBubble() : openBubble(); 
        }
    });

    // Toggle suggestions panel
    suggestionsToggle.addEventListener('click', toggleSuggestions);

    // Show/hide bubble initially closed
    closeBubble();

    // render suggestions
    function renderSuggestions() {
        suggestionsEl.innerHTML = '';
        suggestions.forEach(s => {
            const btn = document.createElement('button');
            btn.className = 'suggestion';
            btn.textContent = s;
            btn.addEventListener('click', () => {
                handleUserMessage(s);
            });
            suggestionsEl.appendChild(btn);
        });
    }

    // push user message
    function pushUserMessage(text) {
        const msg = document.createElement('div');
        msg.className = 'msg user';
        msg.textContent = text;
        messagesEl.appendChild(msg);
        messagesEl.scrollTop = messagesEl.scrollHeight;
    }

    // push agent message with typing indicator (preserve line breaks)
    function pushAgentMessage(text, immediate = false) {
        const typing = document.createElement('div');
        typing.className = 'msg agent';
        typing.textContent = '...';
        messagesEl.appendChild(typing);
        messagesEl.scrollTop = messagesEl.scrollHeight;

        const delay = immediate ? 100 : (700 + Math.min(1200, text.length * 15));
        setTimeout(() => {
            // Use textContent to preserve natural line breaks (with CSS white-space: pre-wrap)
            typing.textContent = text;
            messagesEl.scrollTop = messagesEl.scrollHeight;
        }, delay);
    }

    function requestHeaders() {
        const headers = { 'Content-Type': 'application/json' };
        const token = localStorage.getItem('authToken') || localStorage.getItem('token');
        if (token) headers.Authorization = `Bearer ${token}`;
        return headers;
    }

    async function startHumanSupport() {
        if (supportMode || isProcessing) return;
        isProcessing = true;
        humanSupportBtn.disabled = true;
        try {
            const response = await fetch('/api/support/session', {
                method: 'POST',
                credentials: 'same-origin',
                headers: requestHeaders(),
                body: JSON.stringify({})
            });
            const session = await response.json().catch(() => ({}));
            if (!response.ok || !session.sessionId) {
                throw new Error(session.message || 'Không thể tạo phiên hỗ trợ.');
            }
            supportMode = true;
            supportSessionId = session.sessionId;
            headerTitle.textContent = 'Chuyên viên BeautyStore';
            headerSubtitle.textContent = 'Hỗ trợ trực tiếp · phản hồi sớm nhất';
            suggestionsToggle.hidden = true;
            suggestionsEl.hidden = true;
            humanSupportBtn.hidden = true;
            inputEl.placeholder = 'Nhắn cho chuyên viên BeautyStore…';
            pushAgentMessage('Bạn đã chuyển sang kênh hỗ trợ trực tiếp. Hãy để lại nội dung; chuyên viên BeautyStore sẽ phản hồi tại đây.', true);
            await pollSupportMessages();
            supportPollTimer = window.setInterval(pollSupportMessages, 3000);
        } catch (_) {
            pushAgentMessage('Hiện chưa thể kết nối chuyên viên. Bạn vui lòng thử lại sau hoặc liên hệ hotline của cửa hàng.', true);
            humanSupportBtn.disabled = false;
        } finally {
            isProcessing = false;
        }
    }

    async function pollSupportMessages() {
        if (!supportSessionId) return;
        try {
            const response = await fetch(`/api/support/session/${encodeURIComponent(supportSessionId)}/messages`, {
                credentials: 'same-origin',
                headers: requestHeaders()
            });
            if (!response.ok) return;
            const messages = await response.json();
            messages.forEach(message => {
                if (!message.id || seenSupportMessages.has(message.id)) return;
                seenSupportMessages.add(message.id);
                const node = document.createElement('div');
                node.className = `msg ${message.senderType === 'USER' ? 'user' : 'agent'}`;
                node.textContent = String(message.message || '');
                messagesEl.appendChild(node);
            });
            messagesEl.scrollTop = messagesEl.scrollHeight;
            fetch(`/api/support/session/${encodeURIComponent(supportSessionId)}/read`, {
                method: 'POST', credentials: 'same-origin', headers: requestHeaders(), body: '{}'
            }).catch(() => {});
        } catch (_) {
            // Polling retries automatically without exposing conversation data in logs.
        }
    }

    async function sendSupportMessage(message) {
        const response = await fetch(`/api/support/session/${encodeURIComponent(supportSessionId)}/messages`, {
            method: 'POST',
            credentials: 'same-origin',
            headers: requestHeaders(),
            body: JSON.stringify({ message })
        });
        const saved = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(saved.message || 'Không thể gửi tin nhắn.');
        if (saved.id) seenSupportMessages.add(saved.id);
    }

    // Call backend API to get AI response
    async function getAIResponse(userMessage) {
        try {
            const response = await fetch('/api/chatbot/message', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    message: userMessage,
                    conversationId: conversationId
                })
            });

            if (!response.ok) {
                throw new Error('Network response was not ok');
            }

            const data = await response.json();
            
            if (data.success) {
                // Update conversation ID for context
                conversationId = data.conversationId;
                return data.reply;
            } else {
                throw new Error(data.message || data.error || 'Không thể nhận phản hồi');
            }
        } catch (error) {
            console.error('Error calling chatbot API:', error);
            // Fallback error message
            return configuredHotline
                ? `Xin lỗi bạn, mình đang gặp chút trục trặc kỹ thuật. Bạn có thể thử lại sau hoặc gọi hotline ${configuredHotline} để được hỗ trợ trực tiếp.`
                : 'Xin lỗi bạn, mình đang gặp chút trục trặc kỹ thuật. Bạn vui lòng thử lại sau hoặc liên hệ kênh hỗ trợ của cửa hàng.';
        }
    }

    // handle user input
    async function handleUserMessage(text) {
        if (!text || !text.trim()) return;
        if (isProcessing) return; // Prevent multiple simultaneous requests
        
        const userMessage = text.trim();
        pushUserMessage(userMessage);
        inputEl.value = '';
        
        // Disable input while processing
        isProcessing = true;
        inputEl.disabled = true;
        sendBtn.disabled = true;
        
        // Show typing indicator
        const typingIndicator = document.createElement('div');
        typingIndicator.className = 'msg agent';
        typingIndicator.id = 'typing-indicator';
        typingIndicator.textContent = 'BeautyStore đang tìm gợi ý...';
        messagesEl.appendChild(typingIndicator);
        messagesEl.scrollTop = messagesEl.scrollHeight;
        
        try {
            const aiReply = supportMode ? null : await getAIResponse(userMessage);
            if (supportMode) await sendSupportMessage(userMessage);
            
            // Remove typing indicator
            const indicator = document.getElementById('typing-indicator');
            if (indicator) {
                indicator.remove();
            }
            
            // Show AI response
            if (!supportMode) pushAgentMessage(aiReply, true);
            
        } catch (error) {
            console.error('Error handling message:', error);
            const indicator = document.getElementById('typing-indicator');
            if (indicator) {
                indicator.remove();
            }
            pushAgentMessage('Xin lỗi bạn, mình gặp lỗi khi xử lý câu hỏi. Vui lòng thử lại! 😊', true);
        } finally {
            // Re-enable input
            isProcessing = false;
            inputEl.disabled = false;
            sendBtn.disabled = false;
            inputEl.focus();
        }
    }

    sendBtn.addEventListener('click', () => handleUserMessage(inputEl.value));
    humanSupportBtn.addEventListener('click', startHumanSupport);
    inputEl.addEventListener('keypress', (e) => { 
        if (e.key === 'Enter' && !isProcessing) {
            e.preventDefault();
            handleUserMessage(inputEl.value);
        }
    });

    // Accessibility
    toggle.addEventListener('focus', () => toggle.classList.add('focus'));
    toggle.addEventListener('blur', () => toggle.classList.remove('focus'));

    // expose for debugging
    window._beautystoreChatbot = { openBubble, closeBubble };
    window.addEventListener('beforeunload', () => {
        if (supportPollTimer) window.clearInterval(supportPollTimer);
    });
})();
