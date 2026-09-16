(function () {
    'use strict';

    let product;
    let selectedVariant;

    document.addEventListener('DOMContentLoaded', loadProduct);

    async function loadProduct() {
        const container = document.getElementById('productDetailContainer');
        BeautyUI.setBusy(container, true);
        try {
            const endpoint = resolveEndpoint();
            const response = await fetch(endpoint);
            if (!response.ok) throw new Error(response.status === 404 ? 'Sản phẩm không tồn tại hoặc đã ngừng bán.' : 'Không thể tải sản phẩm.');
            product = await response.json();
            if (product.active === false) throw new Error('Sản phẩm này hiện không còn được bán.');
            selectedVariant = BeautyUI.defaultVariant(product);
            renderProduct();
            updateMetadata();
            loadRelatedProducts();
        } catch (error) {
            renderError(error.message);
        } finally {
            BeautyUI.setBusy(container, false);
        }
    }

    function resolveEndpoint() {
        const parts = window.location.pathname.split('/').filter(Boolean);
        const identifier = decodeURIComponent(parts.at(-1) || '');
        return parts[0] === 'products'
            ? `/api/products/slug/${encodeURIComponent(identifier)}`
            : `/api/products/${encodeURIComponent(identifier)}`;
    }

    function renderProduct() {
        const container = document.getElementById('productDetailContainer');
        const layout = document.createElement('div');
        layout.className = 'row g-5 mx-0';
        layout.append(createGallery(), createSummary());
        const details = createDetails();
        container.replaceChildren(layout, details, createReviewSection());
        updateVariantView();
        updateBreadcrumb();
        loadReviews();
    }

    function createGallery() {
        const column = document.createElement('div');
        column.className = 'col-lg-6';
        const frame = document.createElement('div');
        frame.className = 'beauty-card product-gallery-frame p-3';
        const image = document.createElement('img');
        image.id = 'mainProductImage';
        image.className = 'w-100 rounded-4';
        image.alt = BeautyUI.text(product.name, 'Mỹ phẩm BeautyStore');
        image.style.aspectRatio = '1 / 1';
        image.style.objectFit = 'contain';
        frame.appendChild(image);

        const thumbnails = document.createElement('div');
        thumbnails.id = 'productThumbnails';
        thumbnails.className = 'd-flex flex-wrap gap-2 mt-3';
        const gallery = product.gallery?.length ? product.gallery : [{ url: BeautyUI.productImage(product), altText: product.name }];
        gallery.slice(0, 8).forEach((item, index) => {
            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'border-0 bg-transparent p-0';
            button.setAttribute('aria-label', `Xem ảnh ${index + 1}`);
            const thumb = document.createElement('img');
            thumb.className = 'product-gallery-thumb';
            thumb.src = BeautyUI.safeUrl(item.url);
            thumb.alt = BeautyUI.text(item.altText, `${product.name} - ảnh ${index + 1}`);
            button.addEventListener('click', () => {
                image.src = thumb.src;
                image.alt = thumb.alt;
                thumbnails.querySelectorAll('img').forEach(item => item.removeAttribute('aria-current'));
                thumb.setAttribute('aria-current', 'true');
            });
            button.appendChild(thumb);
            thumbnails.appendChild(button);
        });
        column.append(frame, thumbnails);
        return column;
    }

    function createSummary() {
        const column = document.createElement('div');
        column.className = 'col-lg-6';
        const brand = element('a', 'beauty-eyebrow text-decoration-none', BeautyUI.brandName(product));
        brand.href = product.brand?.slug ? `/products?brand=${encodeURIComponent(product.brand.slug)}` : '/products';
        const title = element('h1', 'beauty-heading display-5 fw-bold mt-2 mb-3', product.name);
        const rating = element('p', 'mb-3', product.ratingCount ? `★ ${Number(product.averageRating || 0).toFixed(1)} · ${product.ratingCount} đánh giá` : 'Chưa có đánh giá');
        rating.id = 'ratingDisplay';
        const benefit = element('p', 'lead text-muted', BeautyUI.text(product.benefits || product.description, 'Một lựa chọn chăm sóc cá nhân được tuyển chọn tại BeautyStore.'));
        const facets = document.createElement('div');
        facets.className = 'd-flex flex-wrap gap-2 mb-4';
        (product.facets || []).slice(0, 6).forEach(facet => facets.appendChild(element('span', 'beauty-pill', facet.label || facet.code)));

        const price = document.createElement('div');
        price.id = 'variantPrice';
        price.className = 'mb-4';
        const options = document.createElement('fieldset');
        options.className = 'mb-4';
        const legend = element('legend', 'h6 fw-bold', 'Chọn phân loại');
        const optionList = document.createElement('div');
        optionList.id = 'variantOptions';
        optionList.className = 'd-flex flex-wrap gap-2';
        const variants = BeautyUI.activeVariants(product);
        (variants.length ? variants : [selectedVariant]).forEach(variant => optionList.appendChild(createVariantButton(variant)));
        options.append(legend, optionList);

        const stock = element('p', 'small mb-3', '');
        stock.id = 'variantStock';
        stock.setAttribute('aria-live', 'polite');
        const quantityGroup = document.createElement('div');
        quantityGroup.className = 'd-flex align-items-center gap-3 mb-4';
        const quantityLabel = element('label', 'fw-bold', 'Số lượng');
        quantityLabel.htmlFor = 'quantityInput';
        const quantity = document.createElement('input');
        quantity.id = 'quantityInput';
        quantity.type = 'number';
        quantity.className = 'form-control';
        quantity.min = '1';
        quantity.value = '1';
        quantity.style.maxWidth = '100px';
        quantityGroup.append(quantityLabel, quantity);

        const actions = document.createElement('div');
        actions.className = 'd-grid d-sm-flex gap-2';
        const cart = element('button', 'btn btn-primary btn-lg flex-grow-1', 'Thêm vào giỏ');
        cart.id = 'addToCartBtn';
        cart.type = 'button';
        cart.addEventListener('click', () => submitCart(false));
        const buy = element('button', 'btn btn-danger btn-lg flex-grow-1', 'Mua ngay');
        buy.id = 'buyNowBtn';
        buy.type = 'button';
        buy.addEventListener('click', () => submitCart(true));
        const favorite = element('button', 'btn btn-outline-danger btn-lg', '♡');
        favorite.type = 'button';
        favorite.title = 'Thêm vào yêu thích';
        favorite.setAttribute('aria-label', 'Thêm vào yêu thích');
        favorite.addEventListener('click', toggleFavorite);
        actions.append(cart, buy, favorite);

        const note = document.createElement('div');
        note.className = 'alert alert-light border mt-4 small';
        note.innerHTML = '<i class="fas fa-circle-info me-2" aria-hidden="true"></i>Đọc kỹ thành phần và hướng dẫn. Thử trên vùng da nhỏ trước khi dùng, đặc biệt với làn da nhạy cảm.';
        column.append(brand, title, rating, benefit, facets, price, options, stock, quantityGroup, actions, note);
        return column;
    }

    function createVariantButton(variant) {
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'variant-option beauty-pill';
        button.dataset.variantId = variant.id ?? '';
        const shade = validHex(variant.shadeHex);
        if (shade) {
            const dot = document.createElement('span');
            dot.className = 'shade-dot';
            dot.style.setProperty('--shade', shade);
            dot.setAttribute('aria-hidden', 'true');
            button.appendChild(dot);
        }
        button.append(document.createTextNode(variantLabel(variant)));
        button.setAttribute('aria-pressed', String(String(variant.id) === String(selectedVariant.id)));
        button.disabled = Number(variant.availableStock ?? 0) <= 0;
        button.addEventListener('click', () => { selectedVariant = variant; updateVariantView(); });
        return button;
    }

    function updateVariantView() {
        document.querySelectorAll('.variant-option').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.variantId === String(selectedVariant.id ?? ''))));
        const mainImage = document.getElementById('mainProductImage');
        if (mainImage) mainImage.src = BeautyUI.productImage(product, selectedVariant);
        const regular = Number(selectedVariant.price ?? product.minPrice ?? 0);
        const current = BeautyUI.currentPrice(product, selectedVariant);
        const price = document.getElementById('variantPrice');
        price.replaceChildren(element('span', 'display-6 fw-bold text-danger me-3', BeautyUI.formatPrice(current)));
        if (current < regular) price.appendChild(element('del', 'text-muted', BeautyUI.formatPrice(regular)));
        const available = Number(selectedVariant.availableStock ?? product.totalAvailableStock ?? 0);
        const stock = document.getElementById('variantStock');
        stock.className = `small mb-3 ${available > 0 ? 'text-success' : 'text-danger'}`;
        stock.textContent = available > 0 ? `Còn hàng · SKU: ${BeautyUI.text(selectedVariant.sku, 'đang cập nhật')}` : 'Tạm hết hàng';
        const quantity = document.getElementById('quantityInput');
        quantity.max = String(Math.max(1, available));
        ['addToCartBtn', 'buyNowBtn'].forEach(id => { document.getElementById(id).disabled = available <= 0; });
        BeautyUI.announce(`Đã chọn ${variantLabel(selectedVariant)}, giá ${BeautyUI.formatPrice(current)}`);
    }

    function createDetails() {
        const section = document.createElement('section');
        section.className = 'mt-5';
        section.setAttribute('aria-labelledby', 'detailsTitle');
        section.appendChild(element('h2', 'beauty-heading fw-bold mb-4', 'Thông tin sản phẩm'));
        section.firstChild.id = 'detailsTitle';
        const grid = document.createElement('div');
        grid.className = 'row g-4';
        const content = [
            ['Công dụng', product.benefits || product.description],
            ['Thành phần (INCI)', product.inci],
            ['Cách sử dụng', product.directions],
            ['Cảnh báo', product.warnings]
        ].filter(([, value]) => BeautyUI.text(value));
        content.forEach(([title, value]) => {
            const col = document.createElement('div');
            col.className = 'col-md-6';
            const card = document.createElement('article');
            card.className = 'beauty-card p-4';
            card.append(element('h3', 'h5', title), element('p', 'text-muted mb-0', value));
            col.appendChild(card);
            grid.appendChild(col);
        });
        const specs = document.createElement('div');
        specs.className = 'col-12';
        const table = document.createElement('table');
        table.className = 'table specification-table mb-0';
        const body = document.createElement('tbody');
        [
            ['Thương hiệu', BeautyUI.brandName(product)], ['Xuất xứ', product.origin], ['Chỉ số SPF', product.spf],
            ['PAO sau mở nắp', product.paoMonths && `${product.paoMonths} tháng`],
            ['Hạn sử dụng tiêu chuẩn', product.shelfLifeMonths && `${product.shelfLifeMonths} tháng`],
            ['Chất liệu', product.material], ['Bảo hành', product.warrantyMonths && `${product.warrantyMonths} tháng`]
        ].filter(([, value]) => BeautyUI.text(value)).forEach(([label, value]) => {
            const row = document.createElement('tr');
            row.append(element('th', '', label), element('td', '', value));
            body.appendChild(row);
        });
        table.appendChild(body);
        const card = document.createElement('div');
        card.className = 'product-specifications';
        card.appendChild(table);
        specs.appendChild(card);
        grid.appendChild(specs);
        section.appendChild(grid);
        return section;
    }

    function createReviewSection() {
        const section = document.createElement('section');
        section.className = 'mt-5 pt-4 border-top';
        section.setAttribute('aria-labelledby', 'reviewsTitle');
        const headingRow = document.createElement('div');
        headingRow.className = 'd-flex flex-wrap justify-content-between align-items-end gap-3 mb-4';
        const heading = document.createElement('div');
        heading.append(element('p', 'beauty-eyebrow mb-1', 'Trải nghiệm thực tế'), element('h2', 'beauty-heading fw-bold mb-0', 'Đánh giá từ khách hàng'));
        heading.lastChild.id = 'reviewsTitle';
        const orderItemId = new URLSearchParams(window.location.search).get('orderItemId');
        const action = element('a', 'btn btn-outline-danger', orderItemId ? 'Ẩn biểu mẫu' : 'Viết đánh giá');
        action.href = orderItemId ? BeautyUI.productHref(product) : '/orders';
        headingRow.append(heading, action);

        const layout = document.createElement('div');
        layout.className = 'row g-4';
        const listColumn = document.createElement('div');
        listColumn.className = orderItemId ? 'col-lg-7' : 'col-12';
        const list = document.createElement('div');
        list.id = 'reviewList';
        list.setAttribute('aria-live', 'polite');
        list.appendChild(element('p', 'text-muted', 'Đang tải đánh giá...'));
        listColumn.appendChild(list);
        layout.appendChild(listColumn);

        if (orderItemId) {
            const formColumn = document.createElement('div');
            formColumn.className = 'col-lg-5';
            const form = document.createElement('form');
            form.id = 'reviewForm';
            form.className = 'beauty-card p-4';
            form.innerHTML = `
                <h3 class="h5 mb-3">Chia sẻ trải nghiệm</h3>
                <p class="small text-muted">Chỉ áp dụng cho sản phẩm trong đơn đã giao. Đánh giá sẽ hiển thị sau khi được duyệt.</p>
                <div class="mb-3"><label class="form-label" for="reviewStars">Số sao</label><select class="form-select" id="reviewStars" required><option value="5">5 – Rất hài lòng</option><option value="4">4 – Hài lòng</option><option value="3">3 – Bình thường</option><option value="2">2 – Chưa hài lòng</option><option value="1">1 – Không hài lòng</option></select></div>
                <div class="mb-3"><label class="form-label" for="reviewTitle">Tiêu đề</label><input class="form-control" id="reviewTitle" maxlength="120"></div>
                <div class="mb-3"><label class="form-label" for="reviewContent">Nội dung</label><textarea class="form-control" id="reviewContent" rows="4" maxlength="3000"></textarea></div>
                <div class="mb-3"><label class="form-label" for="reviewSkinType">Loại da của bạn</label><input class="form-control" id="reviewSkinType" maxlength="50" placeholder="Ví dụ: Da hỗn hợp"></div>
                <div class="mb-3"><label class="form-label" for="reviewImages">Ảnh thực tế (tối đa 5)</label><input class="form-control" id="reviewImages" type="file" accept="image/jpeg,image/png,image/webp" multiple><div class="form-text">JPEG, PNG hoặc WebP; tối đa 10 MB mỗi ảnh.</div></div>
                <button class="btn btn-danger w-100" type="submit">Gửi đánh giá</button>`;
            form.addEventListener('submit', event => submitReview(event, orderItemId));
            formColumn.appendChild(form);
            layout.appendChild(formColumn);
        }
        section.append(headingRow, layout);
        return section;
    }

    async function loadReviews() {
        const list = document.getElementById('reviewList');
        if (!list) return;
        try {
            const response = await fetch(`/api/reviews/product/${encodeURIComponent(product.id)}?page=0&size=10`);
            if (!response.ok) throw new Error();
            const payload = await response.json();
            const reviews = Array.isArray(payload) ? payload : payload.content || [];
            if (!reviews.length) {
                list.replaceChildren(element('p', 'text-muted', 'Chưa có đánh giá đã duyệt cho sản phẩm này.'));
                return;
            }
            list.replaceChildren(...reviews.map(review => {
                const article = document.createElement('article');
                article.className = 'beauty-card p-4 mb-3';
                const title = element('h3', 'h6 mb-1', review.title || `${review.stars} sao`);
                const byline = element('p', 'small text-muted mb-2', `${'★'.repeat(review.stars || 0)}${'☆'.repeat(5 - (review.stars || 0))} · ${review.displayName || 'Khách hàng BeautyStore'}${review.verifiedPurchase ? ' · Đã mua hàng' : ''}`);
                const content = element('p', 'mb-2', review.content || 'Khách hàng chưa để lại nội dung.');
                article.append(title, byline, content);
                if (review.skinType) article.appendChild(element('span', 'beauty-pill', `Loại da: ${review.skinType}`));
                const images = document.createElement('div');
                images.className = 'd-flex flex-wrap gap-2 mt-3';
                (review.images || []).slice(0, 5).forEach((url, index) => {
                    const image = document.createElement('img');
                    image.src = BeautyUI.safeUrl(url);
                    image.alt = `Ảnh đánh giá ${index + 1}`;
                    image.className = 'product-gallery-thumb';
                    image.loading = 'lazy';
                    images.appendChild(image);
                });
                if (images.childElementCount) article.appendChild(images);
                return article;
            }));
        } catch (_) {
            list.replaceChildren(element('p', 'text-muted', 'Chưa thể tải đánh giá lúc này.'));
        }
    }

    async function submitReview(event, orderItemId) {
        event.preventDefault();
        const form = event.currentTarget;
        const button = form.querySelector('button[type="submit"]');
        const token = localStorage.getItem('authToken') || localStorage.getItem('token');
        const headers = { 'Content-Type': 'application/json' };
        if (token) headers.Authorization = `Bearer ${token}`;
        const guestToken = sessionStorage.getItem('guestOrderToken');
        if (guestToken) headers['X-Order-Token'] = guestToken;
        button.disabled = true;
        try {
            const files = Array.from(document.getElementById('reviewImages')?.files || []);
            if (files.length > 5) throw new Error('Mỗi đánh giá có tối đa 5 ảnh.');
            const imageUrls = [];
            for (const file of files) {
                const data = new FormData();
                data.append('orderItemId', String(orderItemId));
                data.append('image', file);
                const uploadHeaders = {};
                if (token) uploadHeaders.Authorization = `Bearer ${token}`;
                if (guestToken) uploadHeaders['X-Order-Token'] = guestToken;
                const upload = await fetch('/api/uploads/review', {
                    method: 'POST', headers: uploadHeaders, body: data
                });
                const uploaded = await upload.json().catch(() => ({}));
                if (!upload.ok || !uploaded.url) {
                    throw new Error(uploaded.message || 'Không thể tải ảnh đánh giá.');
                }
                imageUrls.push(uploaded.url);
            }
            const response = await fetch('/api/reviews', {
                method: 'POST', headers,
                body: JSON.stringify({ orderItemId: Number(orderItemId), stars: Number(document.getElementById('reviewStars').value), title: document.getElementById('reviewTitle').value.trim(), content: document.getElementById('reviewContent').value.trim(), skinType: document.getElementById('reviewSkinType').value.trim(), imageUrls })
            });
            if (!response.ok) {
                const payload = await response.json().catch(() => ({}));
                throw new Error(payload.message || payload.error || 'Không thể gửi đánh giá.');
            }
            form.reset();
            notify('Cảm ơn bạn! Đánh giá đã được gửi và đang chờ duyệt.', 'success');
        } catch (error) { notify(error.message, 'danger'); }
        finally { button.disabled = false; }
    }

    async function submitCart(buyNow) {
        const quantity = Math.max(1, Number(document.getElementById('quantityInput')?.value) || 1);
        const token = localStorage.getItem('authToken') || localStorage.getItem('token');
        const headers = { 'Content-Type': 'application/json' };
        if (token) headers.Authorization = `Bearer ${token}`;
        try {
            const response = await fetch('/api/cart/add', {
                method: 'POST', headers, credentials: 'same-origin',
                body: JSON.stringify({ variantId: selectedVariant.id, quantity })
            });
            if (!response.ok) throw new Error((await response.text()) || 'Không thể thêm sản phẩm vào giỏ.');
            if (buyNow) window.location.href = '/cart';
            else {
                BeautyUI.announce(`Đã thêm ${quantity} ${product.name} vào giỏ hàng`);
                notify('Đã thêm sản phẩm vào giỏ hàng.', 'success');
                if (typeof window.updateCartBadge === 'function') window.updateCartBadge();
            }
        } catch (error) { notify(error.message, 'danger'); }
    }

    async function toggleFavorite(event) {
        const token = localStorage.getItem('authToken') || localStorage.getItem('token');
        if (!token) { notify('Vui lòng đăng nhập để lưu sản phẩm yêu thích.', 'warning'); return; }
        try {
            const response = await fetch(`/api/favorites/toggle/${product.id}`, { method: 'POST', headers: { Authorization: `Bearer ${token}` } });
            if (!response.ok) throw new Error('Không thể cập nhật yêu thích.');
            event.currentTarget.textContent = '♥';
            event.currentTarget.setAttribute('aria-label', 'Đã thêm vào yêu thích');
            notify('Đã cập nhật danh sách yêu thích.', 'success');
        } catch (error) { notify(error.message, 'danger'); }
    }

    async function loadRelatedProducts() {
        const container = document.getElementById('relatedProducts');
        try {
            const category = product.category?.slug || product.category?.id;
            const params = new URLSearchParams({ page: 0, size: 4 });
            if (category) params.set(/^\d+$/.test(String(category)) ? 'categoryId' : 'category', category);
            const response = await fetch(`/api/products/filter?${params}`);
            if (!response.ok) return;
            const payload = await response.json();
            const related = (Array.isArray(payload) ? payload : payload.content || []).filter(item => item.id !== product.id).slice(0, 4);
            container.replaceChildren(...related.map(createRelatedCard));
        } catch (_) { container.replaceChildren(); }
    }

    function createRelatedCard(item) {
        const col = document.createElement('div');
        col.className = 'col-6 col-lg-3';
        const link = document.createElement('a');
        link.className = 'beauty-card d-block text-decoration-none text-dark';
        link.href = BeautyUI.productHref(item);
        const image = document.createElement('img');
        image.src = BeautyUI.productImage(item);
        image.alt = item.name;
        image.loading = 'lazy';
        const body = document.createElement('div');
        body.className = 'p-3';
        body.append(element('p', 'beauty-eyebrow mb-1', BeautyUI.brandName(item)), element('h3', 'h6', item.name), element('strong', 'text-danger', BeautyUI.formatPrice(BeautyUI.priceRange(item).min)));
        link.append(image, body);
        col.appendChild(link);
        return col;
    }

    function updateBreadcrumb() {
        document.getElementById('breadcrumbProduct').textContent = product.name;
        const list = document.getElementById('productBreadcrumb');
        (product.categoryBreadcrumb || []).forEach(category => {
            const item = document.createElement('li');
            item.className = 'breadcrumb-item';
            const link = element('a', '', category.name);
            link.href = `/products?category=${encodeURIComponent(category.slug || category.id)}`;
            item.appendChild(link);
            list.insertBefore(item, list.lastElementChild);
        });
    }

    function updateMetadata() {
        document.title = `${product.name} – BeautyStore`;
        const canonical = document.querySelector('link[rel="canonical"]') || document.createElement('link');
        canonical.rel = 'canonical';
        canonical.href = `${window.location.origin}${BeautyUI.productHref(product)}`;
        if (!canonical.isConnected) document.head.appendChild(canonical);
        [['og:title', product.name], ['og:description', product.benefits || product.description || ''], ['og:image', BeautyUI.productImage(product, selectedVariant)]].forEach(([property, content]) => {
            const meta = document.querySelector(`meta[property="${property}"]`) || document.createElement('meta');
            meta.setAttribute('property', property);
            meta.content = content;
            if (!meta.isConnected) document.head.appendChild(meta);
        });
        const range = BeautyUI.priceRange(product);
        const structured = {
            '@context': 'https://schema.org', '@type': 'Product', name: product.name,
            image: (product.gallery || []).map(item => BeautyUI.safeUrl(item.url)),
            description: product.benefits || product.description, sku: selectedVariant.sku,
            brand: { '@type': 'Brand', name: BeautyUI.brandName(product) },
            offers: { '@type': 'AggregateOffer', priceCurrency: 'VND', lowPrice: range.min, highPrice: range.max, offerCount: BeautyUI.activeVariants(product).length || 1,
                availability: product.inStock === false ? 'https://schema.org/OutOfStock' : 'https://schema.org/InStock' }
        };
        if (product.ratingCount) structured.aggregateRating = { '@type': 'AggregateRating', ratingValue: product.averageRating, reviewCount: product.ratingCount };
        document.getElementById('productStructuredData').textContent = JSON.stringify(structured).replaceAll('<', '\\u003c');
    }

    function renderError(message) {
        const container = document.getElementById('productDetailContainer');
        const box = document.createElement('div');
        box.className = 'text-center py-5';
        box.append(element('p', 'text-danger h5', message));
        const link = element('a', 'btn btn-outline-danger', 'Quay lại danh mục');
        link.href = '/products';
        box.appendChild(link);
        container.replaceChildren(box);
    }

    function variantLabel(variant) {
        return BeautyUI.text(variant.label || [variant.shadeName, variant.volume && `${variant.volume} ${variant.volumeUnit || ''}`].filter(Boolean).join(' · '), 'Tiêu chuẩn');
    }

    function validHex(value) { return /^#[0-9a-f]{6}$/i.test(value || '') ? value : ''; }

    function notify(message, type) {
        const alert = element('div', `alert alert-${type} position-fixed bottom-0 end-0 m-3 shadow`, message);
        alert.setAttribute('role', 'status');
        alert.style.zIndex = '1090';
        document.body.appendChild(alert);
        setTimeout(() => alert.remove(), 3200);
    }

    function element(tag, className, value) {
        const node = document.createElement(tag);
        node.className = className;
        node.textContent = BeautyUI.text(value);
        return node;
    }
})();
