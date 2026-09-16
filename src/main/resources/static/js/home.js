(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', () => {
        loadCategories();
        loadCollection('/api/products/featured', 'featuredProducts');
        loadCollection('/api/products/filter?onSale=true&page=0&size=4', 'saleProducts');
    });

    async function loadCategories() {
        const container = document.getElementById('homeCategories');
        if (!container) return;
        try {
            const response = await fetch('/api/products/categories');
            if (!response.ok) throw new Error('Không thể tải danh mục');
            const payload = await response.json();
            const categories = (Array.isArray(payload) ? payload : payload.content || [])
                .filter(category => category?.active !== false && !category?.parentId)
                .slice(0, 8);
            container.replaceChildren(...categories.map(createCategoryCard));
        } catch (error) {
            renderMessage(container, error.message, 'fa-layer-group');
        }
    }

    function createCategoryCard(category) {
        const column = document.createElement('div');
        column.className = 'col-6 col-md-4 col-lg-3';
        const link = document.createElement('a');
        link.className = 'beauty-card d-flex h-100 align-items-center gap-3 p-3 text-decoration-none text-dark';
        link.href = `/products?category=${encodeURIComponent(category.slug || category.id)}`;
        const icon = document.createElement('span');
        icon.className = 'rounded-circle beauty-soft-bg d-inline-grid place-items-center p-3 text-danger';
        icon.setAttribute('aria-hidden', 'true');
        icon.innerHTML = '<i class="fas fa-spa"></i>';
        const name = document.createElement('strong');
        name.textContent = category.name || 'Danh mục';
        link.append(icon, name);
        column.appendChild(link);
        return column;
    }

    async function loadCollection(url, containerId) {
        const container = document.getElementById(containerId);
        if (!container) return;
        BeautyUI.setBusy(container, true);
        try {
            const response = await fetch(url);
            if (!response.ok) throw new Error('Không thể tải sản phẩm');
            const payload = await response.json();
            const products = (Array.isArray(payload) ? payload : payload.content || []).slice(0, 4);
            if (!products.length) {
                renderMessage(container, 'Chưa có sản phẩm trong bộ sưu tập này.', 'fa-spa');
            } else {
                container.replaceChildren(...products.map(product => createProductColumn(product, 'col-lg-3 col-md-6')));
            }
        } catch (error) {
            renderMessage(container, `${error.message}. Vui lòng thử lại sau.`, 'fa-circle-exclamation');
        } finally {
            BeautyUI.setBusy(container, false);
        }
    }

    function createProductColumn(product, classes) {
        const column = document.createElement('div');
        column.className = classes;
        const card = document.createElement('article');
        card.className = 'beauty-card d-flex flex-column position-relative';

        const href = BeautyUI.productHref(product);
        const imageLink = document.createElement('a');
        imageLink.href = href;
        const image = document.createElement('img');
        image.src = BeautyUI.productImage(product);
        image.alt = BeautyUI.text(product.name, 'Sản phẩm BeautyStore');
        image.loading = 'lazy';
        image.width = 420;
        image.height = 420;
        imageLink.appendChild(image);

        const variant = BeautyUI.defaultVariant(product);
        const regular = Number(variant.price ?? product.minPrice ?? 0);
        const current = BeautyUI.currentPrice(product, variant);
        if (current < regular) {
            const badge = document.createElement('span');
            badge.className = 'badge bg-danger position-absolute top-0 start-0 m-3';
            badge.textContent = 'SALE';
            card.appendChild(badge);
        }

        const body = document.createElement('div');
        body.className = 'p-3 d-flex flex-column flex-grow-1';
        const brand = document.createElement('p');
        brand.className = 'beauty-eyebrow mb-2';
        brand.textContent = BeautyUI.brandName(product);
        const title = document.createElement('h3');
        title.className = 'h6 lh-base mb-2';
        const titleLink = document.createElement('a');
        titleLink.className = 'stretched-link text-decoration-none text-dark';
        titleLink.href = href;
        titleLink.textContent = BeautyUI.text(product.name, 'Sản phẩm');
        title.appendChild(titleLink);

        const meta = document.createElement('p');
        meta.className = 'small text-muted mb-2';
        meta.textContent = BeautyUI.text(variant.label || [variant.shadeName, variant.volume && `${variant.volume} ${variant.volumeUnit || ''}`].filter(Boolean).join(' · '), 'Nhiều lựa chọn');
        const price = document.createElement('div');
        price.className = 'mt-auto d-flex align-items-baseline gap-2';
        const currentEl = document.createElement('strong');
        currentEl.className = 'text-danger';
        currentEl.textContent = BeautyUI.formatPrice(current);
        price.appendChild(currentEl);
        if (current < regular) {
            const old = document.createElement('del');
            old.className = 'small text-muted';
            old.textContent = BeautyUI.formatPrice(regular);
            price.appendChild(old);
        }
        body.append(brand, title, meta, price);
        card.append(imageLink, body);
        column.appendChild(card);
        return column;
    }

    function renderMessage(container, message, iconName) {
        const wrapper = document.createElement('div');
        wrapper.className = 'col-12 text-center py-5 text-muted';
        const icon = document.createElement('i');
        icon.className = `fas ${iconName} fa-2x mb-3 d-block`;
        icon.setAttribute('aria-hidden', 'true');
        const paragraph = document.createElement('p');
        paragraph.textContent = message;
        wrapper.append(icon, paragraph);
        container.replaceChildren(wrapper);
    }
})();
