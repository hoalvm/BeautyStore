// Admin Vouchers Management
let currentPage = 0;
const pageSize = 10;
let currentFilters = {};
let deleteVoucherId = null;

document.addEventListener('DOMContentLoaded', function() {
    checkAdminAuth();
    loadStatistics();
    loadVouchers();
});

function checkAdminAuth() {
    const authToken = localStorage.getItem('authToken') || localStorage.getItem('token');
    const userRole = localStorage.getItem('userRole');
    
    if (!authToken || !userRole || !userRole.includes('ADMIN')) {
        alert('Bạn không có quyền truy cập trang này');
        window.location.href = '/login';
        return;
    }

    // Load admin info
    const adminName = localStorage.getItem('userName');
    const adminEmail = localStorage.getItem('userEmail');
    if (adminName) document.getElementById('adminName').textContent = adminName;
    if (adminEmail) document.getElementById('adminEmail').textContent = adminEmail;
}

function getAuthHeaders() {
    const token = localStorage.getItem('authToken') || localStorage.getItem('token');
    return {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
    };
}

function loadStatistics() {
    fetch('/api/admin/vouchers/stats', {
        headers: getAuthHeaders()
    })
    .then(response => response.json())
    .then(stats => {
        document.getElementById('totalVouchers').textContent = stats.totalVouchers || 0;
        document.getElementById('activeVouchers').textContent = stats.activeVouchers || 0;
        document.getElementById('upcomingVouchers').textContent = stats.upcomingVouchers || 0;
        document.getElementById('totalUsage').textContent = stats.totalUsage || 0;
    })
    .catch(error => {
        console.error('Error loading statistics:', error);
    });
}

function loadVouchers(page = 0) {
    currentPage = page;
    const params = new URLSearchParams({
        page: page,
        size: pageSize,
        sortBy: 'createdAt',
        sortDir: 'desc'
    });

    // Add filters
    if (currentFilters.code) params.append('code', currentFilters.code);
    if (currentFilters.status) params.append('status', currentFilters.status);
    if (currentFilters.discountType) params.append('discountType', currentFilters.discountType);

    fetch(`/api/admin/vouchers?${params.toString()}`, {
        headers: getAuthHeaders()
    })
    .then(response => response.json())
    .then(data => {
        displayVouchers(data.content);
        displayPagination(data);
    })
    .catch(error => {
        console.error('Error loading vouchers:', error);
        renderVoucherTableMessage('Lỗi tải dữ liệu', 'text-danger');
    });
}

function displayVouchers(vouchers) {
    const tbody = document.getElementById('vouchersTableBody');
    tbody.replaceChildren();

    if (!vouchers || vouchers.length === 0) {
        renderVoucherTableMessage('Không có mã giảm giá nào');
        return;
    }

    const fragment = document.createDocumentFragment();
    vouchers.forEach(voucher => fragment.appendChild(createVoucherRow(voucher)));
    tbody.appendChild(fragment);
}

