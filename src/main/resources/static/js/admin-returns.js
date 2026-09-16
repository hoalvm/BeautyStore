'use strict';

let currentPage = 0;
let totalPages = 0;
let selectedReturn = null;
let returnModal = null;

document.addEventListener('DOMContentLoaded', () => {
    const token = authToken();
    const role = localStorage.getItem('userRole') || '';
    if (!token || !role.includes('ADMIN')) {
        location.href = '/login?error=access_denied';
        return;
    }
    document.getElementById('adminEmail').textContent = localStorage.getItem('userEmail') || 'Quản trị viên';
    returnModal = new bootstrap.Modal(document.getElementById('returnModal'));
    document.getElementById('statusFilter').addEventListener('change', () => { currentPage = 0; loadReturns(); });
    document.getElementById('pageSize').addEventListener('change', () => { currentPage = 0; loadReturns(); });
    document.getElementById('refreshButton').addEventListener('click', loadReturns);
    document.getElementById('prevPage').addEventListener('click', () => { if (currentPage > 0) { currentPage -= 1; loadReturns(); } });
    document.getElementById('nextPage').addEventListener('click', () => { if (currentPage + 1 < totalPages) { currentPage += 1; loadReturns(); } });
    document.getElementById('logoutButton').addEventListener('click', () => {
        fetch('/api/auth/logout', { method: 'POST', credentials: 'same-origin', keepalive: true })
            .catch(() => {});
        localStorage.clear();
        location.href = '/login';
    });
    loadReturns();
});

function authToken() { return localStorage.getItem('authToken') || localStorage.getItem('token'); }
function headers(json = false) { const value = { Authorization: `Bearer ${authToken()}` }; if (json) value['Content-Type'] = 'application/json'; return value; }

async function loadReturns() {
    tableMessage('Đang tải yêu cầu…');
    const params = new URLSearchParams({ page: String(currentPage), size: document.getElementById('pageSize').value });
    const status = document.getElementById('statusFilter').value;
    if (status) params.set('status', status);
    try {
        const response = await fetch(`/api/admin/returns?${params}`, { headers: headers() });
        const payload = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(payload.message || 'Không thể tải yêu cầu đổi trả.');
        totalPages = Number(payload.totalPages || 0);
        renderRows(payload.content || []);
        const total = Number(payload.totalElements || 0);
        document.getElementById('pageInfo').textContent = total ? `Trang ${currentPage + 1}/${Math.max(totalPages, 1)} · ${total} yêu cầu` : 'Chưa có yêu cầu';
        document.getElementById('prevPage').disabled = currentPage === 0;
        document.getElementById('nextPage').disabled = currentPage + 1 >= totalPages;
    } catch (error) { tableMessage(error.message, true); }
}

function renderRows(items) {
    const body = document.getElementById('returnsBody');
    body.replaceChildren();
    if (!items.length) { tableMessage('Không có yêu cầu phù hợp.'); return; }
    items.forEach(request => {
        const row = document.createElement('tr');
        row.append(cell(`#RT-${request.id}`), cell(request.orderNumber || '—'), cell((request.items || []).map(item => `${item.productName} × ${item.quantity}`).join(', ') || '—'), cell(formatDate(request.createdAt)));
        const statusCell = document.createElement('td'); statusCell.appendChild(statusBadge(request.status)); row.appendChild(statusCell);
        const actionCell = document.createElement('td'); actionCell.className = 'text-end';
        const button = element('button', 'btn btn-sm btn-outline-primary', 'Xem & xử lý'); button.type = 'button'; button.addEventListener('click', () => showReturn(request)); actionCell.appendChild(button); row.appendChild(actionCell);
        body.appendChild(row);
    });
}

