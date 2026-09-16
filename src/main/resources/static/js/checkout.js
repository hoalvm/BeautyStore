// Checkout functionality
let checkoutCartState = { totalItems: 0, unavailableItems: 0 };

document.addEventListener('DOMContentLoaded', function() {
    loadCheckoutData();
    setupFormValidation();
    setupCheckoutButton();
});

// Load cart data for checkout
async function loadCheckoutData() {
    const summaryLoading = document.getElementById('summaryLoading');
    const orderItemsList = document.getElementById('orderItemsList');

    try {
        const token = localStorage.getItem('authToken') || localStorage.getItem('token');
        // Load user profile to pre-fill form
        if (token) await loadUserProfile(token);

        const headers = { 'Content-Type': 'application/json' };
        if (token) headers.Authorization = `Bearer ${token}`;
        const response = await fetch('/api/cart', {
            headers,
            credentials: 'same-origin'
        });

        if (!response.ok) {
            throw new Error('Failed to load cart');
        }

        const cart = await response.json();

        if (!cart.items || cart.items.length === 0) {
            showNotification('Giỏ hàng trống! Đang chuyển về trang sản phẩm...', 'warning');
            setTimeout(() => {
                window.location.href = '/products';
            }, 2000);
            return;
        }

        const unavailableItems = cart.items.filter(item => item.available !== true).length;
        checkoutCartState = {
            totalItems: Math.max(0, Number(cart.totalItems) || 0),
            unavailableItems
        };

        // Hiển thị cả dòng không khả dụng để người mua biết vì sao nó bị loại.
        displayCheckoutItems(cart.items);
        updateCheckoutSummary(cart);
        updateCheckoutAvailability(unavailableItems, checkoutCartState.totalItems);

        summaryLoading.style.display = 'none';
        orderItemsList.style.display = 'block';

    } catch (error) {
        console.error('Error loading checkout data:', error);
        summaryLoading.innerHTML = `
            <div class="alert alert-danger">
                <i class="fas fa-exclamation-circle me-2"></i>
                Không thể tải thông tin giỏ hàng. Vui lòng thử lại.
            </div>
        `;
    }
}

// Load user profile to pre-fill form
async function loadUserProfile(token) {
    try {
        const response = await fetch('/api/auth/profile', {
            headers: {
                'Authorization': `Bearer ${token}`
            }
        });

        if (response.ok) {
            const profile = await response.json();
            
            // Pre-fill form
            document.getElementById('customerName').value = profile.name || '';
            document.getElementById('customerEmail').value = profile.email || '';
            document.getElementById('customerPhone').value = profile.phone || '';
            document.getElementById('shippingAddress').value = profile.address || '';
        }
    } catch (error) {
        console.error('Error loading profile:', error);
    }
}

// Display checkout items
function displayCheckoutItems(items) {
    const container = document.getElementById('itemsContainer');
    container.replaceChildren();

    items.forEach(item => {
        const available = item.available === true;
        const itemDiv = document.createElement('div');
        itemDiv.className = 'checkout-item d-flex align-items-center mb-3 pb-3 border-bottom';
        if (!available) {
            itemDiv.classList.add('border', 'border-danger', 'rounded', 'p-2', 'bg-light', 'opacity-75');
            itemDiv.setAttribute('aria-disabled', 'true');
        }
        const image = document.createElement('img');
        image.src = window.BeautyUI
            ? BeautyUI.safeUrl(item.productImageUrl)
            : (item.productImageUrl || '/images/beauty/hero-beautystore-v2.webp');
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
        quantity.textContent = `Số lượng: ${item.quantity}`;
        info.append(name, variant, quantity);
        if (!available) {
            const unavailable = document.createElement('span');
            unavailable.className = 'badge bg-danger d-table mt-1';
            unavailable.textContent = 'Không khả dụng · không tính vào đơn';
            info.appendChild(unavailable);
        }
        const total = document.createElement('div');
        total.className = 'text-end';
        const subtotal = document.createElement('p');
        subtotal.className = 'mb-0 fw-bold text-danger';
        subtotal.textContent = available ? formatPrice(item.subtotal) : 'Không tính';
        if (!available) subtotal.classList.replace('text-danger', 'text-muted');
        const unit = document.createElement('small');
        unit.className = 'text-muted';
        unit.textContent = available ? `${formatPrice(item.price)} × ${item.quantity}` : 'Vui lòng xóa tại trang giỏ hàng';
        total.append(subtotal, unit);
        itemDiv.append(image, info, total);
        container.appendChild(itemDiv);
    });
}

