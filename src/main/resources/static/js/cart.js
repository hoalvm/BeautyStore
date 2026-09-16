// Cart functionality
document.addEventListener('DOMContentLoaded', function() {
    loadCart();

    // Clear cart button
    const clearCartBtn = document.getElementById('clearCartBtn');
    if (clearCartBtn) {
        clearCartBtn.addEventListener('click', clearCart);
    }

    // Checkout button
    const checkoutBtn = document.getElementById('checkoutBtn');
    if (checkoutBtn) {
        checkoutBtn.addEventListener('click', function() {
            if (checkoutBtn.disabled || Number(checkoutBtn.dataset.availableItems || 0) <= 0) {
                showNotification('Giỏ hàng chưa có sản phẩm khả dụng để thanh toán.', 'warning');
                return;
            }
            window.location.href = '/checkout';
        });
    }
});

// Load cart from API
async function loadCart() {
    const cartLoading = document.getElementById('cartLoading');
    const emptyCartMessage = document.getElementById('emptyCartMessage');
    const cartItemsList = document.getElementById('cartItemsList');
    const checkoutBtn = document.getElementById('checkoutBtn');
    const clearCartBtn = document.getElementById('clearCartBtn');

    // Show loading
    if (cartLoading) cartLoading.style.display = 'block';

    try {
        const token = localStorage.getItem('authToken') || localStorage.getItem('token');
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

        // Hide loading
        if (cartLoading) cartLoading.style.display = 'none';

        if (cart.items && cart.items.length > 0) {
            // Show cart items
            if (emptyCartMessage) emptyCartMessage.style.display = 'none';
            cartItemsList.replaceChildren();
            
            cart.items.forEach(item => {
                const itemElement = createCartItemElement(item);
                cartItemsList.appendChild(itemElement);
            });

            // Update summary
            updateCartSummary(cart);

            // Chỉ sản phẩm available=true được backend tính vào totalItems/totalPrice.
            const availableItems = Math.max(0, Number(cart.totalItems) || 0);
            const hasUnavailableItems = cart.items.some(item => item.available !== true);
            const canCheckout = availableItems > 0 && !hasUnavailableItems;
            if (checkoutBtn) {
                checkoutBtn.disabled = !canCheckout;
                checkoutBtn.dataset.availableItems = String(availableItems);
                checkoutBtn.title = hasUnavailableItems
                    ? 'Vui lòng xóa hoặc thay sản phẩm không khả dụng trước khi thanh toán'
                    : (availableItems > 0 ? '' : 'Không có sản phẩm khả dụng để thanh toán');
            }
            if (clearCartBtn) clearCartBtn.disabled = false;
        } else {
            // Show empty cart message
            emptyCartMessage.style.display = 'block';
            cartItemsList.replaceChildren();
            
            // Update summary to zero
            document.getElementById('totalItems').textContent = '0';
            document.getElementById('totalPrice').textContent = '0 ₫';

            // Disable buttons
            if (checkoutBtn) {
                checkoutBtn.disabled = true;
                checkoutBtn.dataset.availableItems = '0';
            }
            if (clearCartBtn) clearCartBtn.disabled = true;
        }
    } catch (error) {
        console.error('Error loading cart:', error);
        if (cartLoading) {
            cartLoading.innerHTML = `
            <div class="alert alert-danger">
                <i class="fas fa-exclamation-circle me-2"></i>
                Không thể tải giỏ hàng. Vui lòng thử lại sau.
            </div>
        `;
        }
    }
}

