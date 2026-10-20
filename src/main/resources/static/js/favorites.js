(function () {
    'use strict';
    let loadVersion = 0;

    document.addEventListener('DOMContentLoaded', loadFavorites);

    function authHeaders(json = false) {
        const token = localStorage.getItem('authToken') || localStorage.getItem('token');
        const headers = {};
        if (token) headers.Authorization = `Bearer ${token}`;
        if (json) headers['Content-Type'] = 'application/json';
        return headers;
    }

    async function loadFavorites() {
        const container = document.getElementById('favoritesContainer');
        if (!container) return;
        const version = ++loadVersion;
        const token = localStorage.getItem('authToken') || localStorage.getItem('token');
        if (!token) {
            renderEmpty(container, 'Đăng nhập để xem danh sách sản phẩm yêu thích.', 'Đăng nhập', '/login?redirect=/favorites');
            return;
        }
        try {
            const response = await fetch('/api/favorites', { headers: authHeaders() });
            if (response.status === 401) {
                if (version === loadVersion) renderEmpty(container, 'Phiên đăng nhập đã hết hạn.', 'Đăng nhập lại', '/login?redirect=/favorites');
                return;
            }
            if (!response.ok) throw new Error('Không thể tải danh sách yêu thích.');
            const favorites = await response.json();
            if (version !== loadVersion) return;
            document.getElementById('favoriteCount').textContent = String(favorites.length);
            if (!favorites.length) {
                renderEmpty(container, 'Hãy lưu lại sản phẩm bạn quan tâm để dễ dàng tìm lại.', 'Khám phá mỹ phẩm', '/products');
                return;
            }
            container.replaceChildren(...favorites.map(createFavoriteCard));
        } catch (error) {
            if (version !== loadVersion) return;
            renderEmpty(container, error.message, 'Thử lại', '#');
            container.querySelector('a')?.addEventListener('click', event => { event.preventDefault(); loadFavorites(); });
        }
    }

    function createFavoriteCard(favorite) {
        const product = favorite.product || favorite;
        const variant = BeautyUI.defaultVariant(product);
        const column = document.createElement('div');
        column.className = 'col-sm-6 col-lg-4 col-xl-3 mb-4';
        const card = document.createElement('article');
        card.className = 'beauty-card d-flex flex-column';
        const link = document.createElement('a');
        link.href = BeautyUI.productHref(product);
        const image = document.createElement('img');
        image.src = BeautyUI.productImage(product, variant);
        image.alt = product.name || 'Mỹ phẩm BeautyStore';
        image.loading = 'lazy';
        link.appendChild(image);
        const body = document.createElement('div');
        body.className = 'p-3 d-flex flex-column flex-grow-1';
        body.append(textElement('p', 'beauty-eyebrow mb-1', BeautyUI.brandName(product)));
        const title = textElement('h2', 'h6 lh-base', product.name);
        const meta = textElement('p', 'small text-muted', BeautyUI.variantLabel(variant));
        const range = BeautyUI.priceRange(product);
        const price = textElement('strong', 'text-danger mt-auto mb-3', range.min === range.max
            ? BeautyUI.formatPrice(range.min) : `${BeautyUI.formatPrice(range.min)} – ${BeautyUI.formatPrice(range.max)}`);
        const actions = document.createElement('div');
        actions.className = 'd-grid gap-2';
        const available = BeautyUI.isAvailable(product);
        const multipleVariants = BeautyUI.activeVariants(product).length > 1;
        const add = textElement('button', 'btn btn-primary btn-sm', !available ? 'Không khả dụng' : multipleVariants ? 'Chọn phân loại' : 'Thêm vào giỏ');
        add.type = 'button';
        add.disabled = !available;
        add.addEventListener('click', () => {
            if (multipleVariants) window.location.href = BeautyUI.productHref(product);
            else addToCart(product, variant, add);
        });
        const remove = textElement('button', 'btn btn-outline-danger btn-sm', 'Bỏ yêu thích');
        remove.type = 'button';
        remove.addEventListener('click', () => removeFavorite(product.id, remove));
        actions.append(add, remove);
        body.append(title, meta, price, actions);
        card.append(link, body);
        column.appendChild(card);
        return column;
    }

    async function removeFavorite(productId, button) {
        if (button?.disabled) return;
        if (button) button.disabled = true;
        try {
            const response = await fetch(`/api/favorites/remove/${encodeURIComponent(productId)}`, { method: 'DELETE', headers: authHeaders() });
            if (!response.ok) throw new Error('Không thể bỏ sản phẩm yêu thích.');
            notify('Đã bỏ sản phẩm khỏi danh sách yêu thích.', 'success');
            await loadFavorites();
        } catch (error) { notify(error.message, 'danger'); }
        finally { if (button) button.disabled = false; }
    }

    async function addToCart(product, variant, button) {
        if (button?.disabled || !BeautyUI.isAvailable(product)) return;
        if (button) button.disabled = true;
        try {
            const response = await fetch('/api/cart/add', {
                method: 'POST', headers: authHeaders(true), credentials: 'same-origin',
                body: JSON.stringify({ variantId: variant.id, quantity: 1 })
            });
            if (!response.ok) throw new Error(await BeautyUI.errorMessage(response, 'Không thể thêm vào giỏ hàng.'));
            notify('Đã thêm sản phẩm vào giỏ hàng.', 'success');
            if (typeof window.updateCartBadge === 'function') window.updateCartBadge();
        } catch (error) { notify(error.message, 'danger'); }
        finally { if (button) button.disabled = false; }
    }

    function renderEmpty(container, message, action, href) {
        const box = document.createElement('div');
        box.className = 'col-12 text-center py-5';
        box.append(textElement('i', 'fas fa-heart fa-3x text-muted mb-3', ''), textElement('p', 'text-muted', message));
        const link = textElement('a', 'btn btn-primary', action);
        link.href = href;
        box.appendChild(link);
        container.replaceChildren(box);
    }

    function notify(message, type) {
        const alert = textElement('div', `alert alert-${type} position-fixed top-0 start-50 translate-middle-x mt-3 shadow`, message);
        alert.setAttribute('role', 'status');
        alert.style.zIndex = '1090';
        document.body.appendChild(alert);
        setTimeout(() => alert.remove(), 2600);
    }

    function textElement(tag, className, value) {
        const node = document.createElement(tag);
        node.className = className;
        node.textContent = value || '';
        return node;
    }
})();