// Update checkout summary
function updateCheckoutSummary(cart) {
    const subtotal = cart.totalPrice || 0;
    document.getElementById('subtotal').textContent = formatPrice(subtotal);
    const summary = document.getElementById('orderSummaryContainer');
    const configuredShippingFee = Number(summary?.dataset.shippingFee || 0);
    const configuredFreeThreshold = Number(summary?.dataset.freeShippingThreshold || 0);
    const thresholdShippingFee = subtotal >= configuredFreeThreshold ? 0 : configuredShippingFee;
    
    // Load voucher info from localStorage
    const voucher = getStoredVoucherState();
    const baseShippingFee = voucher.freeShipping ? 0 : thresholdShippingFee;
    document.getElementById('shippingFee').textContent = baseShippingFee ? formatPrice(baseShippingFee) : 'Miễn phí';
    
    if (voucher.code) {
        // Display voucher
        document.getElementById('voucherDisplay').style.display = 'block';
        document.getElementById('displayVoucherCode').textContent = voucher.code;
        const label = document.getElementById('checkoutVoucherLabel');
        if (label) label.textContent = voucher.freeShipping ? 'Ưu đãi vận chuyển' : 'Mã giảm giá';
        document.getElementById('displayVoucherDiscount').textContent = voucher.freeShipping
            ? 'Miễn phí vận chuyển'
            : '- ' + formatPrice(voucher.discountAmount);
        
        // Calculate final total
        const productDiscount = voucher.freeShipping ? 0 : voucher.discountAmount;
        const finalTotal = Math.max(0, subtotal - productDiscount + baseShippingFee);
        document.getElementById('totalAmount').textContent = formatPrice(finalTotal);
    } else {
        // Hide voucher display
        document.getElementById('voucherDisplay').style.display = 'none';
        document.getElementById('totalAmount').textContent = formatPrice(subtotal + baseShippingFee);
    }
}

function updateCheckoutAvailability(unavailableItems, totalItems) {
    const alert = document.getElementById('checkoutAvailabilityAlert');
    const placeOrderBtn = document.getElementById('placeOrderBtn');
    const canCheckout = Number(totalItems) > 0 && Number(unavailableItems) === 0;

    if (placeOrderBtn) {
        placeOrderBtn.disabled = !canCheckout;
        placeOrderBtn.dataset.availableItems = String(Math.max(0, Number(totalItems) || 0));
    }
    if (!alert) return;

    if (unavailableItems > 0) {
        alert.hidden = false;
        alert.className = 'alert alert-danger py-2 small';
        alert.textContent = Number(totalItems) > 0
            ? `${unavailableItems} sản phẩm không khả dụng không được tính vào tạm tính. Vui lòng quay lại giỏ hàng để xóa hoặc thay sản phẩm trước khi đặt hàng.`
            : 'Không còn sản phẩm khả dụng để đặt hàng. Vui lòng quay lại giỏ hàng để xóa hoặc thay sản phẩm.';
    } else {
        alert.hidden = true;
        alert.textContent = '';
    }
}

// Setup form validation
function setupFormValidation() {
    const form = document.getElementById('checkoutForm');
    
    // Add Bootstrap validation classes
    const inputs = form.querySelectorAll('input[required], textarea[required]');
    inputs.forEach(input => {
        input.addEventListener('blur', function() {
            if (this.checkValidity()) {
                this.classList.remove('is-invalid');
                this.classList.add('is-valid');
            } else {
                this.classList.remove('is-valid');
                this.classList.add('is-invalid');
            }
        });
    });

    // Phone validation
    const phoneInput = document.getElementById('customerPhone');
    phoneInput.addEventListener('input', function() {
        this.value = this.value.replace(/[^0-9]/g, '');
    });
}

