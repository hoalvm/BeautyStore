let currentPage = 0;
let pageSize = 10;
let currentReviews = [];

document.addEventListener('DOMContentLoaded', () => {
    if (!checkAdminAuth()) return;
    loadReviews();
});

function checkAdminAuth() {
    const token = localStorage.getItem('authToken') || localStorage.getItem('token');
    const email = localStorage.getItem('authEmail') || localStorage.getItem('userEmail');
    const role = localStorage.getItem('userRole') || '';
    if (!token || !role.includes('ADMIN')) {
        location.href = '/login?error=access_denied';
        return false;
    }
    document.getElementById('adminName').textContent = email?.split('@')[0] || 'Admin';
    document.getElementById('adminEmail').textContent = email || 'admin@beautystore.vn';
    return true;
}

function authHeaders() {
    const token = localStorage.getItem('authToken') || localStorage.getItem('token');
    return { Authorization: `Bearer ${token}` };
}

async function loadReviews() {
    const body = document.getElementById('reviewsTableBody');
    showTableMessage('Đang tải đánh giá...');
    const status = document.getElementById('minStars')?.value || '';
    const params = new URLSearchParams({ page: currentPage, size: pageSize });
    if (status) params.set('status', status);
    try {
        const response = await fetch(`/api/admin/reviews?${params}`, { headers: authHeaders() });
        if (!response.ok) throw new Error('Không thể tải đánh giá.');
        const data = await response.json();
        currentReviews = filterLocally(data.content || []);
        renderReviews(currentReviews);
        updateStatistics(data, currentReviews);
        updatePagination(data);
    } catch (error) {
        showTableMessage(error.message, true);
    }
}

function filterLocally(reviews) {
    const user = document.getElementById('userNameSearch')?.value.trim().toLocaleLowerCase('vi') || '';
    const product = document.getElementById('productNameSearch')?.value.trim().toLocaleLowerCase('vi') || '';
    return reviews.filter(review => {
        const matchesUser = !user || String(review.displayName || '').toLocaleLowerCase('vi').includes(user);
        const matchesProduct = !product || String(review.productId || '').includes(product);
        return matchesUser && matchesProduct;
    });
}

function renderReviews(reviews) {
    const body = document.getElementById('reviewsTableBody');
    body.replaceChildren();
    if (!reviews.length) {
        showTableMessage('Không có đánh giá phù hợp.');
        return;
    }
    reviews.forEach(review => body.appendChild(createReviewRow(review)));
}

function createReviewRow(review) {
    const row = document.createElement('tr');
    const statusCell = document.createElement('td');
    statusCell.appendChild(statusBadge(review.status));
    const productCell = document.createElement('td');
    const productLink = node('a', 'fw-semibold', `#${review.productId}`);
    productLink.href = `/product/${encodeURIComponent(review.productId)}`;
    productLink.target = '_blank';
    productLink.rel = 'noopener';
    productCell.append(productLink, node('div', 'small text-muted', review.variantId ? `Biến thể #${review.variantId}` : ''));
    const contentCell = document.createElement('td');
    contentCell.append(node('strong', 'd-block', review.title || 'Không có tiêu đề'), node('p', 'small text-muted mb-1', review.content || 'Không có nội dung'));
    if (review.skinType) contentCell.appendChild(node('span', 'badge bg-light text-dark border', `Loại da: ${review.skinType}`));
    const images = document.createElement('div');
    images.className = 'd-flex gap-1 mt-2';
    (review.images || []).slice(0, 5).forEach((url, index) => {
        const image = document.createElement('img');
        image.src = safeUrl(url);
        image.alt = `Ảnh đánh giá ${index + 1}`;
        image.style.cssText = 'width:44px;height:44px;object-fit:cover;border-radius:6px';
        images.appendChild(image);
    });
    if (images.childElementCount) contentCell.appendChild(images);
    const customerCell = document.createElement('td');
    customerCell.append(node('div', '', review.displayName || 'Khách hàng BeautyStore'), node('small', `badge ${review.verifiedPurchase ? 'bg-success' : 'bg-secondary'}`, review.verifiedPurchase ? 'Đã mua hàng' : 'Chưa xác minh'));
    const starsCell = node('td', 'text-warning', `${'★'.repeat(review.stars || 0)}${'☆'.repeat(5 - (review.stars || 0))}`);
    const dateCell = node('td', 'small', formatDate(review.createdAt));
    const actionCell = document.createElement('td');
    actionCell.className = 'text-center';
    const group = document.createElement('div');
    group.className = 'btn-group btn-group-sm';
    const approve = actionButton('Duyệt', 'btn-outline-success', 'APPROVED');
    const reject = actionButton('Từ chối', 'btn-outline-danger', 'REJECTED');
    approve.disabled = review.status === 'APPROVED';
    reject.disabled = review.status === 'REJECTED';
    approve.addEventListener('click', () => moderateReview(review.id, 'APPROVED'));
    reject.addEventListener('click', () => moderateReview(review.id, 'REJECTED'));
    group.append(approve, reject);
    actionCell.appendChild(group);
    row.append(statusCell, productCell, contentCell, customerCell, starsCell, dateCell, actionCell);
    return row;
}