function createVoucherRow(voucher) {
    const row = document.createElement('tr');

    const codeCell = document.createElement('td');
    const code = document.createElement('strong');
    code.textContent = String(voucher?.code ?? '');
    codeCell.appendChild(code);

    const descriptionCell = document.createElement('td');
    const description = document.createElement('small');
    description.textContent = String(voucher?.description || '-');
    descriptionCell.appendChild(description);

    const typeCell = document.createElement('td');
    typeCell.appendChild(getTypeBadge(voucher?.discountType));

    const valueCell = document.createElement('td');
    appendValueDisplay(valueCell, voucher || {});

    const usageCell = document.createElement('td');
    const usage = document.createElement('span');
    const usedQuantity = Number(voucher?.usedQuantity) || 0;
    const totalQuantity = Number(voucher?.totalQuantity) || 0;
    usage.className = `badge ${usedQuantity >= totalQuantity ? 'bg-danger' : 'bg-info'}`;
    usage.textContent = `${usedQuantity}/${totalQuantity}`;
    usageCell.appendChild(usage);

    const dateCell = document.createElement('td');
    const dates = document.createElement('small');
    dates.append(
        document.createTextNode(formatDate(voucher?.startDate)),
        document.createElement('br')
    );
    const arrow = document.createElement('i');
    arrow.className = 'fas fa-arrow-down';
    dates.append(arrow, document.createElement('br'), document.createTextNode(formatDate(voucher?.endDate)));
    dateCell.appendChild(dates);

    const statusCell = document.createElement('td');
    statusCell.appendChild(getStatusBadge(voucher?.status));

    const actionCell = document.createElement('td');
    const actions = document.createElement('div');
    actions.className = 'btn-group btn-group-sm';
    actions.setAttribute('role', 'group');
    const id = voucher?.id;

    const editLink = document.createElement('a');
    editLink.href = `/admin/vouchers/edit/${encodeURIComponent(String(id ?? ''))}`;
    editLink.className = 'btn btn-outline-primary';
    editLink.title = 'Sửa';
    editLink.appendChild(createIcon('fa-edit'));

    const toggleButton = document.createElement('button');
    toggleButton.type = 'button';
    toggleButton.className = `btn btn-outline-${voucher?.active ? 'warning' : 'success'}`;
    toggleButton.title = voucher?.active ? 'Tắt' : 'Bật';
    toggleButton.appendChild(createIcon('fa-power-off'));
    toggleButton.addEventListener('click', () => toggleStatus(id));

    const deleteButton = document.createElement('button');
    deleteButton.type = 'button';
    deleteButton.className = 'btn btn-outline-danger';
    deleteButton.title = 'Xóa';
    deleteButton.appendChild(createIcon('fa-trash'));
    deleteButton.addEventListener('click', () => showDeleteModal(id));

    actions.append(editLink, toggleButton, deleteButton);
    actionCell.appendChild(actions);
    row.append(codeCell, descriptionCell, typeCell, valueCell, usageCell, dateCell, statusCell, actionCell);
    return row;
}

function getStatusBadge(status) {
    const badges = {
        'ACTIVE': ['bg-active', 'Đang hoạt động'],
        'UPCOMING': ['bg-upcoming', 'Sắp diễn ra'],
        'EXPIRED': ['bg-expired', 'Đã hết hạn'],
        'DISABLED': ['bg-disabled', 'Đã tắt'],
        'OUT_OF_STOCK': ['bg-out-of-stock', 'Hết lượt']
    };
    const [className, label] = badges[status] || ['bg-secondary', 'N/A'];
    return createBadge(className, label);
}

function getTypeBadge(type) {
    const badges = {
        'PERCENTAGE': ['bg-primary', 'Giảm %'],
        'FIXED_AMOUNT': ['bg-success', 'Giảm tiền'],
        'FREE_SHIPPING': ['bg-info', 'Freeship']
    };
    const [className, label] = badges[type] || ['bg-secondary', 'N/A'];
    return createBadge(className, label);
}

function appendValueDisplay(container, voucher) {
    switch (voucher.discountType) {
        case 'PERCENTAGE':
            container.appendChild(document.createTextNode(`${Number(voucher.discountValue) || 0}%`));
            if (voucher.maxDiscount) {
                container.appendChild(document.createElement('br'));
                const maximum = document.createElement('small');
                maximum.textContent = `(Tối đa ${formatCurrency(voucher.maxDiscount)})`;
                container.appendChild(maximum);
            }
            return;
        case 'FIXED_AMOUNT':
            container.textContent = formatCurrency(voucher.discountValue);
            return;
        case 'FREE_SHIPPING':
            container.textContent = voucher.discountValue
                ? `Tối đa ${formatCurrency(voucher.discountValue)}`
                : 'Miễn phí ship';
            return;
        default:
            container.textContent = '-';
    }
}

function createBadge(className, label) {
    const badge = document.createElement('span');
    badge.className = `badge ${className}`;
    badge.textContent = label;
    return badge;
}

function createIcon(iconClass) {
    const icon = document.createElement('i');
    icon.className = `fas ${iconClass}`;
    return icon;
}

function renderVoucherTableMessage(message, toneClass = '') {
    const tbody = document.getElementById('vouchersTableBody');
    const row = document.createElement('tr');
    const cell = document.createElement('td');
    cell.colSpan = 8;
    cell.className = ['text-center', toneClass].filter(Boolean).join(' ');
    cell.textContent = String(message ?? '');
    row.appendChild(cell);
    tbody.replaceChildren(row);
}

function formatCurrency(amount) {
    return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount);
}

function formatDate(dateString) {
    const date = new Date(dateString);
    return date.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' });
}