// Setup checkout button
function setupCheckoutButton() {
    const placeOrderBtn = document.getElementById('placeOrderBtn');
    
    placeOrderBtn.addEventListener('click', async function() {
        const form = document.getElementById('checkoutForm');

        if (checkoutCartState.totalItems <= 0 || checkoutCartState.unavailableItems > 0) {
            showNotification('Vui lòng xử lý các sản phẩm không khả dụng trong giỏ hàng trước khi đặt.', 'warning');
            return;
        }
        
        // Validate form
        if (!form.checkValidity()) {
            form.classList.add('was-validated');
            showNotification('Vui lòng điền đầy đủ thông tin!', 'warning');
            
            // Scroll to first invalid field
            const firstInvalid = form.querySelector(':invalid');
            if (firstInvalid) {
                firstInvalid.scrollIntoView({ behavior: 'smooth', block: 'center' });
                firstInvalid.focus();
            }
            return;
        }

        // Get form data
        const checkoutData = {
            customerName: document.getElementById('customerName').value.trim(),
            customerEmail: document.getElementById('customerEmail').value.trim(),
            customerPhone: document.getElementById('customerPhone').value.trim(),
            addressLine: document.getElementById('shippingAddress').value.trim(),
            province: document.getElementById('shippingProvince').value.trim(),
            district: document.getElementById('shippingDistrict').value.trim(),
            ward: document.getElementById('shippingWard').value.trim(),
            paymentMethod: document.querySelector('input[name="paymentMethod"]:checked').value,
            notes: document.getElementById('notes').value.trim(),
            voucherCode: localStorage.getItem('voucherCode') || null
        };

        // Disable button and show loading
        placeOrderBtn.disabled = true;
        placeOrderBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Đang xử lý...';

        try {
            const token = localStorage.getItem('authToken') || localStorage.getItem('token');
            const headers = { 'Content-Type': 'application/json' };
            if (token) headers.Authorization = `Bearer ${token}`;
            const response = await fetch('/api/orders/checkout', {
                method: 'POST',
                headers,
                credentials: 'same-origin',
                body: JSON.stringify(checkoutData)
            });

            if (response.status === 401) {
                showNotification('Phiên đăng nhập đã hết hạn!', 'warning');
                setTimeout(() => {
                    window.location.href = '/login';
                }, 1500);
                return;
            }

            if (!response.ok) {
                const error = await response.json();
                throw new Error(error.message || error.error || 'Đặt hàng thất bại');
            }

            const result = await response.json();
            // Check if need to redirect to VNPay
            if (result.redirectToPayment && result.paymentUrl) {
                const order = result.order;
                if (order?.orderNumber) {
                    sessionStorage.setItem('beautystore:last-order', JSON.stringify(order));
                }
                
                // Clear voucher from localStorage
                clearStoredCheckoutState();
                
                // Show message and redirect to VNPay
                showNotification('Đang chuyển đến cổng thanh toán VNPay...', 'info');
                
                setTimeout(() => {
                    window.location.href = result.paymentUrl;
                }, 1000);
                return;
            }
            
            // For COD payment
            const order = result.order || result;
            if (!order.orderNumber) {
                throw new Error('Order number not returned from server');
            }
            sessionStorage.setItem('beautystore:last-order', JSON.stringify(order));
            
            // Clear voucher from localStorage after successful order
            clearStoredCheckoutState();
            
            // Show success and redirect
            showNotification('Đặt hàng thành công! Đang chuyển hướng...', 'success');
            
            setTimeout(() => {
                window.location.href = `/order-confirmation/${order.orderNumber}`;
            }, 1500);

        } catch (error) {
            console.error('Error placing order:', error);
            showNotification(error.message || 'Đặt hàng thất bại. Vui lòng thử lại!', 'danger');
            
            // Re-enable button
            placeOrderBtn.disabled = checkoutCartState.totalItems <= 0
                || checkoutCartState.unavailableItems > 0;
            placeOrderBtn.innerHTML = '<i class="fas fa-check-circle me-2"></i>Đặt hàng ngay';
        }
    });
}

function getStoredVoucherState() {
    const code = (localStorage.getItem('voucherCode') || '').trim();
    const discountAmount = Math.max(0, parseFloat(localStorage.getItem('voucherDiscount')) || 0);
    const explicitFreeShipping = localStorage.getItem('voucherFreeShipping');
    const type = localStorage.getItem('voucherType');
    const freeShipping = Boolean(code) && (explicitFreeShipping === 'true'
        || type === 'FREE_SHIPPING'
        || (explicitFreeShipping === null && type === null && discountAmount === 0));
    return { code, discountAmount, freeShipping };
}

function clearStoredCheckoutState() {
    ['voucherCode', 'voucherDiscount', 'voucherFreeShipping', 'voucherType',
        'voucherShippingDiscount', 'cartSubtotal'].forEach(key => localStorage.removeItem(key));
}

// Format price
function formatPrice(price) {
    return new Intl.NumberFormat('vi-VN', { 
        style: 'currency', 
        currency: 'VND' 
    }).format(price);
}

// Show notification
function showNotification(message, type = 'success') {
    const toastDiv = document.createElement('div');
    toastDiv.className = `alert alert-${type} position-fixed top-0 start-50 translate-middle-x mt-3`;
    toastDiv.style.zIndex = '9999';
    toastDiv.style.minWidth = '300px';
    const icon = document.createElement('i');
    icon.className = `fas fa-${type === 'success' ? 'check-circle' : type === 'warning' ? 'exclamation-triangle' : 'exclamation-circle'} me-2`;
    toastDiv.append(icon, document.createTextNode(String(message ?? '')));
    document.body.appendChild(toastDiv);
    
    setTimeout(() => {
        toastDiv.style.opacity = '0';
        toastDiv.style.transition = 'opacity 0.5s';
        setTimeout(() => toastDiv.remove(), 500);
    }, 3000);
}
