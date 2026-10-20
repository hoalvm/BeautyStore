// Order confirmation functionality
let currentOrder = null;
let currentOrderUsesGuestAccess = false;
let guestOtpTimer = null;

document.addEventListener('DOMContentLoaded', function() {
    loadOrderDetails();
});

async function loadOrderDetails() {
    const loadingState = document.getElementById('loadingState');
    const orderDetails = document.getElementById('orderDetails');
    const errorState = document.getElementById('errorState');
    const pathParts = window.location.pathname.split('/');
    const orderNumber = decodeURIComponent(pathParts[pathParts.length - 1]);

    try {
        // Get order number from URL
        if (!orderNumber) {
            throw new Error('Order number not found');
        }

        const encodedOrderNumber = encodeURIComponent(orderNumber);
        const token = localStorage.getItem('authToken') || localStorage.getItem('token');
        const guestToken = sessionStorage.getItem(`beautystore:order-token:${orderNumber}`);
        let response;
        let order;
        let checkoutReceipt = false;
        currentOrderUsesGuestAccess = !token;

        if (token) {
            response = await fetch(`/api/orders/${encodedOrderNumber}`, {
                headers: { 'Authorization': `Bearer ${token}` },
                credentials: 'same-origin'
            });
        }
        if (guestToken && (!response || [401, 403, 404].includes(response.status))) {
            currentOrderUsesGuestAccess = true;
            response = await fetch(`/api/guest-orders/${encodedOrderNumber}`, {
                headers: { 'X-Order-Token': guestToken },
                credentials: 'same-origin'
            });
        } else if (!response) {
            const cached = readCheckoutOrder(orderNumber);
            if (cached && cached.paymentMethod === 'COD') {
                order = cached;
                checkoutReceipt = true;
                sessionStorage.removeItem('beautystore:last-order');
            } else {
                renderGuestOrderAccess(orderNumber, cached?.customerEmail);
                return;
            }
        }

        if (response) {
            if (response.status === 401 || response.status === 403) {
                if (currentOrderUsesGuestAccess) {
                    sessionStorage.removeItem(`beautystore:order-token:${orderNumber}`);
                    renderGuestOrderAccess(orderNumber);
                    return;
                }
                window.location.href = '/login?error=unauthorized';
                return;
            }
            if (!response.ok) {
                const error = await response.json().catch(() => ({}));
                throw new Error(error.message || 'Không tìm thấy đơn hàng');
            }
            order = await response.json();
        }
        // Save current order
        currentOrder = order;

        // Check if order is PENDING_PAYMENT and redirect to payment-pending page
        if (isAwaitingPayment(order)) {
            window.location.href = '/payment-pending/' + orderNumber;
            return;
        }

        // Keep the cancelled VNPay snapshot visible without offering another method.
        if (order.paymentMethod === 'VNPAY' && order.status === 'CANCELLED'
                && order.paymentStatus !== 'RECONCILIATION_REQUIRED') {
            // Display order details FIRST
            displayOrderDetails(order);
            
            // Then modify the UI to show cancelled message
            displayCancelledPaymentMessage(order);
            
            loadingState.style.display = 'none';
            orderDetails.style.display = 'block';
            return;
        }

        // Display order details
        displayOrderDetails(order);

        loadingState.style.display = 'none';
        orderDetails.style.display = 'block';

        // Add cancel button if applicable
        if (checkoutReceipt) {
            const notice = document.createElement('div');
            notice.className = 'alert alert-info';
            notice.textContent = 'Đây là biên nhận lúc đặt hàng. Xác minh OTP để xem trạng thái mới nhất hoặc hủy đơn.';
            const verify = document.createElement('button');
            verify.type = 'button';
            verify.className = 'btn btn-outline-primary ms-2';
            verify.textContent = 'Xác minh đơn hàng';
            verify.addEventListener('click', () => renderGuestOrderAccess(orderNumber, order.customerEmail));
            notice.appendChild(verify);
            document.getElementById('orderStatusActions').replaceChildren(notice);
        } else addCancelButton(order);

        // Trigger confetti animation
        if (!['CANCELLED', 'FAILED', 'REFUNDED'].includes(order.status)
                && order.paymentStatus !== 'RECONCILIATION_REQUIRED') celebrateOrder();

    } catch (error) {
        console.error('Unable to load order details');
        loadingState.style.display = 'none';
        errorState.style.display = 'block';
        
        // Show more detailed error message
        const errorMessage = document.querySelector('#errorState .card-body');
        if (errorMessage) {
            errorMessage.innerHTML = `
                <h3 class="text-danger mb-3">
                    <i class="fas fa-exclamation-triangle me-2"></i>
                    Không tìm thấy đơn hàng
                </h3>
                <p class="text-muted mb-4">Đơn hàng không tồn tại hoặc đã bị xóa.</p>
                <p class="text-danger mb-4">Chi tiết lỗi: ${window.BeautyUI ? BeautyUI.escapeHtml(error.message) : 'Không thể tải đơn hàng'}</p>
                <a href="/orders" class="btn btn-primary me-2">
                    <i class="fas fa-list me-2"></i>Xem danh sách đơn hàng
                </a>
                <a href="/products" class="btn btn-outline-primary">
                    <i class="fas fa-shopping-bag me-2"></i>Tiếp tục mua sắm
                </a>
            `;
        }
    }
}

function readCheckoutOrder(orderNumber) {
    try {
        const order = JSON.parse(sessionStorage.getItem('beautystore:last-order') || 'null');
        return order?.orderNumber === orderNumber ? order : null;
    } catch (_) {
        return null;
    }
}