function showReturn(request) {
    selectedReturn = request;
    document.getElementById('returnModalTitle').textContent = `Yêu cầu #RT-${request.id} · Đơn ${request.orderNumber}`;
    const detail = document.getElementById('returnDetail'); detail.replaceChildren();
    const summary = element('div', 'd-flex flex-wrap gap-2 align-items-center mb-4'); summary.append(statusBadge(request.status), element('span', 'text-muted', `Tạo lúc ${formatDate(request.createdAt)}`)); detail.appendChild(summary);
    (request.items || []).forEach(item => detail.appendChild(itemCard(item, request.status)));
    if (request.adminNote) detail.appendChild(labeledText('Ghi chú quản trị', request.adminNote));
    if (request.refundReference) detail.appendChild(labeledText('Mã tham chiếu hoàn tiền', request.refundReference));
    const historyTitle = element('h3', 'h6 mt-4', 'Lịch sử xử lý'); detail.appendChild(historyTitle);
    const timeline = element('div', 'timeline');
    (request.history || []).forEach(entry => timeline.appendChild(labeledText(formatDate(entry.createdAt), `${statusLabel(entry.fromStatus)} → ${statusLabel(entry.toStatus)}${entry.note ? ` · ${entry.note}` : ''}`)));
    if (!(request.history || []).length) timeline.appendChild(element('p', 'text-muted', 'Chưa có lịch sử.'));
    detail.appendChild(timeline);
    renderActions(request);
    returnModal.show();
}

function itemCard(item, status) {
    const card = element('article', 'border rounded-3 p-3 mb-3');
    const heading = element('div', 'd-flex justify-content-between gap-3');
    const title = element('div'); title.append(element('h3', 'h6 mb-1', item.productName || 'Sản phẩm'), element('p', 'small text-muted mb-0', `${item.variantLabel || 'Biến thể mặc định'} · SL ${item.quantity}`)); heading.appendChild(title);
    if (status === 'RECEIVED' && !item.restocked) { const check = document.createElement('input'); check.type = 'checkbox'; check.className = 'form-check-input restock-item'; check.value = String(item.id); check.setAttribute('aria-label', `Chọn nhập kho ${item.productName || 'sản phẩm'}`); heading.appendChild(check); }
    card.appendChild(heading);
    card.append(element('p', 'mb-1 mt-3', `Lý do: ${reasonLabel(item.reason)}`), element('p', 'small mb-2', item.details || 'Không có mô tả thêm.'), element('p', `small ${item.unopenedAndSealed ? 'text-success' : 'text-warning'}`, item.unopenedAndSealed ? 'Khách xác nhận còn nguyên seal' : 'Không xác nhận còn nguyên seal'));
    if (item.restocked) card.appendChild(element('p', 'badge text-bg-success', `Đã nhập lại kho ${formatDate(item.restockedAt)}`));
    const images = element('div', 'd-flex flex-wrap gap-2');
    (item.imageUrls || []).forEach((url, index) => { const link = document.createElement('a'); link.href = safeUrl(url); link.target = '_blank'; link.rel = 'noopener'; const image = document.createElement('img'); image.src = safeUrl(url); image.alt = `Ảnh bằng chứng ${index + 1}`; image.className = 'evidence-thumb'; link.appendChild(image); images.appendChild(link); });
    if (images.childElementCount) card.appendChild(images);
    return card;
}

function renderActions(request) {
    const area = document.getElementById('returnActions'); area.replaceChildren();
    const options = { REQUESTED: [['APPROVED','Duyệt','btn-success'],['REJECTED','Từ chối','btn-outline-danger']], APPROVED: [['RECEIVED','Đã nhận hàng','btn-success'],['REJECTED','Từ chối','btn-outline-danger']], RECEIVED: [['REFUNDED','Ghi nhận hoàn tiền','btn-success'],['REJECTED','Từ chối','btn-outline-danger']] }[request.status] || [];
    options.forEach(([status,label,style]) => { const button = element('button', `btn ${style}`, label); button.type='button'; button.addEventListener('click', () => transition(request.id, status)); area.appendChild(button); });
    if (request.status === 'RECEIVED' && (request.items || []).some(item => !item.restocked)) { const restock = element('button', 'btn btn-outline-success', 'Nhập lại kho đã chọn'); restock.type='button'; restock.addEventListener('click', () => restockItems(request.id)); area.prepend(restock); }
}

