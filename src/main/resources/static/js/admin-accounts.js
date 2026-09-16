// Admin Accounts Management JavaScript

let currentPage = 0;
let pageSize = 10;
let selectedAccountIds = new Set();
let accountToDelete = null;

// Helper function to get token
function getAuthToken() {
    return localStorage.getItem('authToken') || localStorage.getItem('token') || '';
}

// Initialize on page load
document.addEventListener('DOMContentLoaded', function() {
    if (!checkAdminAuth()) {
        return; // Stop execution if not authenticated
    }
    loadAccounts();
});

// Load accounts with filters and pagination
function loadAccounts() {
    const search = document.getElementById('searchInput').value;
    const roleFilter = document.getElementById('roleFilter').value;
    pageSize = parseInt(document.getElementById('pageSizeSelect').value);

    const params = new URLSearchParams({
        page: currentPage,
        size: pageSize,
        search: search,
        roleFilter: roleFilter,
        sortBy: 'created',
        sortDir: 'desc'
    });

    fetch(`/api/admin/accounts?${params}`, {
        headers: {
            'Authorization': 'Bearer ' + getAuthToken()
        }
    })
    .then(response => response.json())
    .then(data => {
        displayAccounts(data.accounts);
        updatePagination(data.currentPage, data.totalPages, data.totalItems);
        selectedAccountIds.clear();
        document.getElementById('selectAll').checked = false;
    })
    .catch(error => {
        console.error('Error loading accounts:', error);
        showAlert('Không thể tải danh sách tài khoản', 'danger');
    });
}

// Display accounts in table
function displayAccounts(accounts) {
    const tbody = document.getElementById('accountsTableBody');
    tbody.replaceChildren();

    if (!Array.isArray(accounts) || accounts.length === 0) {
        const row = document.createElement('tr');
        const cell = document.createElement('td');
        cell.colSpan = 7;
        cell.className = 'text-center py-4';
        const icon = document.createElement('i');
        icon.className = 'fas fa-inbox fa-3x text-muted mb-3';
        const message = document.createElement('p');
        message.className = 'text-muted';
        message.textContent = 'Không tìm thấy tài khoản nào';
        cell.append(icon, message);
        row.appendChild(cell);
        tbody.appendChild(row);
        return;
    }

    const fragment = document.createDocumentFragment();
    accounts.forEach(account => {
        const row = document.createElement('tr');
        const selectCell = document.createElement('td');
        const checkbox = document.createElement('input');
        checkbox.type = 'checkbox';
        checkbox.className = 'form-check-input account-checkbox';
        checkbox.value = String(account?.id ?? '');
        checkbox.addEventListener('change', () => toggleAccountSelection(account?.id));
        selectCell.appendChild(checkbox);

        const nameCell = document.createElement('td');
        const name = document.createElement('strong');
        name.textContent = String(account?.name ?? '');
        nameCell.appendChild(name);

        const emailCell = document.createElement('td');
        emailCell.textContent = String(account?.email ?? '');

        const phoneCell = document.createElement('td');
        appendTextOrFallback(phoneCell, account?.phone, 'N/A');

        const rolesCell = document.createElement('td');
        appendRoleBadges(rolesCell, account?.roles);

        const createdCell = document.createElement('td');
        createdCell.textContent = formatDate(account?.created);

        const actionsCell = document.createElement('td');
        const actions = document.createElement('div');
        actions.className = 'btn-group btn-group-sm';
        actions.setAttribute('role', 'group');
        actions.append(
            createAccountActionButton('btn-info', 'Xem chi tiết', 'fa-eye', () => viewAccount(account?.id)),
            createAccountActionButton('btn-warning', 'Chỉnh sửa', 'fa-edit', () => editAccount(account?.id)),
            createAccountActionButton('btn-danger', 'Xóa', 'fa-trash', () => openDeleteModal(account?.id))
        );
        actionsCell.appendChild(actions);

        row.append(selectCell, nameCell, emailCell, phoneCell, rolesCell, createdCell, actionsCell);
        fragment.appendChild(row);
    });
    tbody.appendChild(fragment);
}

function appendRoleBadges(container, roles) {
    const roleColors = {
        'ADMIN': 'primary',
        'USER': 'secondary',
        'SHIPPER': 'warning'
    };

    const values = Array.isArray(roles) ? roles : [];
    if (values.length === 0) {
        appendTextOrFallback(container, null, 'N/A');
        return;
    }
    values.forEach(role => {
        const label = String(role ?? '');
        const color = roleColors[label] || 'secondary';
        const badge = document.createElement('span');
        badge.className = `badge bg-${color} me-1`;
        badge.textContent = label;
        container.appendChild(badge);
    });
}