function renderGuestOrderAccess(orderNumber, suggestedEmail = '') {
    if (guestOtpTimer) clearInterval(guestOtpTimer);
    guestOtpTimer = null;
    const loadingState = document.getElementById('loadingState');
    const orderDetails = document.getElementById('orderDetails');
    const errorState = document.getElementById('errorState');
    orderDetails.style.display = 'none';
    errorState.style.display = 'none';
    loadingState.style.display = 'block';
    loadingState.replaceChildren();

    const card = document.createElement('div');
    card.className = 'card border-0 shadow-sm mx-auto text-start';
    card.style.maxWidth = '520px';
    const body = document.createElement('div');
    body.className = 'card-body p-4 p-md-5';
    const title = document.createElement('h2');
    title.className = 'h4 beauty-heading mb-2';
    title.textContent = 'Xác minh để xem đơn hàng';
    const intro = document.createElement('p');
    intro.className = 'text-muted';
    intro.textContent = 'Nhập email đã dùng khi đặt hàng. Mã OTP có hiệu lực trong 5 phút.';

    const form = document.createElement('form');
    const emailLabel = document.createElement('label');
    emailLabel.className = 'form-label';
    emailLabel.htmlFor = 'guestOrderEmail';
    emailLabel.textContent = 'Email đặt hàng';
    const email = document.createElement('input');
    email.id = 'guestOrderEmail';
    email.className = 'form-control mb-3';
    email.type = 'email';
    email.autocomplete = 'email';
    email.required = true;
    email.value = suggestedEmail || '';
    const requestButton = document.createElement('button');
    requestButton.type = 'submit';
    requestButton.className = 'btn btn-danger w-100';
    requestButton.textContent = 'Gửi mã OTP';
    const message = document.createElement('p');
    message.className = 'small mt-3 mb-0';
    message.setAttribute('role', 'status');
    message.setAttribute('aria-live', 'polite');
    form.append(emailLabel, email, requestButton, message);
    body.append(title, intro, form);
    card.appendChild(body);
    loadingState.appendChild(card);

    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (requestButton.disabled) return;
        requestButton.disabled = true;
        requestButton.textContent = 'Đang gửi...';
        message.className = 'small mt-3 mb-0 text-muted';
        message.textContent = '';
        try {
            const response = await fetch('/api/guest-orders/access/request', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'same-origin',
                body: JSON.stringify({ orderNumber, email: email.value.trim() })
            });
            const result = await response.json().catch(() => ({}));
            if (!response.ok) throw new Error(result.message || 'Không thể gửi OTP');
            showGuestOtpForm(form, orderNumber, email.value.trim(), message);
        } catch (error) {
            message.className = 'small mt-3 mb-0 text-danger';
            message.textContent = error.message || 'Không thể gửi OTP. Vui lòng thử lại.';
            requestButton.disabled = false;
            requestButton.textContent = 'Gửi lại mã OTP';
        }
    });
}

function showGuestOtpForm(form, orderNumber, email, message) {
    const otpForm = form.cloneNode(false);
    form.replaceWith(otpForm);
    form = otpForm;
    const label = document.createElement('label');
    label.className = 'form-label';
    label.htmlFor = 'guestOrderOtp';
    label.textContent = 'Mã OTP gồm 6 chữ số';
    const otp = document.createElement('input');
    otp.id = 'guestOrderOtp';
    otp.className = 'form-control mb-3';
    otp.inputMode = 'numeric';
    otp.autocomplete = 'one-time-code';
    otp.pattern = '[0-9]{6}';
    otp.maxLength = 6;
    otp.required = true;
    const verify = document.createElement('button');
    verify.type = 'submit';
    verify.className = 'btn btn-danger w-100';
    verify.textContent = 'Xác minh và xem đơn';
    const resend = document.createElement('button');
    resend.type = 'button';
    resend.className = 'btn btn-outline-secondary w-100 mt-2';
    const changeEmail = document.createElement('button');
    changeEmail.type = 'button';
    changeEmail.className = 'btn btn-link w-100 mt-2';
    changeEmail.textContent = 'Nhập lại email';
    changeEmail.addEventListener('click', () => renderGuestOrderAccess(orderNumber, email));
    message.className = 'small mt-3 mb-0 text-success';
    message.textContent = 'Nếu thông tin khớp, OTP đã được gửi đến email của bạn.';
    form.append(label, otp, verify, resend, changeEmail, message);
    otp.focus();
    let sentAt = Date.now();
    let verifying = false;
    let resending = false;
    const updateCooldown = () => {
        const seconds = Math.max(0, Math.ceil((sentAt + 60000 - Date.now()) / 1000));
        resend.disabled = seconds > 0 || resending || verifying;
        resend.textContent = seconds > 0 ? `Gửi lại mã sau ${seconds}s` : 'Gửi lại mã OTP';
        if (Date.now() - sentAt >= 300000 && !verifying) {
            verify.disabled = true;
            message.textContent = 'Mã OTP đã hết hạn. Vui lòng gửi lại mã.';
        }
    };
    if (guestOtpTimer) clearInterval(guestOtpTimer);
    updateCooldown();
    guestOtpTimer = setInterval(updateCooldown, 1000);
    resend.addEventListener('click', async () => {
        if (resend.disabled || resending || verifying) return;
        resending = true;
        verify.disabled = true;
        updateCooldown();
        try {
            const response = await fetch('/api/guest-orders/access/request', {
                method: 'POST', credentials: 'same-origin', headers: {'Content-Type':'application/json'},
                body: JSON.stringify({orderNumber, email})
            });
            const result = await response.json().catch(() => ({}));
            if (!response.ok) throw new Error(result.message || 'Chưa thể gửi lại OTP.');
            sentAt = Date.now();
            otp.value = '';
            message.textContent = 'Nếu thông tin khớp, mã mới đã được gửi đến email của bạn.';
            verify.disabled = false;
            otp.focus();
        } catch (error) {
            message.textContent = error.message;
            verify.disabled = Date.now() - sentAt >= 300000;
        } finally {resending = false; updateCooldown();}
    });

    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (verifying || resending || verify.disabled || !form.reportValidity()) return;
        verifying = true;
        verify.disabled = true;
        verify.textContent = 'Đang xác minh...';
        try {
            const response = await fetch('/api/guest-orders/access/verify', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'same-origin',
                body: JSON.stringify({ orderNumber, email, otp: otp.value.trim() })
            });
            const result = await response.json().catch(() => ({}));
            if (!response.ok || !result.accessToken) throw new Error(result.message || 'OTP không hợp lệ');
            sessionStorage.setItem(`beautystore:order-token:${orderNumber}`, result.accessToken);
            sessionStorage.setItem('guestOrderToken', result.accessToken);
            clearInterval(guestOtpTimer);
            guestOtpTimer = null;
            loadOrderDetails();
        } catch (error) {
            message.className = 'small mt-3 mb-0 text-danger';
            message.textContent = error.message || 'Không thể xác minh OTP.';
            verify.disabled = false;
            verify.textContent = 'Xác minh và xem đơn';
        } finally {
            verifying = false;
        }
    });
}