// Create cart item element
function createCartItemElement(item) {
    const template = document.getElementById('cartItemTemplate');
    const clone = template.content.cloneNode(true);

    const cartItemDiv = clone.querySelector('.cart-item');
    cartItemDiv.setAttribute('data-item-id', item.id);

    // Set image
    const img = clone.querySelector('.cart-item-image');
    img.src = window.BeautyUI?.safeUrl(item.productImageUrl)
        || '/images/beauty/hero-beautystore-v2.webp';
    img.alt = item.productName;

    // Set product name
    clone.querySelector('.cart-item-name').textContent = item.productName;
    const variantText = [item.variantLabel, item.shadeName, item.netContent].filter(Boolean).join(' · ');
    clone.querySelector('.cart-item-variant').textContent = variantText;
    clone.querySelector('.cart-item-variant').hidden = !variantText;

    // Set price
    clone.querySelector('.cart-item-price').textContent = formatPrice(item.price);

    // Set stock
    const availableStock = Math.max(0, Number(item.availableStock) || 0);
    clone.querySelector('.cart-item-stock').textContent = String(availableStock);

    // Set quantity
    const quantityInput = clone.querySelector('.quantity-input');
    quantityInput.value = item.quantity;
    quantityInput.max = String(availableStock);

    // Set subtotal
    clone.querySelector('.cart-item-subtotal').textContent = formatPrice(item.subtotal);

    // Add event listeners for quantity buttons
    const decreaseBtn = clone.querySelector('.quantity-decrease');
    const increaseBtn = clone.querySelector('.quantity-increase');
    const removeBtn = clone.querySelector('.remove-item');

    if (item.available !== true) {
        cartItemDiv.classList.add('border-danger', 'bg-light', 'opacity-75');
        cartItemDiv.dataset.available = 'false';
        quantityInput.disabled = true;
        decreaseBtn.disabled = true;
        increaseBtn.disabled = true;
        const availability = clone.querySelector('.cart-item-availability');
        if (availability) {
            availability.hidden = false;
            availability.textContent = availableStock > 0
                ? `Không đủ tồn kho cho số lượng đã chọn (hiện còn ${availableStock}). Hãy xóa và thêm lại số lượng phù hợp.`
                : 'Sản phẩm hoặc phiên bản này hiện không còn khả dụng và không được tính vào đơn hàng.';
        }
        const subtotal = clone.querySelector('.cart-item-subtotal');
        subtotal.textContent = 'Không tính';
        subtotal.classList.remove('text-success');
        subtotal.classList.add('text-danger');
    }

    decreaseBtn.addEventListener('click', () => {
        if (parseInt(quantityInput.value) > 1) {
            quantityInput.value = parseInt(quantityInput.value) - 1;
            updateCartItemQuantity(item.id, parseInt(quantityInput.value));
        }
    });

    increaseBtn.addEventListener('click', () => {
        if (parseInt(quantityInput.value) < availableStock) {
            quantityInput.value = parseInt(quantityInput.value) + 1;
            updateCartItemQuantity(item.id, parseInt(quantityInput.value));
        } else {
            showNotification('Không đủ số lượng trong kho!', 'warning');
        }
    });

    quantityInput.addEventListener('change', () => {
        let value = parseInt(quantityInput.value);
        if (value < 1) value = 1;
        if (value > availableStock) {
            value = availableStock;
            showNotification('Không đủ số lượng trong kho!', 'warning');
        }
        quantityInput.value = value;
        updateCartItemQuantity(item.id, value);
    });

    removeBtn.addEventListener('click', () => {
        removeCartItem(item.id);
    });

    return clone;
}

// Update cart item quantity
async function updateCartItemQuantity(itemId, quantity) {
    try {
        const token = localStorage.getItem('authToken') || localStorage.getItem('token');
        const headers = { 'Content-Type': 'application/json' };
        if (token) headers.Authorization = `Bearer ${token}`;
        const response = await fetch(`/api/cart/items/${itemId}`, {
            method: 'PUT',
            headers,
            credentials: 'same-origin',
            body: JSON.stringify({ quantity: quantity })
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || error.error || 'Không thể cập nhật sản phẩm');
        }

        const cart = await response.json();
        
        // Update the specific item's subtotal
        const itemElement = document.querySelector(`[data-item-id="${itemId}"]`);
        if (itemElement) {
            const itemData = cart.items.find(i => i.id === itemId);
            if (itemData) {
                itemElement.querySelector('.cart-item-subtotal').textContent = formatPrice(itemData.subtotal);
            }
        }

        // Update summary
        updateCartSummary(cart);
        showNotification('Đã cập nhật số lượng!', 'success');
        
        // Update cart badge
        if (typeof updateCartBadge === 'function') {
            updateCartBadge();
        }
    } catch (error) {
        console.error('Error updating cart item:', error);
        showNotification(error.message || 'Không thể cập nhật số lượng!', 'error');
        // Reload cart to reset values
        loadCart();
    }
}

