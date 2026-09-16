// Payment Pending functionality
let currentOrder = null;
let currentOrderToken = null;
let countdownInterval = null;
let paymentPollTimer = null;
let paymentPollAttempt = 0;
let paymentPollDeadline = 0;
let paymentPollInFlight = false;
const PAYMENT_POLL_DELAYS = [3000, 5000, 8000, 13000, 21000, 30000];
const MAX_PAYMENT_POLL_ATTEMPTS = 60;

document.addEventListener('DOMContentLoaded', function() {
    loadOrderDetails();
    setupEventListeners();
});

function setupEventListeners() {
    const cancelOrderBtn = document.getElementById('cancelOrderBtn');
    const confirmCancelBtn = document.getElementById('confirmCancelBtn');

    if (cancelOrderBtn) {
        cancelOrderBtn.addEventListener('click', showCancelModal);
    }

    if (confirmCancelBtn) {
        confirmCancelBtn.addEventListener('click', handleCancelOrder);
    }
}

async function loadOrderDetails() {
    const loadingState = document.getElementById('loadingState');
    const orderDetails = document.getElementById('orderDetails');
    const errorState = document.getElementById('errorState');

    try {
        // Get order number from URL
        const pathParts = window.location.pathname.split('/');
        const orderNumber = pathParts[pathParts.length - 1];

        if (!orderNumber) {
            throw new Error('Order number not found');
        }

        const token = localStorage.getItem('authToken') || localStorage.getItem('token');
        currentOrderToken = sessionStorage.getItem(`beautystore:order-token:${orderNumber}`)
            || sessionStorage.getItem('guestOrderToken');
        if (!token && !currentOrderToken) {
            window.location.href = `/order-confirmation/${encodeURIComponent(orderNumber)}`;
            return;
        }
        const response = await fetchOrder(orderNumber);

        if (response.status === 401 || response.status === 403) {
            if (!token) {
                sessionStorage.removeItem(`beautystore:order-token:${orderNumber}`);
                window.location.href = `/order-confirmation/${encodeURIComponent(orderNumber)}`;
                return;
            }
            window.location.href = '/login?error=unauthorized';
            return;
        }

        if (!response.ok) {
            throw new Error('Order not found');
        }

        const order = await response.json();

        // Check if order is actually pending payment
        if (order.status !== 'PENDING_PAYMENT') {
            // Redirect to order confirmation if order is not pending payment
            window.location.href = '/order-confirmation/' + orderNumber;
            return;
        }

        // Save current order
        currentOrder = order;

        // Display order details
        displayOrderDetails(order);

        // Start countdown timer
        startCountdown(order);
        startStatusPolling(order);

        loadingState.style.display = 'none';
        orderDetails.style.display = 'block';

    } catch (error) {
        console.error('Error loading order:', error);
        loadingState.style.display = 'none';
        errorState.style.display = 'block';
    }
}