function isAwaitingPayment(order) {
    return order?.status === 'PENDING_PAYMENT' && order?.paymentMethod === 'VNPAY'
        && !['PAID', 'FAILED', 'CANCELLED', 'EXPIRED', 'RECONCILIATION_REQUIRED'].includes(order.paymentStatus);
}

function updateOrderStatusHeading(order) {
    const title = document.getElementById('orderStatusTitle');
    const message = document.getElementById('orderStatusMessage');
    const icon = document.getElementById('orderStatusIcon');
    if (order.paymentStatus === 'RECONCILIATION_REQUIRED') {
        title.textContent = 'Giao dịch đang được đối soát';
        message.textContent = 'BeautyStore đang kiểm tra thanh toán. Vui lòng không thanh toán lại hoặc tạo đơn trùng; liên hệ hỗ trợ nếu cần.';
    } else if (['CANCELLED', 'FAILED', 'REFUNDED'].includes(order.status)) {
        title.textContent = {CANCELLED:'Đơn hàng đã hủy', FAILED:'Giao hàng không thành công', REFUNDED:'Đơn hàng đã hoàn tiền'}[order.status];
        message.textContent = 'Thông tin bên dưới là lịch sử đơn hàng, không phải xác nhận giao hàng thành công.';
    } else return;
    title.classList.remove('beauty-gradient-text');
    icon.innerHTML = '<i class="fas fa-circle-info text-warning" style="font-size: 5rem;" aria-hidden="true"></i>';
}

function displayCancelledPaymentMessage(order) {
    const statusIcon = document.getElementById('orderStatusIcon');
    if (statusIcon) {
        statusIcon.innerHTML = '<i class="fas fa-times-circle text-danger" style="font-size: 5rem;"></i>';
    }

    const statusTitle = document.getElementById('orderStatusTitle');
    if (statusTitle) {
        statusTitle.textContent = 'Thanh toán không thành công';
        statusTitle.classList.remove('beauty-gradient-text');
        statusTitle.classList.add('text-danger');
    }

    const statusMessage = document.getElementById('orderStatusMessage');
    if (statusMessage) {
        statusMessage.innerHTML = 'Đơn hàng của bạn đã bị hủy do thanh toán không thành công.';
        statusMessage.classList.remove('text-muted');
        statusMessage.classList.add('text-danger');
    }

    const orderNumberBox = document.getElementById('orderNumberBox');
    if (orderNumberBox) {
        orderNumberBox.style.background = 'linear-gradient(135deg, #ffebee 0%, #ffcdd2 100%)';
    }

    const orderNumberDisplay = document.getElementById('orderNumberDisplay');
    if (orderNumberDisplay) {
        orderNumberDisplay.classList.remove('text-primary');
        orderNumberDisplay.classList.add('text-danger');
    }

    const statusActions = document.getElementById('orderStatusActions');
    if (statusActions) {
    const cancelReason = getPaymentCancelReason(order.vnpayResponseCode, order.paymentStatus);
        statusActions.innerHTML = `
            <div class="alert alert-danger mt-3" role="alert">
                <i class="fas fa-info-circle me-2"></i>
                <strong>Lý do hủy:</strong> ${cancelReason}
            </div>
            <div class="d-flex justify-content-center flex-wrap gap-2 mt-3">
                <a href="/products" class="btn btn-primary">
                    <i class="fas fa-shopping-bag me-2"></i>Đặt hàng mới
                </a>
                <a href="/orders" class="btn btn-outline-secondary">
                    <i class="fas fa-list me-2"></i>Xem đơn hàng của tôi
                </a>
            </div>
        `;
    }

}