// Remove cart item
async function removeCartItem(itemId) {
    console.log('Attempting to remove item with ID:', itemId);

    try {
        const token = localStorage.getItem('authToken') || localStorage.getItem('token');
        const headers = { 'Content-Type': 'application/json' };
        if (token) headers.Authorization = `Bearer ${token}`;
        console.log('Sending DELETE request to:', `/api/cart/items/${itemId}`);
        const response = await fetch(`/api/cart/items/${itemId}`, {
            method: 'DELETE',
            headers,
            credentials: 'same-origin'
        });

        console.log('Response status:', response.status);
        
        if (!response.ok) {
            const error = await response.json();
            console.error('Error response:', error);
            throw new Error(error.message || error.error || 'Không thể xóa sản phẩm');
        }

        console.log('Successfully removed item');
        showNotification('Đã xóa sản phẩm khỏi giỏ hàng!', 'success');
        
        // Update cart badge immediately
        if (typeof updateCartBadge === 'function') {
            console.log('Updating cart badge...');
            updateCartBadge();
        }
        
        // Reload cart
        console.log('Reloading cart...');
        loadCart();
    } catch (error) {
        console.error('Error removing cart item:', error);
        showNotification(error.message || 'Không thể xóa sản phẩm!', 'error');
    }
}

// Clear entire cart
async function clearCart() {
    try {
        const token = localStorage.getItem('authToken') || localStorage.getItem('token');
        const headers = { 'Content-Type': 'application/json' };
        if (token) headers.Authorization = `Bearer ${token}`;
        const response = await fetch('/api/cart/clear', {
            method: 'DELETE',
            headers,
            credentials: 'same-origin'
        });


        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || error.error || 'Không thể xóa giỏ hàng');
        }

        showNotification('Đã xóa toàn bộ giỏ hàng!', 'success');
        
        // Reset cart badge immediately
        if (typeof updateCartBadge === 'function') {
            updateCartBadge();
        }
        
        // Reload cart
        loadCart();
    } catch (error) {
        console.error('Error clearing cart:', error);
        showNotification(error.message || 'Không thể xóa giỏ hàng!', 'error');
    }
}

// Update cart summary
function updateCartSummary(cart) {
    const subtotal = cart.totalPrice || 0;
    document.getElementById('totalItems').textContent = cart.totalItems || 0;
    document.getElementById('subtotalPrice').textContent = formatPrice(subtotal);
    
    // Calculate final total with voucher discount
    const voucherDiscount = getStoredVoucherState().freeShipping
        ? 0
        : getStoredVoucherState().discountAmount;
    const finalTotal = Math.max(0, subtotal - voucherDiscount);
    
    document.getElementById('totalPrice').textContent = formatPrice(finalTotal);
    
    // Store subtotal for voucher validation
    localStorage.setItem('cartSubtotal', subtotal);
    
    // Update cart badge in header if function exists
    if (typeof updateCartBadge === 'function') {
        updateCartBadge();
    }
    
    // Setup voucher button handlers
    setupVoucherHandlers();
    
    // Display applied voucher if exists
    displayAppliedVoucher();
}

// Format price with Vietnamese currency
function formatPrice(price) {
    if (price === null || price === undefined) return '0 ₫';
    return new Intl.NumberFormat('vi-VN', {
        style: 'currency',
        currency: 'VND'
    }).format(price);
}

// Show notification
function showNotification(message, type = 'info') {
    // Create notification element
    const notification = document.createElement('div');
    notification.className = `alert alert-${type === 'error' ? 'danger' : type === 'success' ? 'success' : 'warning'} alert-dismissible fade show position-fixed`;
    notification.style.cssText = 'top: 80px; right: 20px; z-index: 9999; min-width: 300px;';
    notification.textContent = String(message || '');
    const close = document.createElement('button');
    close.type = 'button';
    close.className = 'btn-close ms-3';
    close.setAttribute('aria-label', 'Đóng');
    close.addEventListener('click', () => notification.remove());
    notification.appendChild(close);

    document.body.appendChild(notification);

    // Auto remove after 3 seconds
    setTimeout(() => {
        notification.remove();
    }, 3000);
}