function displayPagination(data) {
    const pagination = document.getElementById('pagination');
    const totalPages = data.totalPages;
    const currentPage = data.number;

    if (totalPages <= 1) {
        pagination.replaceChildren();
        return;
    }

    const fragment = document.createDocumentFragment();
    fragment.appendChild(createPaginationItem('Trước', currentPage - 1, currentPage === 0));

    // Page numbers
    for (let i = 0; i < totalPages; i++) {
        if (i === 0 || i === totalPages - 1 || (i >= currentPage - 2 && i <= currentPage + 2)) {
            fragment.appendChild(createPaginationItem(String(i + 1), i, false, i === currentPage));
        } else if (i === currentPage - 3 || i === currentPage + 3) {
            const ellipsisItem = document.createElement('li');
            ellipsisItem.className = 'page-item disabled';
            const ellipsis = document.createElement('span');
            ellipsis.className = 'page-link';
            ellipsis.textContent = '...';
            ellipsisItem.appendChild(ellipsis);
            fragment.appendChild(ellipsisItem);
        }
    }

    fragment.appendChild(createPaginationItem('Sau', currentPage + 1, currentPage === totalPages - 1));
    pagination.replaceChildren(fragment);
}

function createPaginationItem(label, page, disabled, active = false) {
    const item = document.createElement('li');
    item.className = ['page-item', disabled ? 'disabled' : '', active ? 'active' : '']
        .filter(Boolean).join(' ');
    const link = document.createElement('a');
    link.className = 'page-link';
    link.href = '#';
    link.textContent = label;
    link.addEventListener('click', event => {
        event.preventDefault();
        if (!disabled) loadVouchers(page);
    });
    item.appendChild(link);
    return item;
}

function applyFilters() {
    currentFilters = {
        code: document.getElementById('filterCode').value.trim(),
        status: document.getElementById('filterStatus').value,
        discountType: document.getElementById('filterType').value
    };
    loadVouchers(0);
}

function refreshVouchers() {
    loadStatistics();
    loadVouchers(currentPage);
}

function toggleStatus(id) {
    if (!confirm('Bạn có chắc chắn muốn thay đổi trạng thái voucher này?')) {
        return;
    }

    fetch(`/api/admin/vouchers/${id}/toggle`, {
        method: 'PATCH',
        headers: getAuthHeaders()
    })
    .then(response => {
        if (response.ok) {
            showAlert('Cập nhật trạng thái thành công!', 'success');
            loadVouchers(currentPage);
            loadStatistics();
        } else {
            showAlert('Lỗi cập nhật trạng thái', 'danger');
        }
    })
    .catch(error => {
        console.error('Error toggling status:', error);
        showAlert('Lỗi kết nối máy chủ', 'danger');
    });
}

function showDeleteModal(id) {
    deleteVoucherId = id;
    const modal = new bootstrap.Modal(document.getElementById('deleteModal'));
    modal.show();
}

function confirmDelete() {
    if (!deleteVoucherId) return;

    fetch(`/api/admin/vouchers/${deleteVoucherId}`, {
        method: 'DELETE',
        headers: getAuthHeaders()
    })
    .then(response => {
        if (response.ok) {
            showAlert('Xóa mã giảm giá thành công!', 'success');
            const modal = bootstrap.Modal.getInstance(document.getElementById('deleteModal'));
            modal.hide();
            loadVouchers(currentPage);
            loadStatistics();
        } else {
            return response.text().then(text => {
                throw new Error(text || 'Lỗi xóa mã giảm giá');
            });
        }
    })
    .catch(error => {
        console.error('Error deleting voucher:', error);
        showAlert(error.message || 'Không thể xóa mã giảm giá đã được sử dụng', 'danger');
    });
}

function showAlert(message, type) {
    const alertDiv = document.createElement('div');
    const safeType = ['success', 'danger', 'warning', 'info'].includes(type) ? type : 'info';
    alertDiv.className = `alert alert-${safeType} alert-dismissible fade show position-fixed top-0 start-50 translate-middle-x mt-3`;
    alertDiv.style.zIndex = '9999';
    const messageNode = document.createElement('span');
    messageNode.textContent = String(message ?? '');
    const closeButton = document.createElement('button');
    closeButton.type = 'button';
    closeButton.className = 'btn-close';
    closeButton.setAttribute('data-bs-dismiss', 'alert');
    closeButton.setAttribute('aria-label', 'Đóng');
    alertDiv.append(messageNode, closeButton);
    document.body.appendChild(alertDiv);
    
    setTimeout(() => {
        alertDiv.remove();
    }, 3000);
}

function logout() {
    window.clearBeautySession?.();
    window.location.href = '/login';
}
