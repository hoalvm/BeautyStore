'use strict';

let isEditMode = false;
let voucherId = null;

document.addEventListener('DOMContentLoaded', async () => {
    if (!checkAdminAuth()) return;
    setupFormHandlers();
    detectEditMode();
    if (!isEditMode) setDefaultDates();
    try {
        await loadScopeOptions();
        if (isEditMode) await loadVoucherData(voucherId);
    } catch (error) {
        showAlert(error.message || 'Không thể tải dữ liệu biểu mẫu.', 'danger');
    }
});

function checkAdminAuth() {
    const token = authToken();
    const role = localStorage.getItem('userRole') || '';
    if (!token || !role.includes('ADMIN')) {
        location.href = '/login?error=access_denied';
        return false;
    }
    const name = localStorage.getItem('userName');
    const email = localStorage.getItem('userEmail') || localStorage.getItem('authEmail');
    if (name) document.getElementById('adminName').textContent = name;
    if (email) document.getElementById('adminEmail').textContent = email;
    return true;
}

function authToken() { return localStorage.getItem('authToken') || localStorage.getItem('token'); }
function headers() { return { Authorization: `Bearer ${authToken()}`, 'Content-Type': 'application/json' }; }

function setupFormHandlers() {
    document.querySelectorAll('input[name="discountType"]').forEach(input =>
        input.addEventListener('change', event => updateDiscountControls(event.target.value)));
    document.getElementById('productIds').addEventListener('change', loadVariantsForSelectedProducts);
    document.getElementById('voucherForm').addEventListener('submit', handleSubmit);
}

function updateDiscountControls(type) {
    const valueLabel = document.getElementById('valueLabel');
    const value = document.getElementById('discountValue');
    const maxGroup = document.getElementById('maxDiscountGroup');
    valueLabel.replaceChildren(document.createTextNode(type === 'PERCENTAGE' ? 'Giá trị giảm (%) ' : type === 'FIXED_AMOUNT' ? 'Số tiền giảm (VNĐ) ' : 'Giá trị quy ước '));
    if (type !== 'FREE_SHIPPING') {
        const required = document.createElement('span');
        required.className = 'text-danger';
        required.textContent = '*';
        valueLabel.appendChild(required);
    }
    if (type === 'PERCENTAGE') {
        value.placeholder = 'VD: 15'; value.max = '100'; value.min = '0.01'; value.required = true; maxGroup.style.display = '';
    } else if (type === 'FIXED_AMOUNT') {
        value.placeholder = 'VD: 50000'; value.removeAttribute('max'); value.min = '1'; value.required = true; maxGroup.style.display = 'none';
    } else {
        value.value = '0'; value.placeholder = '0'; value.removeAttribute('max'); value.min = '0'; value.required = false; maxGroup.style.display = 'none';
    }
}

function detectEditMode() {
    const match = location.pathname.match(/\/admin\/vouchers\/edit\/(\d+)/);
    if (!match) return;
    isEditMode = true;
    voucherId = Number(match[1]);
    document.getElementById('pageTitle').textContent = '🎫 Chỉnh sửa mã giảm giá';
    document.getElementById('submitBtn').textContent = 'Cập nhật';
}

