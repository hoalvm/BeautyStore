(function () {
    'use strict';
    const views = new Map();

    function clear(container) {
        views.get(container)?.();
        views.delete(container);
    }

    function mount(container, order, guestToken) {
        if (!container) return;
        clear(container);
        if (!order.hasDeliveryProof && order.status !== 'DELIVERED') return;
        const card = document.createElement('section');
        card.className = 'card card-body mt-3';
        const title = document.createElement('h6');
        title.textContent = 'Bằng chứng giao hàng';
        card.append(title);
        const status = document.createElement('p');
        status.setAttribute('role', 'status');
        status.setAttribute('aria-live', 'polite');
        status.className = 'small mb-2';
        status.textContent = order.deliveredAt
            ? `Đã giao: ${new Date(order.deliveredAt).toLocaleString('vi-VN')}` : '';
        card.append(status);
        container.append(card);
        let objectUrl = null;
        let request = null;
        let disposed = false;
        views.set(container, () => {
            disposed = true;
            request?.abort();
            if (objectUrl) URL.revokeObjectURL(objectUrl);
            card.remove();
        });
        if (!order.hasDeliveryProof) {
            status.textContent = 'Đơn hàng này chưa lưu ảnh giao hàng (dữ liệu trước khi áp dụng tính năng).';
            return;
        }
        const id = Number(order.id);
        if (!Number.isSafeInteger(id) || id < 1) return;
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'btn btn-outline-primary align-self-start';
        button.textContent = 'Xem ảnh giao hàng';
        card.append(button);
        button.addEventListener('click', async () => {
            if (button.disabled || disposed) return;
            button.disabled = true;
            status.textContent = 'Đang tải ảnh riêng tư…';
            const headers = {};
            const token = localStorage.getItem('authToken') || localStorage.getItem('token');
            if (token) headers.Authorization = `Bearer ${token}`;
            if (guestToken) headers['X-Order-Token'] = guestToken;
            request = new AbortController();
            try {
                const response = await fetch(`/api/delivery-proofs/${id}`, {
                    headers, cache: 'no-store', signal: request.signal
                });
                if (!response.ok) {
                    throw new Error([401, 403].includes(response.status)
                        ? 'Phiên truy cập đã hết hạn hoặc bạn không có quyền xem ảnh.'
                        : 'Chưa tải được ảnh. Vui lòng thử lại.');
                }
                const blob = await response.blob();
                if (blob.type !== 'image/jpeg') throw new Error('Ảnh trả về không đúng định dạng.');
                if (disposed) return;
                objectUrl = URL.createObjectURL(blob);
                const image = document.createElement('img');
                image.className = 'img-fluid rounded mt-2';
                image.style.cssText = 'max-height:480px;object-fit:contain';
                image.alt = 'Ảnh bằng chứng giao hàng của đơn ' + String(order.orderNumber || order.id);
                image.src = objectUrl;
                card.append(image);
                button.hidden = true;
                status.textContent = 'Ảnh do shipper cung cấp khi xác nhận giao thành công.';
            } catch (error) {
                if (!disposed && error.name !== 'AbortError') status.textContent = error.message;
            } finally {
                if (!disposed) button.disabled = false;
            }
        });
    }

    window.addEventListener('pagehide', () => {
        for (const container of views.keys()) clear(container);
    });
    document.addEventListener('hidden.bs.modal', event => {
        const container = event.target.querySelector('#orderDetailContent');
        if (container) clear(container);
    });
    window.DeliveryProof = {mount, clear};
}());