function getPaymentCancelReason(responseCode, paymentStatus) {
    const reasons = {
        '24': 'Khách hàng hủy giao dịch',
        '11': 'Đã hết hạn chờ thanh toán',
        '13': 'Nhập sai mật khẩu OTP',
        '51': 'Tài khoản không đủ số dư',
        '65': 'Vượt quá hạn mức giao dịch',
        '75': 'Ngân hàng đang bảo trì',
        '79': 'Nhập sai mật khẩu quá số lần quy định'
    };
    if (responseCode && reasons[responseCode]) {
        return reasons[responseCode];
    }

    if (paymentStatus === 'CANCELLED') {
        return 'Đơn hàng đã được hủy bởi bạn.';
    }

    return 'Thanh toán không thành công. Vui lòng thử lại.';
}

function displayOrderDetails(order) {
    // Reset status header to success state by default
    const statusIcon = document.getElementById('orderStatusIcon');
    if (statusIcon) {
        statusIcon.innerHTML = '<i class="fas fa-check-circle text-success" style="font-size: 5rem;"></i>';
    }

    const statusTitle = document.getElementById('orderStatusTitle');
    if (statusTitle) {
        statusTitle.textContent = 'Bạn đã đặt hàng thành công!';
        statusTitle.classList.add('beauty-gradient-text');
        statusTitle.classList.remove('text-danger');
    }

    const statusMessage = document.getElementById('orderStatusMessage');
    if (statusMessage) {
        statusMessage.textContent = 'Cảm ơn bạn đã lựa chọn BeautyStore!';
        statusMessage.classList.add('text-muted');
        statusMessage.classList.remove('text-danger');
    }

    const orderNumberBox = document.getElementById('orderNumberBox');
    if (orderNumberBox) {
        orderNumberBox.style.background = 'linear-gradient(135deg, #fff5f8 0%, #f4dce5 100%)';
    }

    const orderNumberDisplay = document.getElementById('orderNumberDisplay');
    if (orderNumberDisplay) {
        orderNumberDisplay.classList.add('text-primary');
        orderNumberDisplay.classList.remove('text-danger');
    }

    const statusActions = document.getElementById('orderStatusActions');
    if (statusActions) {
        statusActions.innerHTML = '';
    }

    // Order number
    document.getElementById('orderNumberDisplay').textContent = order.orderNumber;

    // Customer info
    document.getElementById('customerName').textContent = order.customerName;
    document.getElementById('customerEmail').textContent = order.customerEmail;
    document.getElementById('customerPhone').textContent = order.customerPhone;
    document.getElementById('shippingAddress').textContent = order.shippingAddress;

    // Payment method
    const paymentMethodText = getPaymentMethodText(order.paymentMethod);
    document.getElementById('paymentMethod').textContent = paymentMethodText;

    // Order items
    const itemsList = document.getElementById('orderItemsList');
    itemsList.replaceChildren();

    order.items.forEach(item => {
        const itemDiv = document.createElement('div');
        itemDiv.className = 'order-item d-flex align-items-center mb-3 pb-3 border-bottom';
        const image = document.createElement('img');
        image.src = window.BeautyUI
            ? BeautyUI.safeUrl(item.productImageUrl)
            : '/images/beauty/hero-beautystore-v2.webp';
        image.alt = item.productName || 'Sản phẩm';
        image.className = 'rounded me-3';
        image.style.cssText = 'width:60px;height:60px;object-fit:cover';

        const info = document.createElement('div');
        info.className = 'flex-grow-1';
        const name = document.createElement('h6');
        name.className = 'mb-1';
        name.textContent = item.productName || 'Sản phẩm';
        const variant = document.createElement('small');
        variant.className = 'text-muted d-block';
        variant.textContent = [item.variantLabel, item.shadeName, item.netContent].filter(Boolean).join(' · ');
        const quantity = document.createElement('small');
        quantity.className = 'text-muted';
        quantity.textContent = `Số lượng: ${Number(item.quantity) || 0}`;
        info.append(name, variant, quantity);

        const total = document.createElement('div');
        total.className = 'text-end';
        const subtotal = document.createElement('p');
        subtotal.className = 'mb-0 fw-bold text-danger';
        subtotal.textContent = formatPrice(item.subtotal);
        const unitPrice = document.createElement('small');
        unitPrice.className = 'text-muted';
        unitPrice.textContent = `${formatPrice(item.price)} × ${Number(item.quantity) || 0}`;
        total.append(subtotal, unitPrice);
        if (order.status === 'DELIVERED' && item.id) {
            const review = document.createElement('button');
            review.type = 'button';
            review.className = 'btn btn-sm btn-outline-warning mt-2 d-block';
            review.textContent = 'Viết đánh giá';
            review.addEventListener('click', () => openOrderReview(order, item, review));
            total.appendChild(review);
            const returnButton = document.createElement('button');
            returnButton.type = 'button';
            returnButton.className = 'btn btn-sm btn-outline-danger mt-2 d-block w-100';
            returnButton.textContent = 'Yêu cầu đổi trả';
            returnButton.addEventListener('click', () => openReturnRequest(order, item));
            total.appendChild(returnButton);
        }
        itemDiv.append(image, info, total);
        itemsList.appendChild(itemDiv);
    });

    // Calculate subtotal (items total before discount)
    const itemsSubtotal = order.items.reduce((sum, item) => sum + item.subtotal, 0);
    
    // Order totals
    document.getElementById('orderSubtotal').textContent = formatPrice(itemsSubtotal);
    const shippingFee = Math.max(0, Number(order.shippingFee) || 0);
    const shippingFeeElement = document.getElementById('orderShippingFee');
    if (shippingFeeElement) {
        shippingFeeElement.textContent = shippingFee === 0 ? 'Miễn phí' : formatPrice(shippingFee);
    }
    
    // Display voucher discount if applied
    const voucherRow = document.getElementById('voucherDiscountRow');
    
    const freeShippingVoucher = order.voucherType === 'FREE_SHIPPING';
    const hasVoucher = order.voucherCode &&
                       order.voucherCode.trim() !== '' &&
                       (freeShippingVoucher || parseFloat(order.voucherDiscount) > 0);
    
    if (hasVoucher) {
        voucherRow.style.display = 'flex';
        document.getElementById('displayVoucherCode').textContent = order.voucherCode;
        const voucherLabel = document.getElementById('confirmationVoucherLabel');
        if (voucherLabel) voucherLabel.textContent = freeShippingVoucher
            ? 'Ưu đãi vận chuyển'
            : 'Mã giảm giá';
        document.getElementById('displayVoucherDiscount').textContent = freeShippingVoucher
            ? 'Miễn phí vận chuyển'
            : '- ' + formatPrice(order.voucherDiscount);
    } else {
        voucherRow.style.display = 'none';
    }
    
    document.getElementById('orderTotal').textContent = formatPrice(order.totalAmount);
    updateOrderStatusHeading(order);
    if (order.status === 'DELIVERED') loadReturnProgress(order);
}