function displayOrderDetails(order) {
    // Order number
    const orderNumberEl = document.getElementById('orderNumberDisplay');
    if (orderNumberEl) {
        orderNumberEl.textContent = order.orderNumber || '---';
    }

    // Customer info
    const customerNameEl = document.getElementById('customerName');
    if (customerNameEl) {
        customerNameEl.textContent = order.customerName || '---';
    }

    const customerPhoneEl = document.getElementById('customerPhone');
    if (customerPhoneEl) {
        customerPhoneEl.textContent = order.customerPhone || '---';
    }

    const customerEmailEl = document.getElementById('customerEmail');
    if (customerEmailEl) {
        customerEmailEl.textContent = order.customerEmail || '---';
    }

    const shippingAddressEl = document.getElementById('shippingAddress');
    if (shippingAddressEl) {
        shippingAddressEl.textContent = order.shippingAddress || '---';
    }

    // Payment method
    const paymentMethodMap = {
        'COD': 'Thanh toán khi nhận hàng',
        'VNPAY': 'Thanh toán trực tuyến qua VNPay',
        'E_WALLET': 'Ví điện tử VNPay',
        'BANK_TRANSFER': 'Chuyển khoản ngân hàng',
        'CREDIT_CARD': 'Thẻ tín dụng'
    };
    
    const paymentMethodEl = document.getElementById('paymentMethod');
    if (paymentMethodEl) {
        paymentMethodEl.textContent = paymentMethodMap[order.paymentMethod] || order.paymentMethod || '---';
    }

    // Order date
    if (order.createdAt) {
        const orderDate = new Date(order.createdAt);
        const orderDateEl = document.getElementById('orderDate');
        if (orderDateEl) {
            orderDateEl.textContent = formatDate(orderDate);
        }
    }

    // Order items
    if (order.items && Array.isArray(order.items)) {
        displayOrderItems(order.items);
    }

    const calculatedSubtotal = order.items
        ? order.items.reduce((sum, item) => sum + (Number(item.price) * Number(item.quantity)), 0)
        : 0;
    const subtotal = Number(order.subtotal ?? calculatedSubtotal);
    const orderSubtotalEl = document.getElementById('orderSubtotal');
    if (orderSubtotalEl) {
        orderSubtotalEl.textContent = formatCurrency(subtotal);
    }
    const shippingFee = Number(order.shippingFee || 0);
    const shippingFeeEl = document.getElementById('orderShippingFee');
    if (shippingFeeEl) {
        shippingFeeEl.textContent = shippingFee === 0 ? 'Miễn phí' : formatCurrency(shippingFee);
        shippingFeeEl.classList.toggle('text-success', shippingFee === 0);
    }

    // Voucher discount / FREE_SHIPPING
    const freeShippingVoucher = order.voucherType === 'FREE_SHIPPING';
    if (order.voucherCode && (freeShippingVoucher || Number(order.voucherDiscount) > 0)) {
        const voucherDiscountRow = document.getElementById('voucherDiscountRow');
        if (voucherDiscountRow) {
            voucherDiscountRow.style.display = 'flex';
        }
        
        const displayVoucherCode = document.getElementById('displayVoucherCode');
        if (displayVoucherCode) {
            displayVoucherCode.textContent = order.voucherCode || '';
        }
        
        const displayVoucherDiscount = document.getElementById('displayVoucherDiscount');
        if (displayVoucherDiscount) {
            displayVoucherDiscount.textContent = freeShippingVoucher
                ? 'Miễn phí vận chuyển'
                : '- ' + formatCurrency(order.voucherDiscount);
        }
        const voucherLabel = document.getElementById('pendingVoucherLabel');
        if (voucherLabel) voucherLabel.textContent = freeShippingVoucher
            ? 'Ưu đãi vận chuyển'
            : 'Mã giảm giá';
    }

    // Total
    const orderTotalEl = document.getElementById('orderTotal');
    if (orderTotalEl) {
        orderTotalEl.textContent = formatCurrency(order.totalAmount || 0);
    }
}

function displayOrderItems(items) {
    const container = document.getElementById('orderItemsList');
    container.replaceChildren();

    items.forEach(item => {
        const itemDiv = document.createElement('div');
        itemDiv.className = 'order-item d-flex align-items-center mb-3 pb-3 border-bottom';
        const image = document.createElement('img');
        image.src = window.BeautyUI
            ? BeautyUI.safeUrl(item.productImageUrl)
            : (item.productImageUrl || '/images/beauty/hero-beautystore-v2.webp');
        image.alt = item.productName || 'Sản phẩm';
        image.className = 'rounded me-3';
        image.style.cssText = 'width:80px;height:80px;object-fit:cover';
        const info = document.createElement('div');
        info.className = 'flex-grow-1';
        const name = document.createElement('h6');
        name.className = 'mb-1';
        name.textContent = item.productName || 'Sản phẩm';
        const variant = document.createElement('p');
        variant.className = 'text-muted mb-0 small';
        variant.textContent = [item.variantLabel, item.shadeName, item.netContent].filter(Boolean).join(' · ');
        const quantity = document.createElement('p');
        quantity.className = 'text-muted mb-0 small';
        quantity.textContent = `Số lượng: ${Number(item.quantity) || 0}`;
        info.append(name, variant, quantity);
        const total = document.createElement('div');
        total.className = 'text-end';
        const subtotal = document.createElement('p');
        subtotal.className = 'mb-0 fw-bold';
        subtotal.textContent = formatCurrency(Number(item.price) * Number(item.quantity));
        const unitPrice = document.createElement('p');
        unitPrice.className = 'text-muted mb-0 small';
        unitPrice.textContent = `${formatCurrency(item.price)} × ${Number(item.quantity) || 0}`;
        total.append(subtotal, unitPrice);
        itemDiv.append(image, info, total);
        container.appendChild(itemDiv);
    });
}