function appendTextOrFallback(container, value, fallback) {
    if (value !== null && value !== undefined && String(value).trim()) {
        container.textContent = String(value);
        return;
    }
    const missing = document.createElement('span');
    missing.className = 'text-muted';
    missing.textContent = fallback;
    container.appendChild(missing);
}

function createAccountActionButton(buttonClass, title, iconClass, handler) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = `btn ${buttonClass}`;
    button.title = title;
    const icon = document.createElement('i');
    icon.className = `fas ${iconClass}`;
    button.appendChild(icon);
    button.addEventListener('click', handler);
    return button;
}

// Format date
function formatDate(dateString) {
    const date = new Date(dateString);
    return date.toLocaleDateString('vi-VN', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric'
    });
}

// Update pagination
function updatePagination(current, total, totalItems) {
    const pagination = document.getElementById('pagination');
    const pageInfo = document.getElementById('pageInfo');

    // Page info
    const start = totalItems > 0 ? current * pageSize + 1 : 0;
    const end = Math.min((current + 1) * pageSize, totalItems);
    pageInfo.textContent = `Hiển thị ${start}-${end} / ${totalItems}`;

    const fragment = document.createDocumentFragment();
    fragment.appendChild(createAccountPageItem(current - 1, current <= 0, false, 'fa-chevron-left'));

    // Page numbers
    const maxPages = 5;
    let startPage = Math.max(0, current - Math.floor(maxPages / 2));
    let endPage = Math.min(total - 1, startPage + maxPages - 1);

    if (endPage - startPage < maxPages - 1) {
        startPage = Math.max(0, endPage - maxPages + 1);
    }

    for (let i = startPage; i <= endPage; i++) {
        fragment.appendChild(createAccountPageItem(i, false, i === current, null, String(i + 1)));
    }

    fragment.appendChild(createAccountPageItem(
        current + 1,
        total <= 0 || current >= total - 1,
        false,
        'fa-chevron-right'
    ));
    pagination.replaceChildren(fragment);
}

function createAccountPageItem(page, disabled, active, iconClass, label) {
    const item = document.createElement('li');
    item.className = ['page-item', disabled ? 'disabled' : '', active ? 'active' : '']
        .filter(Boolean).join(' ');
    const link = document.createElement('a');
    link.className = 'page-link';
    link.href = '#';
    if (iconClass) {
        const icon = document.createElement('i');
        icon.className = `fas ${iconClass}`;
        link.appendChild(icon);
    } else {
        link.textContent = label;
    }
    link.addEventListener('click', event => {
        event.preventDefault();
        if (!disabled) changePage(page);
    });
    item.appendChild(link);
    return item;
}

// Change page
function changePage(page) {
    currentPage = page;
    loadAccounts();
}

// Search accounts
let searchTimeout;
function searchAccounts() {
    clearTimeout(searchTimeout);
    searchTimeout = setTimeout(() => {
        currentPage = 0;
        loadAccounts();
    }, 500);
}

// Toggle select all
function toggleSelectAll() {
    const selectAll = document.getElementById('selectAll').checked;
    const checkboxes = document.querySelectorAll('.account-checkbox');
    
    checkboxes.forEach(checkbox => {
        checkbox.checked = selectAll;
        const id = parseInt(checkbox.value);
        if (selectAll) {
            selectedAccountIds.add(id);
        } else {
            selectedAccountIds.delete(id);
        }
    });
}

// Toggle account selection
function toggleAccountSelection(id) {
    if (selectedAccountIds.has(id)) {
        selectedAccountIds.delete(id);
    } else {
        selectedAccountIds.add(id);
    }
}