function orderAccessHeaders(orderNumber, json = false) {
    const headers = {};
    const token = localStorage.getItem('authToken') || localStorage.getItem('token');
    const guestToken = sessionStorage.getItem(`beautystore:order-token:${orderNumber}`);
    if (token && !currentOrderUsesGuestAccess) headers.Authorization = `Bearer ${token}`;
    if (guestToken) headers['X-Order-Token'] = guestToken;
    if (json) headers['Content-Type'] = 'application/json';
    return headers;
}

let orderReviewRequestPending = false;

function openOrderReview(order, item, actionButton) {
    if (orderReviewRequestPending) return;
    const previous = document.getElementById('orderReviewModal');
    if (previous) {
        bootstrap.Modal.getInstance(previous)?.dispose();
        previous.remove();
    }
    const modal = document.createElement('div');
    modal.id = 'orderReviewModal';
    modal.className = 'modal fade';
    modal.tabIndex = -1;
    modal.setAttribute('aria-labelledby', 'orderReviewTitle');
    modal.setAttribute('aria-hidden', 'true');
    // All interpolated user/order data is assigned through textContent below.
    modal.innerHTML = `
        <div class="modal-dialog modal-dialog-centered"><div class="modal-content">
            <div class="modal-header"><h2 class="modal-title h5" id="orderReviewTitle">Viết đánh giá</h2>
                <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Đóng"></button></div>
            <form id="orderReviewForm">
                <div class="modal-body">
                    <p class="small text-muted">Đánh giá sản phẩm đã nhận, kể cả khi sản phẩm đã ngừng bán. Nội dung sẽ hiển thị sau khi được duyệt.</p>
                    <label for="orderReviewStars" class="form-label">Số sao</label>
                    <select id="orderReviewStars" class="form-select mb-3" required><option value="5">5 – Rất hài lòng</option><option value="4">4 – Hài lòng</option><option value="3">3 – Bình thường</option><option value="2">2 – Chưa hài lòng</option><option value="1">1 – Không hài lòng</option></select>
                    <label for="orderReviewHeading" class="form-label">Tiêu đề</label>
                    <input id="orderReviewHeading" class="form-control mb-3" maxlength="120">
                    <label for="orderReviewContent" class="form-label">Nội dung</label>
                    <textarea id="orderReviewContent" class="form-control mb-3" rows="4" maxlength="3000"></textarea>
                    <label for="orderReviewSkinType" class="form-label">Loại da của bạn</label>
                    <input id="orderReviewSkinType" class="form-control mb-3" maxlength="50">
                    <label for="orderReviewImages" class="form-label">Ảnh thực tế (tối đa 5)</label>
                    <input id="orderReviewImages" class="form-control mb-3" type="file" accept="image/jpeg,image/png,image/webp" multiple>
                    <p id="orderReviewMessage" class="small mb-0" role="status" aria-live="polite"></p>
                </div>
                <div class="modal-footer"><button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Đóng</button>
                    <button type="submit" class="btn btn-danger" id="orderReviewSubmit">Gửi đánh giá</button></div>
            </form>
        </div></div>`;
    document.body.appendChild(modal);
    document.getElementById('orderReviewTitle').textContent = `Đánh giá: ${item.productName || 'Sản phẩm'}`;
    const form = document.getElementById('orderReviewForm');
    const button = document.getElementById('orderReviewSubmit');
    const message = document.getElementById('orderReviewMessage');
    const uploadedFiles = new Map();
    let submitting = false;
    let submitted = false;
    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (submitting || submitted || !form.reportValidity()) return;
        submitting = true;
        orderReviewRequestPending = true;
        button.disabled = true;
        message.className = 'small mb-0 text-muted';
        message.textContent = 'Đang gửi đánh giá...';
        try {
            const imageUrls = await BeautyUI.uploadEvidence(document.getElementById('orderReviewImages').files,
                'review', item.id, orderAccessHeaders(order.orderNumber), uploadedFiles);
            const response = await fetch('/api/reviews', {
                method: 'POST', headers: orderAccessHeaders(order.orderNumber, true),
                body: JSON.stringify({
                    orderItemId: Number(item.id), stars: Number(document.getElementById('orderReviewStars').value),
                    title: document.getElementById('orderReviewHeading').value.trim(),
                    content: document.getElementById('orderReviewContent').value.trim(),
                    skinType: document.getElementById('orderReviewSkinType').value.trim(), imageUrls
                })
            });
            if (!response.ok) throw new Error(await BeautyUI.errorMessage(response, 'Không thể gửi đánh giá.'));
            submitted = true;
            message.className = 'small mb-0 text-success';
            message.textContent = 'Cảm ơn bạn! Đánh giá đã gửi và đang chờ duyệt.';
            button.textContent = 'Đã gửi, chờ duyệt';
            if (actionButton) {
                actionButton.disabled = true;
                actionButton.textContent = 'Đã gửi, chờ duyệt';
            }
        } catch (error) {
            message.className = 'small mb-0 text-danger';
            message.textContent = error.message;
        } finally {
            submitting = false;
            orderReviewRequestPending = false;
            button.disabled = submitted;
        }
    });
    bootstrap.Modal.getOrCreateInstance(modal).show();
}