// Add to cart function (to be used from product pages)
async function addToCart(variantId, quantity = 1) {
    try {
        const token = localStorage.getItem('authToken') || localStorage.getItem('token');
        const headers = { 'Content-Type': 'application/json' };
        if (token) headers.Authorization = `Bearer ${token}`;
        const response = await fetch('/api/cart/add', {
            method: 'POST',
            headers,
            credentials: 'same-origin',
            body: JSON.stringify({
                variantId: variantId,
                quantity: quantity
            })
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error.message || error.error || 'Không thể thêm vào giỏ hàng');
        }

        showNotification('Đã thêm vào giỏ hàng!', 'success');
        
        // Update cart badge in header if function exists
        if (typeof updateCartBadge === 'function') {
            updateCartBadge();
        }
        
        return true;
    } catch (error) {
        console.error('Error adding to cart:', error);
        showNotification(error.message || 'Không thể thêm vào giỏ hàng!', 'error');
        return false;
    }
}

// ==================== VOUCHER FUNCTIONS ====================

function setupVoucherHandlers() {
    const applyBtn = document.getElementById('applyVoucherBtn');
    const removeBtn = document.getElementById('removeVoucherBtn');
    const voucherInput = document.getElementById('voucherCodeInput');
    
    if (applyBtn && !applyBtn.hasAttribute('data-handler-attached')) {
        applyBtn.addEventListener('click', applyVoucher);
        applyBtn.setAttribute('data-handler-attached', 'true');
    }
    
    if (removeBtn && !removeBtn.hasAttribute('data-handler-attached')) {
        removeBtn.addEventListener('click', removeVoucher);
        removeBtn.setAttribute('data-handler-attached', 'true');
    }
    
    if (voucherInput && !voucherInput.hasAttribute('data-handler-attached')) {
        voucherInput.addEventListener('keypress', function(e) {
            if (e.key === 'Enter') {
                applyVoucher();
            }
        });
        voucherInput.setAttribute('data-handler-attached', 'true');
    }
}

async function applyVoucher() {
    const voucherInput = document.getElementById('voucherCodeInput');
    const voucherCode = voucherInput.value.trim().toUpperCase();
    if (!voucherCode) {
        showVoucherMessage('Vui lòng nhập mã giảm giá', 'danger');
        return;
    }
    
    // Get cart subtotal
    const subtotal = parseFloat(localStorage.getItem('cartSubtotal')) || 0;
    
    if (subtotal <= 0) {
        showVoucherMessage('Giỏ hàng trống', 'danger');
        return;
    }
    
    try {
        const token = localStorage.getItem('authToken') || localStorage.getItem('token');
        const headers = { 'Content-Type': 'application/json' };
        if (token) headers.Authorization = `Bearer ${token}`;
        const params = new URLSearchParams({
            code: voucherCode,
            orderTotal: String(subtotal)
        });
        const response = await fetch(`/api/vouchers/validate?${params.toString()}`, {
            method: 'POST',
            headers,
            credentials: 'same-origin'
        });

        const result = await response.json().catch(() => ({}));
        if (!response.ok) {
            throw new Error(result.message || 'Không thể kiểm tra mã giảm giá');
        }
        
        if (result.valid) {
            const discountAmount = Math.max(0, Number(result.discountAmount) || 0);
            // API hiện tại trả discountAmount=0 cho FREE_SHIPPING. Các tên field
            // bổ sung dưới đây giúp client tương thích khi API cung cấp metadata rõ hơn.
            const freeShipping = result.freeShipping === true
                || result.discountType === 'FREE_SHIPPING'
                || result.voucherType === 'FREE_SHIPPING'
                || Number(result.shippingDiscount) > 0
                || discountAmount === 0;
            // Save voucher info to localStorage
            localStorage.setItem('voucherCode', result.voucherCode || voucherCode);
            localStorage.setItem('voucherDiscount', String(discountAmount));
            localStorage.setItem('voucherFreeShipping', String(freeShipping));
            localStorage.setItem('voucherType', freeShipping ? 'FREE_SHIPPING' : 'DISCOUNT');
            if (result.shippingDiscount != null) {
                localStorage.setItem('voucherShippingDiscount', String(Math.max(0, Number(result.shippingDiscount) || 0)));
            } else {
                localStorage.removeItem('voucherShippingDiscount');
            }
            
            // Update display
            displayAppliedVoucher();
            updateTotalPrice();
            
            showVoucherMessage(result.message || 'Áp dụng mã giảm giá thành công!', 'success');
            
            // Hide input, show applied voucher
            document.getElementById('voucherInputGroup').style.display = 'none';
            document.getElementById('appliedVoucherGroup').style.display = 'block';
        } else {
            showVoucherMessage(result.message || 'Mã giảm giá không hợp lệ', 'danger');
        }
    } catch (error) {
        console.error('Error validating voucher:', error);
        showVoucherMessage(error.message || 'Lỗi kết nối máy chủ', 'danger');
    }
}