// Handle bulk action
function handleBulkAction() {
    const action = document.getElementById('bulkActionSelect').value;
    if (!action) return;

    if (selectedAccountIds.size === 0) {
        showAlert('Vui lòng chọn ít nhất một tài khoản', 'warning');
        document.getElementById('bulkActionSelect').value = '';
        return;
    }

    if (action === 'delete') {
        if (confirm(`Bạn có chắc chắn muốn XÓA VĨNH VIỄN ${selectedAccountIds.size} tài khoản đã chọn?\n\nLưu ý: Chỉ xóa được tài khoản chưa có đơn hàng. Hành động này không thể hoàn tác!`)) {
            const requestData = {
                userIds: Array.from(selectedAccountIds),
                action: action
            };

            fetch('/api/admin/accounts/bulk-action', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': 'Bearer ' + getAuthToken()
                },
                body: JSON.stringify(requestData)
            })
            .then(response => {
                if (!response.ok) {
                    return response.json().then(data => {
                        throw new Error(data.message || data.error || 'Không thể xóa các tài khoản đã chọn');
                    });
                }
                return response.json();
            })
            .then(data => {
                if (data.message) {
                    showAlert(data.message, 'success');
                    loadAccounts();
                }
            })
            .catch(error => {
                console.error('Error:', error);
                showAlert(error.message || 'Có lỗi xảy ra khi thực hiện hành động', 'danger');
            })
            .finally(() => {
                document.getElementById('bulkActionSelect').value = '';
            });
        } else {
            document.getElementById('bulkActionSelect').value = '';
        }
    }
}

// Open create modal
function openCreateModal() {
    document.getElementById('accountModalTitle').textContent = 'Tạo Tài khoản Mới';
    document.getElementById('accountForm').reset();
    document.getElementById('accountId').value = '';
    document.getElementById('passwordFields').style.display = 'block';
    document.getElementById('accountPassword').required = true;
    document.getElementById('accountConfirmPassword').required = true;
    document.getElementById('passwordChangeFields').style.display = 'none';
    document.getElementById('changePasswordCheck').checked = false;
    document.getElementById('passwordChangeInputs').style.display = 'none';
    
    const modal = new bootstrap.Modal(document.getElementById('accountModal'));
    modal.show();
}

// Edit account
function editAccount(id) {
    fetch(`/api/admin/accounts/${id}`, {
        headers: {
            'Authorization': 'Bearer ' + getAuthToken()
        }
    })
    .then(response => response.json())
    .then(account => {
        document.getElementById('accountModalTitle').textContent = 'Chỉnh sửa Tài khoản';
        document.getElementById('accountId').value = account.id;
        document.getElementById('accountName').value = account.name;
        document.getElementById('accountEmail').value = account.email;
        document.getElementById('accountPhone').value = account.phone || '';
        document.getElementById('accountRole').value = account.roles[0] || '';
        document.getElementById('passwordFields').style.display = 'none';
        document.getElementById('accountPassword').required = false;
        document.getElementById('accountConfirmPassword').required = false;
        document.getElementById('passwordChangeFields').style.display = 'block';
        document.getElementById('changePasswordCheck').checked = false;
        document.getElementById('passwordChangeInputs').style.display = 'none';
        document.getElementById('newPassword').value = '';
        document.getElementById('confirmNewPassword').value = '';
        
        const modal = new bootstrap.Modal(document.getElementById('accountModal'));
        modal.show();
    })
    .catch(error => {
        console.error('Error:', error);
        showAlert('Không thể tải thông tin tài khoản', 'danger');
    });
}

// Save account
function saveAccount() {
    const id = document.getElementById('accountId').value;
    const name = document.getElementById('accountName').value.trim();
    const email = document.getElementById('accountEmail').value.trim();
    const phone = document.getElementById('accountPhone').value.trim();
    const role = document.getElementById('accountRole').value;

    if (!name || !email || !role) {
        showAlert('Vui lòng điền đầy đủ thông tin bắt buộc', 'warning');
        return;
    }

    if (id) {
        // Update existing account
        const changePassword = document.getElementById('changePasswordCheck').checked;
        let updateData = { name, email, phone, role };
        
        if (changePassword) {
            const newPassword = document.getElementById('newPassword').value;
            const confirmNewPassword = document.getElementById('confirmNewPassword').value;
            
            if (!newPassword || !confirmNewPassword) {
                showAlert('Vui lòng nhập mật khẩu mới', 'warning');
                return;
            }
            
            if (newPassword !== confirmNewPassword) {
                showAlert('Mật khẩu xác nhận không khớp', 'warning');
                return;
            }
            
            if (newPassword.length < 6) {
                showAlert('Mật khẩu mới phải có ít nhất 6 ký tự', 'warning');
                return;
            }
            
            updateData.password = newPassword;
            updateData.confirmPassword = confirmNewPassword;
        }
        
        updateAccountData(id, updateData);
    } else {
        // Create new account
        const password = document.getElementById('accountPassword').value;
        const confirmPassword = document.getElementById('accountConfirmPassword').value;

        if (!password || !confirmPassword) {
            showAlert('Vui lòng nhập mật khẩu', 'warning');
            return;
        }

        if (password !== confirmPassword) {
            showAlert('Mật khẩu xác nhận không khớp', 'warning');
            return;
        }

        if (password.length < 6) {
            showAlert('Mật khẩu phải có ít nhất 6 ký tự', 'warning');
            return;
        }

        createAccountData({ name, email, phone, role, password, confirmPassword });
    }
}