function openReturnRequest(order, item) {
    const uploadedFiles = new Map();
    let submitting = false;
    let submitted = false;
    document.getElementById('beautyReturnModal')?.remove();
    const modal = document.createElement('div');
    modal.id = 'beautyReturnModal';
    modal.className = 'modal fade';
    modal.tabIndex = -1;
    modal.setAttribute('aria-labelledby', 'beautyReturnTitle');
    modal.setAttribute('aria-hidden', 'true');

    const dialog = document.createElement('div');
    dialog.className = 'modal-dialog modal-dialog-centered';
    const content = document.createElement('div');
    content.className = 'modal-content';
    const header = document.createElement('div');
    header.className = 'modal-header';
    const title = document.createElement('h2');
    title.id = 'beautyReturnTitle';
    title.className = 'modal-title h5';
    title.textContent = `Yêu cầu đổi trả: ${item.productName || 'Sản phẩm'}`;
    const close = document.createElement('button');
    close.type = 'button';
    close.className = 'btn-close';
    close.setAttribute('data-bs-dismiss', 'modal');
    close.setAttribute('aria-label', 'Đóng');
    header.append(title, close);

    const form = document.createElement('form');
    const body = document.createElement('div');
    body.className = 'modal-body';
    const policy = document.createElement('p');
    policy.className = 'small text-muted';
    policy.textContent = 'Đổi ý: sản phẩm phải nguyên seal trong 7 ngày. Hàng sai, lỗi hoặc hư hỏng phải báo trong 48 giờ và có ảnh.';
    const reasonLabel = document.createElement('label');
    reasonLabel.className = 'form-label';
    reasonLabel.htmlFor = 'returnReason';
    reasonLabel.textContent = 'Lý do';
    const reason = document.createElement('select');
    reason.id = 'returnReason';
    reason.className = 'form-select mb-3';
    const reasons = [
        ['UNOPENED_CHANGE_OF_MIND', 'Đổi ý, hàng còn nguyên seal'],
        ['WRONG_ITEM', 'Giao sai sản phẩm'],
        ['DAMAGED', 'Hư hỏng khi giao'],
        ['DEFECTIVE', 'Sản phẩm lỗi'],
        ['OTHER', 'Lý do khác']
    ];
    reasons.forEach(([value, label]) => {
        const option = document.createElement('option');
        option.value = value;
        option.textContent = label;
        reason.appendChild(option);
    });
    const quantityLabel = document.createElement('label');
    quantityLabel.className = 'form-label';
    quantityLabel.htmlFor = 'returnQuantity';
    quantityLabel.textContent = 'Số lượng';
    const quantity = document.createElement('input');
    quantity.id = 'returnQuantity';
    quantity.type = 'number';
    quantity.className = 'form-control mb-3';
    quantity.min = '1';
    quantity.max = String(Math.max(1, Number(item.quantity) || 1));
    quantity.value = '1';
    quantity.required = true;
    const sealWrap = document.createElement('div');
    sealWrap.className = 'form-check mb-3';
    const sealed = document.createElement('input');
    sealed.id = 'returnSealed';
    sealed.type = 'checkbox';
    sealed.className = 'form-check-input';
    const sealLabel = document.createElement('label');
    sealLabel.className = 'form-check-label';
    sealLabel.htmlFor = sealed.id;
    sealLabel.textContent = 'Tôi xác nhận sản phẩm còn nguyên seal/chưa mở';
    sealWrap.append(sealed, sealLabel);
    const detailsLabel = document.createElement('label');
    detailsLabel.className = 'form-label';
    detailsLabel.htmlFor = 'returnDetails';
    detailsLabel.textContent = 'Mô tả';
    const details = document.createElement('textarea');
    details.id = 'returnDetails';
    details.className = 'form-control mb-3';
    details.rows = 3;
    details.maxLength = 2000;
    const imagesLabel = document.createElement('label');
    imagesLabel.className = 'form-label';
    imagesLabel.htmlFor = 'returnImages';
    imagesLabel.textContent = 'Ảnh bằng chứng (tối đa 5)';
    const images = document.createElement('input');
    images.id = 'returnImages';
    images.type = 'file';
    images.accept = 'image/jpeg,image/png,image/webp';
    images.multiple = true;
    images.className = 'form-control mb-3';
    const message = document.createElement('p');
    message.className = 'small mb-0';
    message.setAttribute('role', 'status');
    body.append(policy, reasonLabel, reason, quantityLabel, quantity, sealWrap,
        detailsLabel, details, imagesLabel, images, message);

    const footer = document.createElement('div');
    footer.className = 'modal-footer';
    const cancel = document.createElement('button');
    cancel.type = 'button';
    cancel.className = 'btn btn-outline-secondary';
    cancel.setAttribute('data-bs-dismiss', 'modal');
    cancel.textContent = 'Đóng';
    const submit = document.createElement('button');
    submit.type = 'submit';
    submit.className = 'btn btn-danger';
    submit.textContent = 'Gửi yêu cầu';
    footer.append(cancel, submit);
    form.append(body, footer);
    content.append(header, form);
    dialog.appendChild(content);
    modal.appendChild(dialog);
    document.body.appendChild(modal);

    form.addEventListener('submit', async event => {
        event.preventDefault();
        if (submitting || submitted || !form.reportValidity()) return;
        submitting = true;
        submit.disabled = true;
        message.className = 'small mb-0 text-muted';
        message.textContent = 'Đang gửi yêu cầu...';
        try {
            const files = Array.from(images.files || []);
            if (reason.value === 'UNOPENED_CHANGE_OF_MIND' && !sealed.checked) {
                throw new Error('Sản phẩm đổi ý phải còn nguyên seal.');
            }
            if (['WRONG_ITEM', 'DAMAGED', 'DEFECTIVE'].includes(reason.value) && !files.length) {
                throw new Error('Vui lòng cung cấp ảnh cho hàng sai, lỗi hoặc hư hỏng.');
            }
            const imageUrls = await BeautyUI.uploadEvidence(files, 'return', item.id,
                orderAccessHeaders(order.orderNumber), uploadedFiles);
            const response = await fetch('/api/returns', {
                method: 'POST',
                headers: orderAccessHeaders(order.orderNumber, true),
                body: JSON.stringify({
                    orderNumber: order.orderNumber,
                    items: [{
                        orderItemId: Number(item.id),
                        quantity: Number(quantity.value),
                        reason: reason.value,
                        unopenedAndSealed: sealed.checked,
                        details: details.value.trim(),
                        imageUrls
                    }]
                })
            });
            const result = await response.json().catch(() => ({}));
            if (!response.ok) throw new Error(result.message || 'Không thể tạo yêu cầu đổi trả.');
            submitted = true;
            message.className = 'small mb-0 text-success';
            message.textContent = `Đã tạo yêu cầu #${result.id}; trạng thái: ${result.status}.`;
            submit.remove();
            loadReturnProgress(order);
        } catch (error) {
            message.className = 'small mb-0 text-danger';
            message.textContent = error.message || 'Không thể tạo yêu cầu đổi trả.';
            submit.disabled = false;
        } finally {
            submitting = false;
        }
    });
    bootstrap.Modal.getOrCreateInstance(modal).show();
}