function startCountdown(order) {
    if (!order?.createdAt && !order?.reservationExpiresAt) {
        console.error('Created date not provided');
        return;
    }

    const createdTime = new Date(order.createdAt);
    
    // Validate date
    if (isNaN(createdTime.getTime())) {
        console.error('Invalid created date:', order.createdAt);
        return;
    }

    const expiryTime = order.reservationExpiresAt
        ? new Date(order.reservationExpiresAt)
        : new Date(createdTime.getTime() + 15 * 60 * 1000);

    function updateCountdown() {
        const now = new Date();
        const timeLeft = expiryTime - now;

        if (timeLeft <= 0) {
            const timeRemainingEl = document.getElementById('timeRemaining');
            
            if (timeRemainingEl) {
                timeRemainingEl.textContent = 'Đã hết hạn';
                timeRemainingEl.classList.add('text-danger');
            }
            updatePaymentGuidance('Thời gian giữ tồn đã hết. BeautyStore đang kiểm tra trạng thái cuối cùng của giao dịch; nếu chưa thanh toán, hãy hủy đơn và đặt lại từ giỏ hàng.');
            
            clearInterval(countdownInterval);
            scheduleStatusPoll(0, true);
            return;
        }

        const minutes = Math.floor(timeLeft / 60000);
        const seconds = Math.floor((timeLeft % 60000) / 1000);
        
        const timeRemainingEl = document.getElementById('timeRemaining');
        if (timeRemainingEl) {
            timeRemainingEl.textContent = `${minutes} phút ${seconds} giây`;
        }
    }

    updateCountdown();
    countdownInterval = setInterval(updateCountdown, 1000);
}

function startStatusPolling(order) {
    stopStatusPolling();
    paymentPollAttempt = 0;
    const reservationExpiry = order?.reservationExpiresAt
        ? new Date(order.reservationExpiresAt).getTime()
        : NaN;
    const fallbackDeadline = Date.now() + (16 * 60 * 1000);
    paymentPollDeadline = Number.isFinite(reservationExpiry)
        ? Math.min(fallbackDeadline, Math.max(Date.now() + 60_000, reservationExpiry + 60_000))
        : fallbackDeadline;
    scheduleStatusPoll(PAYMENT_POLL_DELAYS[0]);
}

function scheduleStatusPoll(delay, force = false) {
    if (!force && (paymentPollAttempt >= MAX_PAYMENT_POLL_ATTEMPTS
            || Date.now() >= paymentPollDeadline)) {
        stopStatusPolling();
        updatePaymentGuidance('Chưa nhận được trạng thái cuối cùng. Bạn có thể tải lại trang hoặc liên hệ BeautyStore; không tạo lại liên kết VNPay cho đơn này.');
        return;
    }
    if (paymentPollTimer) clearTimeout(paymentPollTimer);
    paymentPollTimer = setTimeout(pollOrderStatus, Math.max(0, Number(delay) || 0));
}

async function pollOrderStatus() {
    if (paymentPollInFlight || !currentOrder?.orderNumber) return;
    paymentPollInFlight = true;
    paymentPollAttempt += 1;

    try {
        const response = await fetchOrder(currentOrder.orderNumber);
        if (response.status === 401 || response.status === 403) {
            stopStatusPolling();
            updatePaymentGuidance('Phiên xem đơn hàng đã hết hạn. Vui lòng xác minh lại để kiểm tra kết quả thanh toán.');
            return;
        }
        if (response.ok) {
            const order = await response.json();
            currentOrder = order;
            if (isFinalPaymentState(order)) {
                stopStatusPolling();
                window.location.replace(`/order-confirmation/${encodeURIComponent(order.orderNumber)}`);
                return;
            }
        }
    } catch (error) {
        console.warn('Không thể cập nhật trạng thái thanh toán:', error);
    } finally {
        paymentPollInFlight = false;
    }

    const delayIndex = Math.min(paymentPollAttempt, PAYMENT_POLL_DELAYS.length - 1);
    scheduleStatusPoll(PAYMENT_POLL_DELAYS[delayIndex]);
}

async function fetchOrder(orderNumber) {
    const token = localStorage.getItem('authToken') || localStorage.getItem('token');
    const guestToken = currentOrderToken
        || sessionStorage.getItem(`beautystore:order-token:${orderNumber}`)
        || sessionStorage.getItem('guestOrderToken');
    if (!token && !guestToken) {
        return new Response(null, { status: 401 });
    }
    const encodedOrderNumber = encodeURIComponent(orderNumber);
    if (token) {
        const memberResponse = await fetch(`/api/orders/${encodedOrderNumber}`, {
            headers: { 'Authorization': `Bearer ${token}` },
            credentials: 'same-origin',
            cache: 'no-store'
        });
        if ((memberResponse.status !== 401 && memberResponse.status !== 403) || !guestToken) {
            return memberResponse;
        }
    }
    return fetch(`/api/guest-orders/${encodedOrderNumber}`, {
        headers: { 'X-Order-Token': guestToken },
        credentials: 'same-origin',
        cache: 'no-store'
    });
}