// Create account
function createAccountData(data) {
    fetch('/api/admin/accounts', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer ' + getAuthToken()
        },
        body: JSON.stringify(data)
    })
    .then(async response => {
        const result = await response.json();
        if (!response.ok) throw new Error(result.message || result.error || 'Account creation failed');
        return result;
    })
    .then(result => {
        if (result.message) {
            showAlert(result.message, 'success');
            bootstrap.Modal.getInstance(document.getElementById('accountModal')).hide();
            loadAccounts();
        }
    })
    .catch(error => {
        console.error('Error:', error);
        showAlert('Có lỗi xảy ra khi tạo tài khoản', 'danger');
    });
}

// Update account
function updateAccountData(id, data) {
    fetch(`/api/admin/accounts/${id}`, {
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer ' + getAuthToken()
        },
        body: JSON.stringify(data)
    })
    .then(async response => {
        const result = await response.json();
        if (!response.ok) throw new Error(result.message || result.error || 'Account update failed');
        return result;
    })
    .then(result => {
        if (result.message) {
            showAlert(result.message, 'success');
            bootstrap.Modal.getInstance(document.getElementById('accountModal')).hide();
            loadAccounts();
        }
    })
    .catch(error => {
        console.error('Error:', error);
        showAlert('Có lỗi xảy ra khi cập nhật tài khoản', 'danger');
    });
}

// View account details
function viewAccount(id) {
    fetch(`/api/admin/accounts/${id}`, {
        headers: {
            'Authorization': 'Bearer ' + getAuthToken()
        }
    })
    .then(async response => {
        const result = await response.json();
        if (!response.ok) throw new Error(result.message || result.error || 'Account loading failed');
        return result;
    })
    .then(account => {
        const modalBody = document.getElementById('viewAccountBody');
        const row = document.createElement('div');
        row.className = 'row g-3';
        appendAccountDetail(row, 'col-md-6', 'fa-user', 'text-primary', 'Họ và Tên:', account?.name);
        appendAccountDetail(row, 'col-md-6', 'fa-envelope', 'text-info', 'Email:', account?.email);
        appendAccountDetail(
            row, 'col-md-6', 'fa-phone', 'text-success', 'Số điện thoại:', account?.phone, 'Chưa cập nhật'
        );
        appendAccountDetail(
            row, 'col-md-6', 'fa-map-marker-alt', 'text-danger', 'Địa chỉ:', account?.address, 'Chưa cập nhật'
        );
        appendAccountDetail(
            row, 'col-md-6', 'fa-user-tag', 'text-warning', 'Vai trò:', null, 'N/A',
            valueContainer => appendRoleBadges(valueContainer, account?.roles)
        );
        appendAccountDetail(
            row, 'col-md-6', 'fa-calendar-plus', 'text-primary', 'Ngày tham gia:', formatDate(account?.created)
        );
        appendAccountDetail(
            row, 'col-md-12', 'fa-clock', 'text-secondary', 'Cập nhật lần cuối:', formatDate(account?.updated)
        );
        modalBody.replaceChildren(row);
        
        const modal = new bootstrap.Modal(document.getElementById('viewAccountModal'));
        modal.show();
    })
    .catch(error => {
        console.error('Error:', error);
        showAlert('Không thể tải thông tin tài khoản', 'danger');
    });
}

function appendAccountDetail(row, columnClass, iconClass, colorClass, label, value, fallback = '', renderer = null) {
    const column = document.createElement('div');
    column.className = columnClass;
    const heading = document.createElement('strong');
    const icon = document.createElement('i');
    icon.className = `fas ${iconClass} me-2 ${colorClass}`;
    heading.append(icon, document.createTextNode(label));
    const valueContainer = document.createElement('span');
    valueContainer.className = 'ms-4';
    if (renderer) {
        renderer(valueContainer);
    } else {
        appendTextOrFallback(valueContainer, value, fallback);
    }
    column.append(heading, document.createElement('br'), valueContainer);
    row.appendChild(column);
}