async function loadReturnProgress(order) {
    const previous = document.getElementById('returnProgress');
    if (previous) previous.remove();
    try {
        const response = await fetch(`/api/returns?orderNumber=${encodeURIComponent(order.orderNumber)}`, {
            headers: orderAccessHeaders(order.orderNumber)
        });
        if (!response.ok) return;
        const requests = await response.json();
        if (!Array.isArray(requests) || !requests.length) return;
        const panel = document.createElement('section');
        panel.id = 'returnProgress';
        panel.className = 'alert alert-light border mt-3';
        const heading = document.createElement('h3');
        heading.className = 'h6';
        heading.textContent = 'Tiến trình đổi trả';
        panel.appendChild(heading);
        requests.forEach(request => {
            const row = document.createElement('p');
            row.className = 'small mb-1';
            row.textContent = `Yêu cầu #${request.id}: ${request.status}`;
            panel.appendChild(row);
        });
        document.getElementById('orderItemsList')?.insertAdjacentElement('afterend', panel);
    } catch (_) {
        // Order detail remains usable if return progress is temporarily unavailable.
    }
}

function getPaymentMethodText(method) {
    const methodMap = {
        'COD': 'Thanh toán khi nhận hàng (COD)',
        'VNPAY': 'Thanh toán qua VNPay',
        // Historic snapshots remain readable even though these methods are no
        // longer offered at checkout.
        'BANK_TRANSFER': 'Chuyển khoản ngân hàng',
        'E_WALLET': 'Ví điện tử',
        'CREDIT_CARD': 'Thẻ tín dụng/ATM'
    };
    return methodMap[method] || method;
}

function celebrateOrder() {
    if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return;
    // Add success animation
    const successIcon = document.querySelector('.success-animation i');
    if (successIcon) {
        successIcon.style.animation = 'scaleIn 0.5s ease-out';
    }

    // Add confetti effect if you want (optional)
    // You can add a confetti library here
}

function formatPrice(price) {
    return new Intl.NumberFormat('vi-VN', { 
        style: 'currency', 
        currency: 'VND' 
    }).format(price);
}

