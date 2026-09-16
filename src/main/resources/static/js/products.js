(function () {
    'use strict';

    const state = {
        page: 0,
        search: '',
        category: '',
        brand: '',
        skinType: '',
        concern: '',
        hairType: '',
        form: '',
        price: '',
        inStock: false,
        onSale: false,
        sort: 'newest'
    };
    const productCache = new Map();

    document.addEventListener('DOMContentLoaded', async () => {
        readUrl();
        bindControls();
        await Promise.allSettled([loadCategories(), loadBrands(), loadFacets()]);
        syncControls();
        loadProducts();
    });

    function readUrl() {
        const params = new URLSearchParams(window.location.search);
        Object.keys(state).forEach(key => {
            if (key === 'page') state.page = Math.max(0, Number(params.get(key)) || 0);
            else if (['inStock', 'onSale'].includes(key)) state[key] = params.get(key) === 'true';
            else state[key] = params.get(key) || state[key];
        });
    }

    function bindControls() {
        const debouncedSearch = debounce(() => applyControl('search', document.getElementById('filterSearchInput')?.value.trim() || ''), 450);
        document.getElementById('filterSearchInput')?.addEventListener('input', debouncedSearch);
        document.getElementById('filterSearchBtn')?.addEventListener('click', () => applyControl('search', document.getElementById('filterSearchInput')?.value.trim() || ''));
        document.getElementById('filterSearchInput')?.addEventListener('keydown', event => {
            if (event.key === 'Enter') { event.preventDefault(); applyControl('search', event.currentTarget.value.trim()); }
        });
        ['brand', 'skinType', 'concern', 'hairType', 'form', 'price', 'sort'].forEach(key => {
            document.getElementById(`${key}Filter`)?.addEventListener('change', event => applyControl(key, event.target.value));
        });
        ['inStock', 'onSale'].forEach(key => {
            document.getElementById(`${key}Filter`)?.addEventListener('change', event => applyControl(key, event.target.checked));
        });
        document.getElementById('resetFiltersBtn')?.addEventListener('click', resetFilters);
    }

    function applyControl(key, value) {
        if (state[key] === value) return;
        state[key] = value;
        state.page = 0;
        updateUrl();
        loadProducts();
    }

    function syncControls() {
        ['brand', 'skinType', 'concern', 'hairType', 'form', 'price', 'sort'].forEach(key => {
            const element = document.getElementById(`${key}Filter`);
            if (element) element.value = state[key];
        });
        const search = document.getElementById('filterSearchInput');
        if (search) search.value = state.search;
        ['inStock', 'onSale'].forEach(key => {
            const element = document.getElementById(`${key}Filter`);
            if (element) element.checked = state[key];
        });
    }

    async function loadCategories() {
        const container = document.getElementById('categoryFilter');
        if (!container) return;
        try {
            const response = await fetch('/api/products/categories');
            if (!response.ok) throw new Error();
            const payload = await response.json();
            const categories = Array.isArray(payload) ? payload : payload.content || [];
            const all = createRadio('', 'Tất cả danh mục', 'category-all');
            container.replaceChildren(all, ...categoryRadios(categories));
        } catch (_) {
            container.textContent = 'Không thể tải danh mục.';
        }
    }

    function categoryRadios(categories, depth = 0) {
        return (Array.isArray(categories) ? categories : [])
            .filter(category => category?.active !== false)
            .flatMap(category => [
                createRadio(String(category.slug || category.id), category.name,
                    `category-${category.id}`, depth),
                ...categoryRadios(category.children, depth + 1)
            ]);
    }

    function createRadio(value, label, id, depth = 0) {
        const wrapper = document.createElement('div');
        wrapper.className = 'form-check mb-2';
        wrapper.style.marginInlineStart = `${Math.min(depth, 4) * 1.1}rem`;
        const input = document.createElement('input');
        input.className = 'form-check-input';
        input.type = 'radio';
        input.name = 'category';
        input.id = id;
        input.value = value;
        input.checked = state.category === value;
        input.addEventListener('change', () => applyControl('category', value));
        const text = document.createElement('label');
        text.className = 'form-check-label';
        text.htmlFor = id;
        text.textContent = label || 'Danh mục';
        wrapper.append(input, text);
        return wrapper;
    }

    async function loadBrands() {
        const select = document.getElementById('brandFilter');
        if (!select) return;
        try {
            const response = await fetch('/api/products/brands');
            if (!response.ok) return;
            const payload = await response.json();
            (Array.isArray(payload) ? payload : payload.content || []).filter(brand => brand?.active !== false).forEach(brand => {
                const option = document.createElement('option');
                option.value = brand.slug || brand.name;
                option.textContent = brand.name;
                select.appendChild(option);
            });
        } catch (_) { /* Text search remains available as a fallback. */ }
    }

    async function loadFacets() {
        try {
            const response = await fetch('/api/products/facets');
            if (!response.ok) return;
            const payload = await response.json();
            const facets = Array.isArray(payload) ? payload : payload.content || [];
            const map = { SKIN_TYPE: 'skinType', SKIN_CONCERN: 'concern', HAIR_TYPE: 'hairType', FORM: 'form' };
            facets.forEach(facet => {
                const key = map[String(facet.type || '').toUpperCase()];
                const select = key && document.getElementById(`${key}Filter`);
                if (!select || [...select.options].some(option => option.value === facet.code)) return;
                const option = document.createElement('option');
                option.value = facet.code;
                option.textContent = facet.label || facet.code;
                select.appendChild(option);
            });
        } catch (_) { /* Facet controls remain optional during migration. */ }
    }

    async function loadProducts() {
        const container = document.getElementById('productsContainer');
        if (!container) return;
        BeautyUI.setBusy(container, true);
        renderLoading(container);
        const params = new URLSearchParams({ page: state.page, size: 12, sort: state.sort });
        if (state.search) params.set('keyword', state.search);
        if (state.category) {
            if (/^\d+$/.test(state.category)) params.set('categoryId', state.category);
            else params.set('category', state.category);
        }
        ['brand', 'skinType', 'concern', 'hairType', 'form'].forEach(key => { if (state[key]) params.set(key, state[key]); });
        if (state.price) {
            const [minPrice, maxPrice] = state.price.split('-');
            params.set('minPrice', minPrice);
            params.set('maxPrice', maxPrice);
        }
        if (state.inStock) params.set('inStock', 'true');
        if (state.onSale) params.set('onSale', 'true');

        try {
            const response = await fetch(`/api/products/filter?${params}`);
            if (!response.ok) throw new Error('Không thể tải danh sách sản phẩm');
            const payload = await response.json();
            const products = Array.isArray(payload) ? payload : payload.content || [];
            productCache.clear();
            products.forEach(product => productCache.set(String(product.id), product));
            renderProducts(container, products);
            renderPagination(Array.isArray(payload) ? { totalPages: 1, number: 0 } : payload);
            document.getElementById('productCount').textContent = String(payload.totalElements ?? products.length);
            renderActiveFilters();
        } catch (error) {
            renderError(container, error.message);
        } finally {
            BeautyUI.setBusy(container, false);
        }
    }

    function renderProducts(container, products) {
        if (!products.length) {
            const empty = document.createElement('div');
            empty.className = 'col-12 text-center py-5 text-muted';
            empty.innerHTML = '<i class="fas fa-spa fa-3x mb-3" aria-hidden="true"></i><h3 class="h4">Chưa tìm thấy sản phẩm phù hợp</h3><p>Hãy thử bỏ bớt một vài bộ lọc.</p>';
            container.replaceChildren(empty);
            return;
        }
        container.replaceChildren(...products.map(createProductCard));
    }

    function createProductCard(product) {
        const column = document.createElement('div');
        column.className = 'col-sm-6 col-xl-4';
        const card = document.createElement('article');
        card.className = 'beauty-card d-flex flex-column position-relative';
        const href = BeautyUI.productHref(product);
        const variant = BeautyUI.defaultVariant(product);
        const link = document.createElement('a');
        link.href = href;
        const image = document.createElement('img');
        image.src = BeautyUI.productImage(product, variant);
        image.alt = BeautyUI.text(product.name, 'Mỹ phẩm BeautyStore');
        image.loading = 'lazy';
        link.appendChild(image);
        card.appendChild(link);

        const body = document.createElement('div');
        body.className = 'p-3 d-flex flex-column flex-grow-1';
        const brand = element('p', 'beauty-eyebrow mb-1', BeautyUI.brandName(product));
        const title = document.createElement('h3');
        title.className = 'h6 lh-base';
        const titleLink = element('a', 'text-dark text-decoration-none', BeautyUI.text(product.name, 'Sản phẩm'));
        titleLink.href = href;
        title.appendChild(titleLink);
        const labels = BeautyUI.facetLabels(product, ['SKIN_CONCERN', 'SKIN_TYPE', 'HAIR_CONCERN']);
        const chips = document.createElement('div');
        chips.className = 'd-flex flex-wrap gap-1 mb-2';
        labels.forEach(label => chips.appendChild(element('span', 'beauty-pill', label)));
        const variantText = BeautyUI.text(variant.label || [variant.shadeName, variant.volume && `${variant.volume} ${variant.volumeUnit || ''}`].filter(Boolean).join(' · '));
        const meta = element('p', 'small text-muted mb-2', variantText || `${BeautyUI.activeVariants(product).length || 1} lựa chọn`);
        const rating = element('p', 'small mb-2', product.ratingCount ? `★ ${Number(product.averageRating || 0).toFixed(1)} (${product.ratingCount})` : 'Chưa có đánh giá');
        const range = BeautyUI.priceRange(product);
        const priceLabel = range.min === range.max ? BeautyUI.formatPrice(range.min) : `${BeautyUI.formatPrice(range.min)} – ${BeautyUI.formatPrice(range.max)}`;
        const price = element('strong', 'text-danger d-block mb-3 mt-auto', priceLabel);
        const buttons = document.createElement('div');
        buttons.className = 'd-grid gap-2';
        const cartButton = element('button', 'btn btn-primary btn-sm', product.inStock === false ? 'Hết hàng' : 'Thêm vào giỏ');
        cartButton.type = 'button';
        cartButton.disabled = product.inStock === false;
        cartButton.addEventListener('click', () => addToCart(product));
        const detailButton = element('a', 'btn btn-outline-danger btn-sm', 'Xem chi tiết');
        detailButton.href = href;
        buttons.append(cartButton, detailButton);
        body.append(brand, title, chips, meta, rating, price, buttons);
        card.appendChild(body);
        column.appendChild(card);
        return column;
    }

    async function addToCart(product) {
        const variant = BeautyUI.defaultVariant(product);
        const token = localStorage.getItem('authToken') || localStorage.getItem('token');
        const headers = { 'Content-Type': 'application/json' };
        if (token) headers.Authorization = `Bearer ${token}`;
        try {
            const response = await fetch('/api/cart/add', {
                method: 'POST', headers, credentials: 'same-origin',
                body: JSON.stringify({ variantId: variant.id, quantity: 1 })
            });
            if (!response.ok) throw new Error((await response.text()) || 'Không thể thêm vào giỏ');
            BeautyUI.announce(`Đã thêm ${product.name} vào giỏ hàng`);
            showToast('Đã thêm sản phẩm vào giỏ hàng.');
            if (typeof window.updateCartBadge === 'function') window.updateCartBadge();
        } catch (error) {
            showToast(error.message, 'danger');
        }
    }

    function renderPagination(payload) {
        const container = document.getElementById('pagination');
        const pages = Number(payload.totalPages || 0);
        container.replaceChildren();
        if (pages <= 1) return;
        const add = (label, page, disabled = false, active = false) => {
            const item = document.createElement('li');
            item.className = `page-item${disabled ? ' disabled' : ''}${active ? ' active' : ''}`;
            const button = element('button', 'page-link', label);
            button.type = 'button';
            button.disabled = disabled;
            if (active) button.setAttribute('aria-current', 'page');
            button.addEventListener('click', () => { state.page = page; updateUrl(); loadProducts(); window.scrollTo({ top: 0, behavior: 'smooth' }); });
            item.appendChild(button);
            container.appendChild(item);
        };
        add('‹', state.page - 1, state.page === 0);
        const start = Math.max(0, state.page - 2);
        const end = Math.min(pages, start + 5);
        for (let page = start; page < end; page += 1) add(String(page + 1), page, false, page === state.page);
        add('›', state.page + 1, state.page >= pages - 1);
    }

    function renderActiveFilters() {
        const container = document.getElementById('activeFilters');
        container.replaceChildren();
        const labels = { search: 'Tìm', category: 'Danh mục', brand: 'Thương hiệu', skinType: 'Loại da', concern: 'Nhu cầu', hairType: 'Loại tóc', form: 'Dạng', price: 'Giá', inStock: 'Còn hàng', onSale: 'Ưu đãi' };
        Object.entries(labels).forEach(([key, label]) => {
            if (!state[key]) return;
            const chip = element('button', 'beauty-pill', `${label}: ${state[key] === true ? 'Có' : state[key]} ×`);
            chip.type = 'button';
            chip.addEventListener('click', () => { state[key] = typeof state[key] === 'boolean' ? false : ''; state.page = 0; syncControls(); updateUrl(); loadProducts(); });
            container.appendChild(chip);
        });
    }

    function resetFilters() {
        Object.assign(state, { page: 0, search: '', category: '', brand: '', skinType: '', concern: '', hairType: '', form: '', price: '', inStock: false, onSale: false, sort: 'newest' });
        document.querySelector('input[name="category"][value=""]')?.click();
        syncControls();
        updateUrl();
        loadProducts();
    }

    function updateUrl() {
        const params = new URLSearchParams();
        Object.entries(state).forEach(([key, value]) => {
            if (!value || (key === 'sort' && value === 'newest') || (key === 'page' && value === 0)) return;
            params.set(key, value);
        });
        history.replaceState({}, '', params.size ? `/products?${params}` : '/products');
    }

    function renderLoading(container) {
        const loading = document.createElement('div');
        loading.className = 'col-12 text-center py-5';
        loading.innerHTML = '<span class="spinner-border text-danger" aria-hidden="true"></span><p class="text-muted mt-3">Đang tìm sản phẩm phù hợp...</p>';
        container.replaceChildren(loading);
    }

    function renderError(container, message) {
        const error = document.createElement('div');
        error.className = 'col-12 text-center py-5';
        error.append(element('p', 'text-danger', message));
        const retry = element('button', 'btn btn-outline-danger', 'Thử lại');
        retry.type = 'button';
        retry.addEventListener('click', loadProducts);
        error.appendChild(retry);
        container.replaceChildren(error);
    }

    function showToast(message, type = 'success') {
        const toast = element('div', `alert alert-${type} position-fixed bottom-0 end-0 m-3 shadow`, message);
        toast.setAttribute('role', 'status');
        toast.style.zIndex = '1090';
        document.body.appendChild(toast);
        window.setTimeout(() => toast.remove(), 3000);
    }

    function element(tag, className, text) {
        const node = document.createElement(tag);
        node.className = className;
        node.textContent = text ?? '';
        return node;
    }

    function debounce(fn, delay) {
        let timeout;
        return (...args) => { clearTimeout(timeout); timeout = setTimeout(() => fn(...args), delay); };
    }
})();