// Reset password
function resetPassword(id) {
    if (confirm('Gửi email đặt lại mật khẩu cho người dùng này?')) {
        fetch(`/api/admin/accounts/${id}/reset-password`, {
            method: 'POST',
            headers: {
                'Authorization': 'Bearer ' + getAuthToken()
            }
        })
        .then(async response => {
            const data = await response.json();
            if (!response.ok) throw new Error(data.message || data.error || 'Password reset request failed');
            return data;
        })
        .then(data => {
            if (data.message) {
                showAlert(data.message, 'success');
            }
        })
        .catch(error => {
            console.error('Error:', error);
            showAlert('Có lỗi xảy ra khi gửi email', 'danger');
        });
    }
}

// Open delete modal
function openDeleteModal(id) {
    accountToDelete = id;
    const modal = new bootstrap.Modal(document.getElementById('deleteModal'));
    modal.show();
}

// Confirm delete
function confirmDelete() {
    if (accountToDelete) {
        fetch(`/api/admin/accounts/${accountToDelete}`, {
            method: 'DELETE',
            headers: {
                'Authorization': 'Bearer ' + getAuthToken()
            }
        })
        .then(response => {
            if (!response.ok) {
                return response.json().then(data => {
                    throw new Error(data.message || data.error || 'Không thể xóa tài khoản');
                });
            }
            return response.json();
        })
        .then(data => {
            if (data.message) {
                showAlert(data.message, 'success');
                loadAccounts();
            }
        })
        .catch(error => {
            console.error('Error:', error);
            showAlert(error.message || 'Có lỗi xảy ra khi xóa tài khoản', 'danger');
        })
        .finally(() => {
            bootstrap.Modal.getInstance(document.getElementById('deleteModal')).hide();
            accountToDelete = null;
        });
    }
}

// Show alert
function showAlert(message, type) {
    const alertContainer = document.getElementById('alertContainer');
    const alert = document.createElement('div');
    const safeType = ['success', 'danger', 'warning', 'info'].includes(type) ? type : 'info';
    alert.className = `alert alert-${safeType} alert-dismissible fade show`;
    const messageNode = document.createElement('span');
    messageNode.textContent = String(message ?? '');
    const closeButton = document.createElement('button');
    closeButton.type = 'button';
    closeButton.className = 'btn-close';
    closeButton.setAttribute('data-bs-dismiss', 'alert');
    closeButton.setAttribute('aria-label', 'Đóng');
    alert.append(messageNode, closeButton);
    alertContainer.appendChild(alert);

    setTimeout(() => {
        alert.remove();
    }, 5000);
}

// Check admin authentication
function checkAdminAuth() {
    const token = getAuthToken();
    const userEmail = localStorage.getItem('authEmail') || localStorage.getItem('userEmail');
    const userRole = localStorage.getItem('userRole');
    
    if (!token || !userEmail) {
        showAlert('Vui lòng đăng nhập để truy cập trang quản trị!', 'warning');
        setTimeout(() => {
            window.location.href = '/login?error=unauthorized';
        }, 1500);
        return false;
    }
    
    // Check if user has ADMIN role
    if (!userRole || !userRole.includes('ADMIN')) {
        showAlert('Bạn không có quyền truy cập trang này!', 'danger');
        setTimeout(() => {
            window.location.href = '/';
        }, 1500);
        return false;
    }
    
    // Update admin info display
    const userName = userEmail.split('@')[0];
    document.getElementById('adminName').textContent = userName.charAt(0).toUpperCase() + userName.slice(1);
    document.getElementById('adminEmail').textContent = userEmail;
    
    return true;
}

// Event listener for password change checkbox
document.getElementById('changePasswordCheck').addEventListener('change', function() {
    const passwordInputs = document.getElementById('passwordChangeInputs');
    const newPassword = document.getElementById('newPassword');
    const confirmNewPassword = document.getElementById('confirmNewPassword');
    
    if (this.checked) {
        passwordInputs.style.display = 'block';
        newPassword.required = true;
        confirmNewPassword.required = true;
    } else {
        passwordInputs.style.display = 'none';
        newPassword.required = false;
        confirmNewPassword.required = false;
        newPassword.value = '';
        confirmNewPassword.value = '';
    }
});

// Logout
function logout() {
    if (confirm('Bạn có chắc muốn đăng xuất?')) {
        window.clearBeautySession?.();
        showAlert('Đã đăng xuất thành công!', 'success');
        setTimeout(() => {
            window.location.href = '/login';
        }, 1000);
    }
}