function addCancelButton(order) {
    const actionButtons = document.getElementById('actionButtons');
    
    // Show cancel button for PENDING orders with COD payment
    if (order.status === 'PENDING' && order.paymentMethod === 'COD') {
        // Add cancel button
        const cancelBtn = document.createElement('button');
        cancelBtn.className = 'btn btn-outline-danger btn-lg mt-2 mt-md-0';
        cancelBtn.innerHTML = '<i class="fas fa-times me-2"></i>Hủy đơn hàng';
        cancelBtn.onclick = () => cancelOrder(order.id, order.orderNumber);
        
        actionButtons.insertBefore(cancelBtn, actionButtons.firstChild);
        
        // Add info alert
        const infoAlert = document.createElement('div');
        infoAlert.className = 'alert alert-warning mt-3';
        infoAlert.innerHTML = '<i class="fas fa-info-circle me-2"></i>Bạn có thể hủy đơn hàng COD trong khi đơn đang chờ xử lý.';
        actionButtons.parentElement.insertBefore(infoAlert, actionButtons);
    }
    
    // Một TxnRef VNPay chỉ được dùng một lần; không cấp lại liên kết thanh toán.
    if (isAwaitingPayment(order)) {
        const warningAlert = document.createElement('div');
        warningAlert.className = 'alert alert-warning mt-3';
        const icon = document.createElement('i');
        icon.className = 'fas fa-exclamation-triangle me-2';
        const strong = document.createElement('strong');
        strong.textContent = 'Chưa thanh toán: ';
        warningAlert.append(icon, strong, document.createTextNode(
            'BeautyStore không cấp lại liên kết VNPay cho đơn này. Nếu đã rời cổng thanh toán, hãy hủy đơn và đặt lại để nhận một giao dịch mới an toàn.'));
        actionButtons.parentElement.insertBefore(warningAlert, actionButtons);
    }
}

let currentCancelOrderId = null;
let currentCancelOrderNumber = null;

function cancelOrder(orderId, orderNumber) {
    // Store order info
    currentCancelOrderId = orderId;
    currentCancelOrderNumber = orderNumber;
    
    // Update modal content
    document.getElementById('cancelOrderNumber').textContent = orderNumber;
    
    // Show cancel confirmation modal
    const cancelModal = new bootstrap.Modal(document.getElementById('cancelOrderModal'));
    cancelModal.show();
    
    // Setup confirm button handler
    setupCancelConfirmHandler();
}

function setupCancelConfirmHandler() {
    const confirmCancelBtn = document.getElementById('confirmCancelBtn');
    if (!confirmCancelBtn) return;
    
    // Remove old listeners
    const newBtn = confirmCancelBtn.cloneNode(true);
    confirmCancelBtn.parentNode.replaceChild(newBtn, confirmCancelBtn);
    
    // Add new listener
    newBtn.addEventListener('click', async function() {
        // Hide cancel modal
        const cancelModal = bootstrap.Modal.getInstance(document.getElementById('cancelOrderModal'));
        cancelModal.hide();
        
        // Show loading state
        newBtn.disabled = true;
        newBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Đang xử lý...';
        
        await performCancelOrder();
        
        // Reset button
        newBtn.disabled = false;
        newBtn.innerHTML = '<i class="fas fa-times me-2"></i>Xác nhận hủy';
    });
}

async function performCancelOrder() {
    try {
        const token = localStorage.getItem('authToken') || localStorage.getItem('token');
        const guestToken = sessionStorage.getItem(`beautystore:order-token:${currentCancelOrderNumber}`);

        if (!token && !guestToken) {
            showErrorModal('Vui lòng xác minh OTP để hủy đơn hàng.');
            return;
        }

        const memberRequest = Boolean(token) && !currentOrderUsesGuestAccess;
        const response = await fetch(memberRequest
            ? `/api/orders/${currentCancelOrderId}/cancel`
            : `/api/guest-orders/${encodeURIComponent(currentCancelOrderNumber)}/cancel`, {
            method: memberRequest ? 'PUT' : 'POST',
            headers: memberRequest
                ? { 'Authorization': `Bearer ${token}`, 'Content-Type': 'application/json' }
                : { 'X-Order-Token': guestToken, 'Content-Type': 'application/json' },
            credentials: 'same-origin'
        });

        if (response.status === 401) {
            showErrorModal(memberRequest
                ? 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.'
                : 'Phiên xác minh đơn hàng đã hết hạn. Vui lòng xác minh OTP lại.');
            return;
        }

        const data = await response.json();

        if (!response.ok) {
            showErrorModal(data.message || data.error || 'Không thể hủy đơn hàng. Vui lòng thử lại!');
            return;
        }

        // Show success modal
        const successModal = new bootstrap.Modal(document.getElementById('successModal'));
        successModal.show();
        
        // Start countdown timer
        const countdownHint = document.querySelector('#successModal small');
        if (!memberRequest && countdownHint) {
            countdownHint.innerHTML = 'Tự động tải lại đơn hàng sau <strong><span id="countdownTimer">5</span></strong> giây...';
        }
        startCountdown(5, memberRequest ? '/orders' : window.location.href);

    } catch (error) {
        console.error('Error cancelling order:', error);
        showErrorModal('Đã xảy ra lỗi khi hủy đơn hàng. Vui lòng thử lại sau!');
    }
}

function startCountdown(seconds, destination = '/orders') {
    let timeLeft = seconds;
    const timerElement = document.getElementById('countdownTimer');
    
    const countdown = setInterval(() => {
        timeLeft--;
        if (timerElement) {
            timerElement.textContent = timeLeft;
        }
        
        if (timeLeft <= 0) {
            clearInterval(countdown);
            window.location.href = destination;
        }
    }, 1000);
}

function showErrorModal(message) {
    document.getElementById('errorMessage').textContent = message;
    const errorModal = new bootstrap.Modal(document.getElementById('errorModal'));
    errorModal.show();
}

// Add CSS animation
const style = document.createElement('style');
style.textContent = `
    @keyframes scaleIn {
        0% {
            transform: scale(0);
            opacity: 0;
        }
        50% {
            transform: scale(1.2);
        }
        100% {
            transform: scale(1);
            opacity: 1;
        }
    }
`;
document.head.appendChild(style);