function statusBadge(status) {
    const map = { PENDING: ['bg-warning text-dark', 'Chờ duyệt'], APPROVED: ['bg-success', 'Đã duyệt'], REJECTED: ['bg-danger', 'Từ chối'] };
    const [style, label] = map[status] || ['bg-secondary', status || 'Không rõ'];
    return node('span', `badge ${style}`, label);
}

function actionButton(label, style, status) {
    const button = node('button', `btn ${style}`, label);
    button.type = 'button';
    button.dataset.status = status;
    return button;
}

async function moderateReview(id, status) {
    const label = status === 'APPROVED' ? 'duyệt' : 'từ chối';
    if (!confirm(`Xác nhận ${label} đánh giá này?`)) return;
    try {
        const response = await fetch(`/api/admin/reviews/${id}/status?status=${status}`, { method: 'PATCH', headers: authHeaders() });
        const data = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(data.message || 'Không thể cập nhật đánh giá.');
        notify(`Đã ${label} đánh giá.`, 'success');
        loadReviews();
    } catch (error) { notify(error.message, 'danger'); }
}

function updateStatistics(data, reviews) {
    document.getElementById('totalReviews').textContent = String(data.totalElements ?? reviews.length);
    const average = reviews.length ? reviews.reduce((sum, review) => sum + Number(review.stars || 0), 0) / reviews.length : 0;
    document.getElementById('averageRating').textContent = average.toFixed(1);
    document.getElementById('fiveStars').textContent = String(reviews.filter(review => review.stars === 5).length);
    document.getElementById('lowStars').textContent = String(reviews.filter(review => review.stars <= 2).length);
}

function updatePagination(data) {
    const controls = document.getElementById('paginationControls');
    controls.replaceChildren();
    const total = Number(data.totalElements || 0);
    const from = total ? currentPage * pageSize + 1 : 0;
    const to = Math.min(total, (currentPage + 1) * pageSize);
    document.getElementById('paginationInfo').textContent = `Hiển thị ${from}–${to} trong ${total} đánh giá`;
    for (let page = 0; page < Number(data.totalPages || 0); page += 1) {
        const item = document.createElement('li');
        item.className = `page-item${page === currentPage ? ' active' : ''}`;
        const button = node('button', 'page-link', String(page + 1));
        button.type = 'button';
        button.addEventListener('click', () => { currentPage = page; loadReviews(); });
        item.appendChild(button);
        controls.appendChild(item);
    }
}

function showTableMessage(message, error = false) {
    const body = document.getElementById('reviewsTableBody');
    const row = document.createElement('tr');
    const cell = node('td', `text-center py-5 ${error ? 'text-danger' : 'text-muted'}`, message);
    cell.colSpan = 7;
    row.appendChild(cell);
    body.replaceChildren(row);
}

function applyFilters() { currentPage = 0; loadReviews(); }
function clearFilters() {
    ['userNameSearch', 'productNameSearch', 'minStars', 'maxStars', 'startDate', 'endDate'].forEach(id => { const element = document.getElementById(id); if (element) element.value = ''; });
    currentPage = 0;
    loadReviews();
}
function changePageSize() { pageSize = Number(document.getElementById('pageSizeSelect').value) || 10; currentPage = 0; loadReviews(); }
function changeSorting() { /* API returns newest first; retained for template compatibility. */ }
function toggleSelectAll() { /* Bulk deletion is intentionally unavailable for moderated reviews. */ }
function bulkDeleteReviews() { notify('Hãy duyệt hoặc từ chối đánh giá thay vì xóa vĩnh viễn.', 'warning'); }

function formatDate(value) { return value ? new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '—'; }
function safeUrl(value) { try { const url = new URL(value, location.origin); return ['http:', 'https:'].includes(url.protocol) ? url.href : ''; } catch (_) { return ''; } }
function node(tag, className, text) { const element = document.createElement(tag); element.className = className; element.textContent = text ?? ''; return element; }
function notify(message, type) { const alert = node('div', `alert alert-${type} position-fixed top-0 start-50 translate-middle-x mt-3 shadow`, message); alert.style.zIndex = '2000'; alert.setAttribute('role', 'status'); document.body.appendChild(alert); setTimeout(() => alert.remove(), 2800); }