function removeVoucher() {
    // Clear voucher from localStorage
    localStorage.removeItem('voucherCode');
    localStorage.removeItem('voucherDiscount');
    localStorage.removeItem('voucherFreeShipping');
    localStorage.removeItem('voucherType');
    localStorage.removeItem('voucherShippingDiscount');
    
    // Hide applied voucher, show input
    document.getElementById('appliedVoucherGroup').style.display = 'none';
    document.getElementById('voucherInputGroup').style.display = 'block';
    document.getElementById('voucherCodeInput').value = '';
    
    // Update total price
    updateTotalPrice();
    
    showNotification('Đã xóa mã giảm giá', 'info');
}

function displayAppliedVoucher() {
    const voucher = getStoredVoucherState();
    
    if (voucher.code) {
        document.getElementById('appliedVoucherCode').textContent = voucher.code;
        const label = document.getElementById('cartVoucherLabel');
        if (label) label.textContent = voucher.freeShipping ? 'Ưu đãi vận chuyển:' : 'Giảm giá:';
        document.getElementById('voucherDiscount').textContent = voucher.freeShipping
            ? 'Miễn phí vận chuyển'
            : '- ' + formatPrice(voucher.discountAmount);
        document.getElementById('voucherInputGroup').style.display = 'none';
        document.getElementById('appliedVoucherGroup').style.display = 'block';
    } else {
        document.getElementById('voucherInputGroup').style.display = 'block';
        document.getElementById('appliedVoucherGroup').style.display = 'none';
    }
}

function updateTotalPrice() {
    const subtotal = parseFloat(localStorage.getItem('cartSubtotal')) || 0;
    const voucher = getStoredVoucherState();
    const voucherDiscount = voucher.freeShipping ? 0 : voucher.discountAmount;
    const finalTotal = Math.max(0, subtotal - voucherDiscount);
    
    document.getElementById('totalPrice').textContent = formatPrice(finalTotal);
}

function getStoredVoucherState() {
    const code = (localStorage.getItem('voucherCode') || '').trim();
    const discountAmount = Math.max(0, parseFloat(localStorage.getItem('voucherDiscount')) || 0);
    const explicitFreeShipping = localStorage.getItem('voucherFreeShipping');
    const type = localStorage.getItem('voucherType');
    // Mã hợp lệ có discount=0 từ API hiện tại chính là FREE_SHIPPING.
    const freeShipping = Boolean(code) && (explicitFreeShipping === 'true'
        || type === 'FREE_SHIPPING'
        || (explicitFreeShipping === null && type === null && discountAmount === 0));
    return { code, discountAmount, freeShipping };
}

function showVoucherMessage(message, type) {
    const messageDiv = document.getElementById('voucherMessage');
    messageDiv.className = `alert alert-${type} py-1 px-2 small mb-0`;
    messageDiv.textContent = message;
    messageDiv.style.display = 'block';
    
    setTimeout(() => {
        messageDiv.style.display = 'none';
    }, 5000);
}