async function loadScopeOptions() {
    const [brands, categories, products] = await Promise.all([
        getJson('/api/admin/brands'),
        getJson('/api/admin/categories'),
        loadAllProducts()
    ]);
    fillSelect('brandIds', brands, item => item.name || `Thương hiệu #${item.id}`);
    fillSelect('categoryIds', categories, item => `${item.parentId ? '↳ ' : ''}${item.name || `Danh mục #${item.id}`}`);
    fillSelect('productIds', products, item => `${item.brandName ? `${item.brandName} · ` : ''}${item.name || `Sản phẩm #${item.id}`}`);
    document.getElementById('scopeHelp').textContent = 'Đã tải phạm vi. Không chọn mục nào để áp dụng cho toàn catalog.';
}

async function loadAllProducts() {
    const products = [];
    let page = 0;
    let totalPages = 1;
    while (page < totalPages && page < 50) {
        const payload = await getJson(`/api/admin/products?page=${page}&size=100&includeInactive=false`);
        products.push(...(payload.content || []));
        totalPages = Number(payload.totalPages || 0);
        page += 1;
    }
    return products;
}

async function loadVariantsForSelectedProducts() {
    const select = document.getElementById('variantIds');
    const preserved = selectedIds(select);
    select.replaceChildren();
    const productIds = selectedIds(document.getElementById('productIds'));
    const responses = await Promise.all(productIds.map(id => getJson(`/api/admin/products/${id}/variants`)));
    responses.flat().forEach(variant => appendVariantOption(variant));
    setSelected(select, preserved);
}

async function ensureVariantOptions(ids) {
    const wanted = [...new Set(ids.map(Number).filter(Number.isSafeInteger))];
    const existing = new Set([...document.getElementById('variantIds').options].map(option => Number(option.value)));
    const missing = wanted.filter(id => !existing.has(id));
    const variants = await Promise.all(missing.map(id => getJson(`/api/admin/variants/${id}`)));
    variants.forEach(appendVariantOption);
}

function appendVariantOption(variant) {
    const option = document.createElement('option');
    option.value = String(variant.id);
    option.textContent = [variant.sku, variant.label || variant.shadeName, variant.sizeValue && variant.sizeUnit ? `${variant.sizeValue} ${variant.sizeUnit}` : null].filter(Boolean).join(' · ') || `Biến thể #${variant.id}`;
    document.getElementById('variantIds').appendChild(option);
}

function fillSelect(id, values, label) {
    const select = document.getElementById(id);
    select.replaceChildren();
    values.forEach(value => {
        const option = document.createElement('option');
        option.value = String(value.id);
        option.textContent = label(value);
        select.appendChild(option);
    });
}

async function loadVoucherData(id) {
    const voucher = await getJson(`/api/admin/vouchers/${id}`);
    document.getElementById('code').value = voucher.code || '';
    document.getElementById('description').value = voucher.description || '';
    document.getElementById('active').checked = voucher.active !== false;
    const type = ['PERCENTAGE', 'FIXED_AMOUNT', 'FREE_SHIPPING'].includes(voucher.discountType) ? voucher.discountType : 'PERCENTAGE';
    const radio = document.querySelector(`input[name="discountType"][value="${type}"]`);
    if (radio) radio.checked = true;
    updateDiscountControls(type);
    document.getElementById('discountValue').value = voucher.discountValue ?? (type === 'FREE_SHIPPING' ? 0 : '');
    document.getElementById('maxDiscount').value = voucher.maxDiscount ?? '';
    document.getElementById('minOrderValue').value = voucher.minOrderValue ?? '';
    document.getElementById('limitPerUser').value = voucher.limitPerUser ?? '';
    document.getElementById('totalQuantity').value = voucher.totalQuantity ?? '';
    document.getElementById('startDate').value = formatDateTimeLocal(voucher.startDate);
    document.getElementById('endDate').value = formatDateTimeLocal(voucher.endDate);
    setSelected(document.getElementById('brandIds'), voucher.brandIds || []);
    setSelected(document.getElementById('categoryIds'), voucher.categoryIds || []);
    setSelected(document.getElementById('productIds'), voucher.productIds || []);
    await loadVariantsForSelectedProducts();
    await ensureVariantOptions(voucher.variantIds || []);
    setSelected(document.getElementById('variantIds'), voucher.variantIds || []);
}

async function handleSubmit(event) {
    event.preventDefault();
    const startDate = document.getElementById('startDate').value;
    const endDate = document.getElementById('endDate').value;
    if (!startDate || !endDate || new Date(endDate) <= new Date(startDate)) {
        showAlert('Ngày kết thúc phải sau ngày bắt đầu.', 'warning');
        return;
    }
    const discountType = document.querySelector('input[name="discountType"]:checked').value;
    const discountValue = numberOrNull('discountValue') ?? 0;
    if (discountType === 'PERCENTAGE' && (discountValue <= 0 || discountValue > 100)) {
        showAlert('Phần trăm giảm phải lớn hơn 0 và không vượt quá 100.', 'warning');
        return;
    }
    if (discountType === 'FIXED_AMOUNT' && discountValue <= 0) {
        showAlert('Số tiền giảm phải lớn hơn 0.', 'warning');
        return;
    }
    const payload = {
        code: document.getElementById('code').value.toUpperCase().trim(),
        description: document.getElementById('description').value.trim(),
        discountType,
        discountValue,
        maxDiscount: numberOrNull('maxDiscount'),
        minOrderValue: numberOrNull('minOrderValue'),
        totalQuantity: Number(document.getElementById('totalQuantity').value),
        limitPerUser: numberOrNull('limitPerUser'),
        startDate,
        endDate,
        active: document.getElementById('active').checked,
        brandIds: selectedIds(document.getElementById('brandIds')),
        categoryIds: selectedIds(document.getElementById('categoryIds')),
        productIds: selectedIds(document.getElementById('productIds')),
        variantIds: selectedIds(document.getElementById('variantIds'))
    };
    const button = document.getElementById('submitBtn');
    button.disabled = true;
    button.textContent = 'Đang xử lý…';
    try {
        await getJson(isEditMode ? `/api/admin/vouchers/${voucherId}` : '/api/admin/vouchers', {
            method: isEditMode ? 'PUT' : 'POST', headers: headers(), body: JSON.stringify(payload)
        });
        showAlert(isEditMode ? 'Đã cập nhật mã giảm giá.' : 'Đã tạo mã giảm giá.', 'success');
        setTimeout(() => { location.href = '/admin/vouchers'; }, 900);
    } catch (error) {
        showAlert(error.message || 'Không thể lưu mã giảm giá.', 'danger');
        button.disabled = false;
        button.textContent = isEditMode ? 'Cập nhật' : 'Lưu mã giảm giá';
    }
}

async function getJson(url, options = {}) {
    const response = await fetch(url, { headers: options.headers || headers(), ...options });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(payload.message || 'Yêu cầu không thành công.');
    return payload;
}

function selectedIds(select) { return [...select.selectedOptions].map(option => Number(option.value)).filter(Number.isSafeInteger); }
function setSelected(select, ids) { const wanted = new Set(ids.map(Number)); [...select.options].forEach(option => { option.selected = wanted.has(Number(option.value)); }); }
function numberOrNull(id) { const value = document.getElementById(id).value.trim(); return value === '' ? null : Number(value); }
function formatDateTimeLocal(value) { if (!value) return ''; const date = new Date(value); if (Number.isNaN(date.getTime())) return ''; const offset = date.getTimezoneOffset() * 60000; return new Date(date.getTime() - offset).toISOString().slice(0, 16); }
function setDefaultDates() { const start = new Date(Date.now() + 86400000); const end = new Date(); end.setMonth(end.getMonth() + 1); document.getElementById('startDate').value = formatDateTimeLocal(start); document.getElementById('endDate').value = formatDateTimeLocal(end); }
function generateCode() { const alphabet='ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'; const bytes=new Uint32Array(8); crypto.getRandomValues(bytes); document.getElementById('code').value=[...bytes].map(value=>alphabet[value%alphabet.length]).join(''); }
function showAlert(message, type='info') { const tone=['success','danger','warning','info'].includes(type)?type:'info'; const alert=document.createElement('div'); alert.className=`alert alert-${tone} position-fixed top-0 start-50 translate-middle-x mt-3 shadow`; alert.style.zIndex='9999'; alert.setAttribute('role','status'); alert.textContent=String(message || ''); document.body.appendChild(alert); setTimeout(()=>alert.remove(),3200); }
function logout() { window.clearBeautySession?.(); location.href='/login'; }