function isFinalPaymentState(order) {
    if (!order || order.status !== 'PENDING_PAYMENT') return true;
    return ['PAID', 'FAILED', 'CANCELLED', 'EXPIRED', 'RECONCILIATION_REQUIRED']
        .includes(String(order.paymentStatus || '').toUpperCase());
}

function updatePaymentGuidance(message) {
    const guidance = document.getElementById('paymentPendingGuidance');
    if (guidance) guidance.textContent = message;
}

function stopStatusPolling() {
    if (paymentPollTimer) clearTimeout(paymentPollTimer);
    paymentPollTimer = null;
}

function showCancelModal() {
    const modal = new bootstrap.Modal(document.getElementById('cancelConfirmModal'));
    modal.show();
}

async function handleCancelOrder() {
    if (!currentOrder) {
        showNotification('Không tìm thấy thông tin đơn hàng', 'danger');
        return;
    }

    const btn = document.getElementById('confirmCancelBtn');
    if (!btn) return;
    
    btn.disabled = true;
    btn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Đang hủy...';

    try {
        const token = localStorage.getItem('authToken') || localStorage.getItem('token');
        if (!token && !currentOrderToken) {
            window.location.href = `/order-confirmation/${encodeURIComponent(currentOrder.orderNumber)}`;
            return;
        }

        const response = await fetch(token
            ? `/api/orders/${encodeURIComponent(currentOrder.orderNumber)}/cancel`
            : `/api/guest-orders/${encodeURIComponent(currentOrder.orderNumber)}/cancel`, {
            method: 'POST',
            headers: token
                ? {'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json'}
                : {'X-Order-Token': currentOrderToken, 'Content-Type': 'application/json'},
            credentials: 'same-origin'
        });

        if (response.status === 401 || response.status === 403) {
            showNotification('Phiên truy cập đơn hàng đã hết hạn.', 'warning');
            setTimeout(() => window.location.href =
                `/order-confirmation/${encodeURIComponent(currentOrder.orderNumber)}`, 1200);
            return;
        }

        if (!response.ok) {
            throw new Error('Không thể hủy đơn hàng');
        }

        showNotification('Đơn hàng đã được hủy thành công!', 'success');
        
        // Hide modal
        const modal = bootstrap.Modal.getInstance(document.getElementById('cancelConfirmModal'));
        modal.hide();

        // Redirect after 2 seconds
        setTimeout(() => {
            window.location.href = token
                ? '/orders'
                : `/order-confirmation/${encodeURIComponent(currentOrder.orderNumber)}`;
        }, 2000);

    } catch (error) {
        console.error('Error canceling order:', error);
        showNotification('Có lỗi xảy ra: ' + error.message, 'danger');
        btn.disabled = false;
        btn.innerHTML = '<i class="fas fa-times me-2"></i>Xác nhận hủy';
    }
}

function formatCurrency(amount) {
    return new Intl.NumberFormat('vi-VN', {
        style: 'currency',
        currency: 'VND'
    }).format(amount);
}

function formatDate(date) {
    return new Intl.DateTimeFormat('vi-VN', {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
    }).format(date);
}

function showNotification(message, type = 'info') {
    let toastContainer = document.querySelector('.toast-container');
    if (!toastContainer) {
        toastContainer = document.createElement('div');
        toastContainer.className = 'toast-container position-fixed top-0 end-0 p-3';
        document.body.appendChild(toastContainer);
    }

    const allowedTypes = new Set(['primary', 'secondary', 'success', 'danger', 'warning', 'info']);
    const toastElement = document.createElement('div');
    toastElement.className = `toast align-items-center text-white bg-${allowedTypes.has(type) ? type : 'info'} border-0`;
    toastElement.setAttribute('role', 'alert');
    toastElement.setAttribute('aria-live', 'assertive');
    toastElement.setAttribute('aria-atomic', 'true');
    const row = document.createElement('div');
    row.className = 'd-flex';
    const body = document.createElement('div');
    body.className = 'toast-body';
    body.textContent = message;
    const close = document.createElement('button');
    close.type = 'button';
    close.className = 'btn-close btn-close-white me-2 m-auto';
    close.dataset.bsDismiss = 'toast';
    close.setAttribute('aria-label', 'Đóng');
    row.append(body, close);
    toastElement.appendChild(row);
    toastContainer.replaceChildren(toastElement);
    const toast = new bootstrap.Toast(toastElement);
    toast.show();
}

// Cleanup on page unload
window.addEventListener('beforeunload', function() {
    if (countdownInterval) {
        clearInterval(countdownInterval);
    }
    stopStatusPolling();
});