async function transition(id, status) {
    const adminNote = prompt(status === 'REJECTED' ? 'Nhập lý do từ chối:' : 'Ghi chú xử lý (có thể để trống):') ?? null;
    if (adminNote === null) return;
    let refundReference = null;
    if (status === 'REFUNDED') { refundReference = prompt('Nhập mã tham chiếu hoàn tiền:')?.trim(); if (!refundReference) { notify('Mã tham chiếu hoàn tiền là bắt buộc.', 'danger'); return; } }
    await mutate(`/api/admin/returns/${id}/status`, 'PATCH', { status, adminNote, refundReference });
}

async function restockItems(id) {
    const returnItemIds = [...document.querySelectorAll('.restock-item:checked')].map(input => Number(input.value)).filter(Number.isFinite);
    if (!returnItemIds.length) { notify('Hãy chọn ít nhất một sản phẩm đủ điều kiện.', 'warning'); return; }
    if (!confirm('Xác nhận hàng còn đủ điều kiện và nhập lại đúng các lô xuất ban đầu?')) return;
    await mutate(`/api/admin/returns/${id}/restock`, 'POST', { returnItemIds });
}

async function mutate(url, method, body) {
    try {
        const response = await fetch(url, { method, headers: headers(true), body: JSON.stringify(body) });
        const payload = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(payload.message || 'Không thể cập nhật yêu cầu.');
        notify('Đã cập nhật yêu cầu đổi trả.', 'success'); returnModal.hide(); await loadReturns();
    } catch (error) { notify(error.message, 'danger'); }
}

function tableMessage(message, danger = false) { const row = document.createElement('tr'); const item = element('td', `text-center py-5 ${danger ? 'text-danger' : 'text-muted'}`, message); item.colSpan=6; row.appendChild(item); document.getElementById('returnsBody').replaceChildren(row); }
function statusBadge(status) { const config={REQUESTED:['text-bg-warning','Mới gửi'],APPROVED:['text-bg-info','Đã duyệt'],RECEIVED:['text-bg-primary','Đã nhận hàng'],REJECTED:['text-bg-danger','Từ chối'],REFUNDED:['text-bg-success','Đã hoàn tiền']}[status]||['text-bg-secondary',status||'Không rõ']; return element('span',`badge ${config[0]}`,config[1]); }
function statusLabel(status) { return status ? statusBadge(status).textContent : 'Khởi tạo'; }
function reasonLabel(reason) { return ({UNOPENED_CHANGE_OF_MIND:'Đổi ý, hàng còn nguyên seal',WRONG_ITEM:'Giao sai hàng',DEFECTIVE:'Sản phẩm lỗi',DAMAGED:'Hư hỏng khi giao',ALLERGIC_REACTION:'Phản ứng không phù hợp',OTHER:'Lý do khác'})[reason] || reason || 'Khác'; }
function labeledText(label, value) { const wrapper=element('div','mb-2'); wrapper.append(element('strong','d-block',label),element('span','text-muted',value)); return wrapper; }
function cell(text) { const item=document.createElement('td'); item.textContent=text ?? '—'; return item; }
function element(tag,className='',text='') { const item=document.createElement(tag); item.className=className; item.textContent=text; return item; }
function safeUrl(value) { try { const url=new URL(value,location.origin); return ['http:','https:'].includes(url.protocol)?url.href:''; } catch (_) { return ''; } }
function formatDate(value) { return value ? new Intl.DateTimeFormat('vi-VN',{dateStyle:'short',timeStyle:'short'}).format(new Date(value)) : '—'; }
function notify(message,type='info') { const alert=document.getElementById('pageAlert'); alert.className=`alert alert-${['success','danger','warning','info'].includes(type)?type:'info'} position-fixed top-0 start-50 translate-middle-x mt-3 shadow`; alert.style.zIndex='2000'; alert.textContent=message; setTimeout(()=>{alert.className='visually-hidden';alert.textContent='';},3200); }
