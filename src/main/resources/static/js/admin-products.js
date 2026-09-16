let currentPage = 0;
let currentSearch = '';
let currentStockFilter = '';
let currentIncludeInactive = false;
const pageSize = 20;
let categories = [];
let brands = [];
let editingProduct = null;
let currentBatchVariant = null;
let currentInventoryAlertKind = 'low';
let returnToProductModalAfterBatch = false;
const variantDetailsCache = new Map();

document.addEventListener('DOMContentLoaded', async () => {
    if (!checkAdminAuth()) return;
    setupCatalogAdminEvents();
    const filter = new URLSearchParams(location.search).get('filter');
    if (['in-stock', 'low-stock', 'out-of-stock'].includes(filter)) {
        currentStockFilter = filter;
        const control = document.getElementById('stockFilter');
        if (control) control.value = filter;
    }
    await Promise.allSettled([loadCategories(), loadBrands()]);
    loadProducts();
    loadInventoryAlerts();
});

function tokenHeaders(json = false) {
    const token = localStorage.getItem('authToken') || localStorage.getItem('token');
    const headers = { Authorization: `Bearer ${token}` };
    if (json) headers['Content-Type'] = 'application/json';
    return headers;
}

function checkAdminAuth() {
    const token = localStorage.getItem('authToken') || localStorage.getItem('token');
    const role = localStorage.getItem('userRole') || '';
    if (!token || !role.includes('ADMIN')) {
        showToast('Bạn cần đăng nhập bằng tài khoản quản trị.', 'warning');
        setTimeout(() => { location.href = '/login?error=unauthorized'; }, 1000);
        return false;
    }
    return true;
}

async function loadCategories() {
    try {
        let response = await fetch('/api/admin/categories?includeInactive=true', { headers: tokenHeaders() });
        if (!response.ok) response = await fetch('/api/products/categories');
        if (!response.ok) throw new Error();
        const payload = await response.json();
        categories = flattenCategories(Array.isArray(payload) ? payload : payload.content || []);
        populateSelect('productCategory', categories, 'Chọn danh mục');
        populateCategoryParentOptions();
        renderCategories();
    } catch (_) { showToast('Không thể tải danh mục.', 'warning'); }
}

async function loadBrands() {
    try {
        let response = await fetch('/api/admin/brands?includeInactive=true', { headers: tokenHeaders() });
        if (!response.ok) response = await fetch('/api/products/brands');
        if (!response.ok) throw new Error();
        const payload = await response.json();
        brands = Array.isArray(payload) ? payload : payload.content || [];
        populateSelect('productBrand', brands, 'Chọn thương hiệu');
        renderBrands();
    } catch (_) { showToast('Không thể tải thương hiệu.', 'warning'); }
}

function populateSelect(id, items, placeholder) {
    const select = document.getElementById(id);
    if (!select) return;
    select.replaceChildren(option('', placeholder));
    items.forEach(item => select.appendChild(option(item.id, `${item.name}${item.active === false ? ' (đã ẩn)' : ''}`)));
}

function option(value, label) {
    const node = document.createElement('option');
    node.value = value;
    node.textContent = label;
    return node;
}

function setupCatalogAdminEvents() {
    document.getElementById('addBrandBtn')?.addEventListener('click', () => openBrandModal());
    document.getElementById('brandForm')?.addEventListener('submit', saveBrand);
    document.getElementById('addCategoryBtn')?.addEventListener('click', () => openCategoryModal());
    document.getElementById('categoryForm')?.addEventListener('submit', saveCategory);
    document.getElementById('batchForm')?.addEventListener('submit', saveBatch);
    document.getElementById('resetBatchFormBtn')?.addEventListener('click', resetBatchForm);
    document.getElementById('batchModal')?.addEventListener('hidden.bs.modal', () => {
        if (!returnToProductModalAfterBatch) return;
        returnToProductModalAfterBatch = false;
        bootstrap.Modal.getOrCreateInstance(document.getElementById('productModal')).show();
    });
    document.getElementById('refreshGalleryBtn')?.addEventListener('click', () => {
        const productId = value('productId');
        if (productId) loadGallery(productId);
    });
    document.getElementById('refreshInventoryAlerts')?.addEventListener('click', () => loadInventoryAlerts());
    document.getElementById('lowStockAlertThreshold')?.addEventListener('change', () => {
        if (currentInventoryAlertKind === 'low') loadInventoryAlerts();
    });
    document.querySelectorAll('[data-alert-kind]').forEach(button => {
        button.addEventListener('click', () => {
            currentInventoryAlertKind = button.dataset.alertKind;
            document.querySelectorAll('[data-alert-kind]').forEach(item => item.classList.toggle('active', item === button));
            loadInventoryAlerts();
        });
    });
}

function flattenCategories(items) {
    const flattened = [];
    const seen = new Set();
    const visit = item => {
        if (!item || seen.has(String(item.id))) return;
        seen.add(String(item.id));
        flattened.push(item);
        (item.children || []).forEach(child => visit({ ...child, parentId: child.parentId ?? item.id }));
    };
    items.forEach(visit);
    return flattened;
}

function renderBrands() {
    const body = document.getElementById('brandsTableBody');
    if (!body) return;
    body.replaceChildren();
    if (!brands.length) {
        body.appendChild(tableMessageRow('Chưa có thương hiệu.', 5));
        return;
    }
    brands.slice().sort((a, b) => String(a.name || '').localeCompare(String(b.name || ''), 'vi')).forEach(brand => {
        const row = document.createElement('tr');
        if (brand.active === false) row.className = 'table-secondary';
        const logoCell = document.createElement('td');
        if (brand.logoUrl) {
            const logo = document.createElement('img');
            logo.src = safeImage(brand.logoUrl);
            logo.alt = `Logo ${brand.name || ''}`;
            logo.loading = 'lazy';
            logo.style.cssText = 'width:48px;height:48px;object-fit:contain';
            logoCell.appendChild(logo);
        } else {
            logoCell.appendChild(textElement('span', 'text-muted', '—'));
        }
        const nameCell = document.createElement('td');
        nameCell.append(textElement('strong', 'd-block', brand.name || '—'), textElement('small', 'text-muted', brand.slug || '—'));
        const status = textElement('span', `badge ${brand.active === false ? 'bg-secondary' : 'bg-success'}`, brand.active === false ? 'Đã ẩn' : 'Hoạt động');
        const actions = document.createElement('td');
        actions.className = 'text-end';
        const edit = iconButton('Sửa thương hiệu', 'btn-outline-primary btn-sm', 'fa-pen');
        edit.addEventListener('click', () => openBrandModal(brand));
        const visibility = iconButton(brand.active === false ? 'Khôi phục thương hiệu' : 'Ẩn thương hiệu', `${brand.active === false ? 'btn-outline-success' : 'btn-outline-warning'} btn-sm`, brand.active === false ? 'fa-rotate-left' : 'fa-eye-slash');
        visibility.addEventListener('click', () => setBrandVisibility(brand));
        const group = document.createElement('div');
        group.className = 'btn-group btn-group-sm';
        group.append(edit, visibility);
        actions.appendChild(group);
        const statusCell = document.createElement('td');
        statusCell.appendChild(status);
        row.append(logoCell, nameCell, textElement('td', '', brand.country || '—'), statusCell, actions);
        body.appendChild(row);
    });
}

function openBrandModal(brand = null) {
    document.getElementById('brandForm').reset();
    setValue('brandId', brand?.id);
    setValue('brandName', brand?.name);
    setValue('brandSlug', brand?.slug);
    setValue('brandCountry', brand?.country);
    setValue('brandLogoUrl', brand?.logoUrl);
    setValue('brandDescription', brand?.description);
    document.getElementById('brandActive').checked = brand?.active !== false;
    document.getElementById('brandModalTitle').textContent = brand ? 'Sửa thương hiệu' : 'Thêm thương hiệu';
    bootstrap.Modal.getOrCreateInstance(document.getElementById('brandModal')).show();
}

async function saveBrand(event) {
    event.preventDefault();
    const form = event.currentTarget;
    if (!form.reportValidity()) return;
    const id = value('brandId');
    const payload = {
        name: value('brandName'), slug: nullable('brandSlug'), country: nullable('brandCountry'),
        logoUrl: nullable('brandLogoUrl'), description: nullable('brandDescription'),
        active: document.getElementById('brandActive').checked
    };
    await submitCatalogEntity(id ? `/api/admin/brands/${id}` : '/api/admin/brands', id ? 'PUT' : 'POST', payload, async () => {
        bootstrap.Modal.getInstance(document.getElementById('brandModal'))?.hide();
        await loadBrands();
        loadProducts();
    }, id ? 'Đã cập nhật thương hiệu.' : 'Đã tạo thương hiệu.');
}

async function setBrandVisibility(brand) {
    const active = brand.active === false;
    if (!confirm(`${active ? 'Khôi phục' : 'Ẩn'} thương hiệu “${brand.name || ''}”?`)) return;
    await submitCatalogEntity(`/api/admin/brands/${brand.id}/visibility?active=${active}`, 'PATCH', null, async () => {
        await loadBrands();
        loadProducts();
    }, active ? 'Đã khôi phục thương hiệu.' : 'Đã ẩn thương hiệu.');
}

function renderCategories() {
    const body = document.getElementById('categoriesTableBody');
    if (!body) return;
    body.replaceChildren();
    if (!categories.length) {
        body.appendChild(tableMessageRow('Chưa có danh mục.', 5));
        return;
    }
    const byId = new Map(categories.map(category => [String(category.id), category]));
    categories.slice().sort((a, b) => Number(a.displayOrder || 0) - Number(b.displayOrder || 0) || String(a.name || '').localeCompare(String(b.name || ''), 'vi')).forEach(category => {
        const row = document.createElement('tr');
        if (category.active === false) row.className = 'table-secondary';
        const nameCell = document.createElement('td');
        nameCell.append(textElement('strong', 'd-block', `${'— '.repeat(categoryDepth(category))}${category.name || '—'}`), textElement('small', 'text-muted', category.slug || '—'));
        const statusCell = document.createElement('td');
        statusCell.appendChild(textElement('span', `badge ${category.active === false ? 'bg-secondary' : 'bg-success'}`, category.active === false ? 'Đã ẩn' : 'Hoạt động'));
        const actions = document.createElement('td');
        actions.className = 'text-end';
        const edit = iconButton('Sửa danh mục', 'btn-outline-primary btn-sm', 'fa-pen');
        edit.addEventListener('click', () => openCategoryModal(category));
        const visibility = iconButton(category.active === false ? 'Khôi phục danh mục' : 'Ẩn danh mục', `${category.active === false ? 'btn-outline-success' : 'btn-outline-warning'} btn-sm`, category.active === false ? 'fa-rotate-left' : 'fa-eye-slash');
        visibility.addEventListener('click', () => setCategoryVisibility(category));
        const group = document.createElement('div');
        group.className = 'btn-group btn-group-sm';
        group.append(edit, visibility);
        actions.appendChild(group);
        const parent = byId.get(String(category.parentId));
        row.append(nameCell, textElement('td', parent?.active === false ? 'text-warning' : '', parent ? `${parent.name}${parent.active === false ? ' (đã ẩn)' : ''}` : '—'), textElement('td', '', String(category.displayOrder ?? 0)), statusCell, actions);
        body.appendChild(row);
    });
}

function categoryDepth(category) {
    const byId = new Map(categories.map(item => [String(item.id), item]));
    const visited = new Set([String(category.id)]);
    let parentId = category.parentId;
    let depth = 0;
    while (parentId != null && byId.has(String(parentId)) && !visited.has(String(parentId)) && depth < 5) {
        visited.add(String(parentId));
        parentId = byId.get(String(parentId)).parentId;
        depth += 1;
    }
    return depth;
}

function populateCategoryParentOptions(editingId = null) {
    const select = document.getElementById('categoryParent');
    if (!select) return;
    const excluded = new Set(editingId == null ? [] : [String(editingId)]);
    let changed = true;
    while (changed) {
        changed = false;
        categories.forEach(category => {
            if (category.parentId != null && excluded.has(String(category.parentId)) && !excluded.has(String(category.id))) {
                excluded.add(String(category.id));
                changed = true;
            }
        });
    }
    select.replaceChildren(option('', 'Không có'));
    categories.filter(category => !excluded.has(String(category.id))).forEach(category => {
        select.appendChild(option(category.id, `${'— '.repeat(categoryDepth(category))}${category.name}${category.active === false ? ' (đã ẩn)' : ''}`));
    });
}

function openCategoryModal(category = null) {
    document.getElementById('categoryForm').reset();
    populateCategoryParentOptions(category?.id);
    setValue('categoryId', category?.id);
    setValue('categoryName', category?.name);
    setValue('categorySlug', category?.slug);
    setValue('categoryParent', category?.parentId);
    setValue('categoryDisplayOrder', category?.displayOrder ?? 0);
    setValue('categoryIcon', category?.icon);
    setValue('categoryDescription', category?.description);
    document.getElementById('categoryActive').checked = category?.active !== false;
    document.getElementById('categoryModalTitle').textContent = category ? 'Sửa danh mục' : 'Thêm danh mục';
    bootstrap.Modal.getOrCreateInstance(document.getElementById('categoryModal')).show();
}

async function saveCategory(event) {
    event.preventDefault();
    const form = event.currentTarget;
    if (!form.reportValidity()) return;
    const id = value('categoryId');
    const payload = {
        name: value('categoryName'), slug: nullable('categorySlug'), description: nullable('categoryDescription'),
        icon: nullable('categoryIcon'), parentId: nullableNumber('categoryParent'),
        displayOrder: numberValue('categoryDisplayOrder', 0), active: document.getElementById('categoryActive').checked
    };
    await submitCatalogEntity(id ? `/api/admin/categories/${id}` : '/api/admin/categories', id ? 'PUT' : 'POST', payload, async () => {
        bootstrap.Modal.getInstance(document.getElementById('categoryModal'))?.hide();
        await loadCategories();
        loadProducts();
    }, id ? 'Đã cập nhật danh mục.' : 'Đã tạo danh mục.');
}

async function setCategoryVisibility(category) {
    const active = category.active === false;
    if (!confirm(`${active ? 'Khôi phục' : 'Ẩn'} danh mục “${category.name || ''}”?`)) return;
    await submitCatalogEntity(`/api/admin/categories/${category.id}/visibility?active=${active}`, 'PATCH', null, async () => {
        await loadCategories();
        loadProducts();
    }, active ? 'Đã khôi phục danh mục.' : 'Đã ẩn danh mục.');
}

async function submitCatalogEntity(url, method, payload, onSuccess, successMessage) {
    try {
        const options = { method, headers: tokenHeaders(payload != null) };
        if (payload != null) options.body = JSON.stringify(payload);
        const response = await fetch(url, options);
        if (!response.ok) throw new Error(await responseError(response, 'Không thể lưu thay đổi.'));
        showToast(successMessage, 'success');
        await onSuccess();
    } catch (error) {
        showToast(error.message, 'danger');
    }
}

async function responseError(response, fallback) {
    const payload = await response.json().catch(() => ({}));
    const fieldMessage = payload.fieldErrors && Object.values(payload.fieldErrors)[0];
    return fieldMessage || payload.message || payload.error || fallback;
}

async function loadProducts() {
    const tbody = document.getElementById('productsTableBody');
    showTableMessage('Đang tải dữ liệu...', 'spinner-border spinner-border-sm');
    const serverStockFiltered = Boolean(currentStockFilter && !currentSearch);
    const endpoint = serverStockFiltered ? `/api/admin/products/${currentStockFilter}` : '/api/admin/products';
    const params = new URLSearchParams({ page: currentPage, size: pageSize });
    if (!serverStockFiltered) params.set('includeInactive', currentIncludeInactive);
    if (currentSearch) params.set('search', currentSearch);
    if (currentStockFilter === 'low-stock' && serverStockFiltered) params.set('threshold', numberValue('lowStockAlertThreshold', 10));
    try {
        const response = await fetch(`${endpoint}?${params}`, { headers: tokenHeaders() });
        if (!response.ok) throw new Error('Không thể tải danh sách sản phẩm.');
        let data = await response.json();
        if (currentStockFilter && !serverStockFiltered) data = filterStockPage(data);
        renderProducts(data.content || []);
        updatePagination(data);
        document.getElementById('totalProducts').textContent = String(data.totalElements ?? data.content?.length ?? 0);
        const result = document.getElementById('searchResultText');
        if (result) {
            result.hidden = !currentSearch;
            result.textContent = currentSearch ? `Kết quả cho “${currentSearch}”` : '';
        }
    } catch (error) {
        showTableMessage(error.message, 'fas fa-circle-exclamation text-danger');
    }
}

function filterStockPage(data) {
    const threshold = numberValue('lowStockAlertThreshold', 10);
    const products = (data.content || []).filter(product => {
        const stock = productStock(product);
        if (currentStockFilter === 'out-of-stock') return stock <= 0;
        if (currentStockFilter === 'low-stock') return stock > 0 && stock <= threshold;
        return stock > 0;
    });
    return { ...data, content: products, totalElements: products.length, totalPages: 1 };
}

function renderProducts(products) {
    const tbody = document.getElementById('productsTableBody');
    tbody.replaceChildren();
    if (!products.length) {
        showTableMessage('Không có sản phẩm phù hợp.', 'fas fa-box-open');
        return;
    }
    products.forEach(product => tbody.appendChild(createProductRow(product)));
}

function createProductRow(product) {
    const variant = defaultVariant(product);
    const row = document.createElement('tr');
    if (product.active === false) row.className = 'table-secondary';
    const imageCell = document.createElement('td');
    const image = document.createElement('img');
    image.src = safeImage(variant.imageUrl || product.gallery?.[0]?.url);
    image.alt = product.name || 'Sản phẩm';
    image.style.cssText = 'width:60px;height:60px;object-fit:cover;border-radius:8px';
    imageCell.appendChild(image);

    const nameCell = document.createElement('td');
    const name = textElement('strong', '', product.name || 'Sản phẩm');
    const sku = textElement('div', 'small text-muted mt-1', `SKU: ${variant.sku || '—'}`);
    nameCell.append(name, sku);
    if ((product.variants || []).length > 1) nameCell.appendChild(textElement('span', 'badge bg-light text-dark border mt-1', `${product.variants.length} biến thể`));

    const categoryHidden = product.category?.active === false
        || (product.categoryBreadcrumb || []).some(category => category.active === false);
    const brandHidden = product.brand?.active === false;
    const categoryCell = textElement('td', categoryHidden ? 'text-warning' : '',
        `${product.category?.name || '—'}${categoryHidden ? ' (đã ẩn)' : ''}`);
    const brandCell = textElement('td', brandHidden ? 'text-warning' : '',
        `${product.brand?.name || product.brandName || '—'}${brandHidden ? ' (đã ẩn)' : ''}`);
    const priceCell = document.createElement('td');
    priceCell.appendChild(textElement('strong', variant.discountPrice ? 'text-danger' : '', formatPrice(variant.discountPrice || variant.price || product.minPrice)));
    if (variant.discountPrice) priceCell.appendChild(textElement('div', 'small text-muted text-decoration-line-through', formatPrice(variant.price)));
    const stockCell = document.createElement('td');
    const stock = productStock(product);
    stockCell.append(textElement('strong', '', String(stock)), stockBadge(stock));
    const statusCell = document.createElement('td');
    statusCell.appendChild(textElement('span', `badge ${product.active === false ? 'bg-secondary' : 'bg-success'}`, product.active === false ? 'Đã ẩn' : 'Đang bán'));
    const actionCell = document.createElement('td');
    const group = document.createElement('div');
    group.className = 'btn-group btn-group-sm';
    const edit = iconButton('Sửa sản phẩm', 'btn-outline-primary', 'fa-pen');
    edit.addEventListener('click', () => editProduct(product.id));
    const visibility = iconButton(product.active === false ? 'Khôi phục' : 'Ẩn khỏi cửa hàng', product.active === false ? 'btn-outline-success' : 'btn-outline-warning', product.active === false ? 'fa-rotate-left' : 'fa-eye-slash');
    visibility.addEventListener('click', () => toggleProductVisibility(product, product.active === false));
    group.append(edit, visibility);
    actionCell.appendChild(group);
    row.append(imageCell, nameCell, categoryCell, brandCell, priceCell, stockCell, statusCell, actionCell);
    return row;
}

function stockBadge(stock) {
    const badge = textElement('div', 'mt-1', '');
    badge.appendChild(textElement('span', `badge ${stock <= 0 ? 'bg-danger' : stock <= 10 ? 'bg-warning text-dark' : 'bg-success'}`, stock <= 0 ? 'Hết hàng' : stock <= 10 ? 'Sắp hết' : 'Còn hàng'));
    return badge;
}

function iconButton(title, style, icon) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = `btn ${style}`;
    button.title = title;
    button.setAttribute('aria-label', title);
    const i = document.createElement('i');
    i.className = `fas ${icon}`;
    button.appendChild(i);
    return button;
}

function openAddModal() {
    editingProduct = null;
    document.getElementById('modalTitle').textContent = 'Thêm sản phẩm mỹ phẩm';
    document.getElementById('productForm').reset();
    document.getElementById('productId').value = '';
    document.getElementById('editingVariantId').value = '';
    document.getElementById('productActive').checked = true;
    document.getElementById('productShadeHex').value = '#a93f68';
    document.getElementById('productLowStockThreshold').value = '10';
    document.getElementById('productStock').disabled = false;
    document.getElementById('existingVariantsSection').classList.add('d-none');
    document.getElementById('galleryManagementSection').classList.add('d-none');
    document.getElementById('galleryList').replaceChildren();
    document.getElementById('productImageVariantScope').replaceChildren(option('', 'Gallery chung của sản phẩm'));
    removeImage();
    bootstrap.Modal.getOrCreateInstance(document.getElementById('productModal')).show();
}

async function editProduct(id) {
    try {
        const response = await fetch(`/api/admin/products/${id}`, { headers: tokenHeaders() });
        if (!response.ok) throw new Error('Không thể tải thông tin sản phẩm.');
        editingProduct = await response.json();
        document.getElementById('modalTitle').textContent = 'Sửa sản phẩm mỹ phẩm';
        fillProductForm(editingProduct);
        await Promise.allSettled([loadVariants(id), loadGallery(id)]);
        bootstrap.Modal.getOrCreateInstance(document.getElementById('productModal')).show();
    } catch (error) { showToast(error.message, 'danger'); }
}

function fillProductForm(product) {
    const variant = defaultVariant(product);
    setValue('productId', product.id);
    setValue('editingVariantId', variant.id);
    setValue('productName', product.name);
    setValue('productSlug', product.slug);
    setValue('productDescription', product.description);
    setValue('productBenefits', product.benefits);
    setValue('productInci', product.inci);
    setValue('productDirections', product.directions);
    setValue('productWarnings', product.warnings);
    setValue('productBrand', product.brand?.id || '');
    setValue('productCategory', product.category?.id || '');
    setValue('productOrigin', product.origin);
    setValue('productMaterial', product.material);
    setValue('productSpf', product.spf);
    setValue('productPaoMonths', product.paoMonths);
    setValue('productShelfLifeMonths', product.shelfLifeMonths);
    setValue('productWarrantyMonths', product.warrantyMonths);
    setValue('productSpecifications', product.specifications);
    document.getElementById('productFeatured').checked = Boolean(product.featured);
    document.getElementById('productActive').checked = product.active !== false;
    fillVariantForm(variant, product);
    const imageUrl = variant.imageUrl || product.gallery?.[0]?.url;
    if (imageUrl) showExistingImage(imageUrl);
    else removeImage();
}

function fillVariantForm(variant, product = editingProduct || {}) {
    setValue('editingVariantId', variant.id);
    setValue('productSku', variant.sku);
    setValue('productBarcode', variant.barcode);
    setValue('productVariantLabel', variant.label);
    setValue('productShadeName', variant.shadeName);
    setValue('productShadeHex', /^#[0-9a-f]{6}$/i.test(variant.shadeHex || '') ? variant.shadeHex : '#a93f68');
    setValue('productVolume', variant.sizeValue ?? variant.volume);
    setValue('productUnit', variant.sizeUnit || variant.volumeUnit || 'ml');
    setValue('productPrice', variant.price ?? product.minPrice);
    setValue('productDiscountPrice', variant.discountPrice);
    setValue('productStock', variant.availableStock ?? product.totalAvailableStock ?? 0);
    document.getElementById('productStock').disabled = true;
    setValue('productLowStockThreshold', variant.lowStockThreshold ?? 10);
}

async function saveProduct() {
    const form = document.getElementById('productForm');
    if (!form.reportValidity()) return;
    const id = value('productId');
    const variant = variantPayload();
    if (variant.discountPrice != null && variant.discountPrice >= variant.price) {
        showToast('Giá khuyến mãi phải thấp hơn giá gốc.', 'warning');
        return;
    }
    const payload = productPayload();
    if (!id) payload.defaultVariant = { ...variant, defaultVariant: true, active: true };
    const initialStock = id ? 0 : numberValue('productStock', 0);
    const saveButton = document.querySelector('#productModal .modal-footer .btn-primary');
    saveButton.disabled = true;
    saveButton.textContent = 'Đang lưu...';
    try {
        const response = await fetch(id ? `/api/admin/products/${id}` : '/api/admin/products', {
            method: id ? 'PUT' : 'POST', headers: tokenHeaders(true), body: JSON.stringify(payload)
        });
        const result = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(result.message || result.error || 'Không thể lưu sản phẩm.');
        if (id && value('editingVariantId')) await saveExistingVariant(value('editingVariantId'), variant);
        if (!id && initialStock > 0) await createInitialInventoryBatch(result, initialStock);
        const image = document.getElementById('productImageFile').files[0];
        if (image) await uploadProductImage(result.id || id, image, !result.gallery?.length);
        showToast(id ? 'Đã cập nhật sản phẩm.' : 'Đã tạo sản phẩm.', 'success');
        bootstrap.Modal.getInstance(document.getElementById('productModal'))?.hide();
        loadProducts();
    } catch (error) { showToast(error.message, 'danger'); }
    finally {
        saveButton.disabled = false;
        const icon = document.createElement('i');
        icon.className = 'fas fa-save me-2';
        saveButton.replaceChildren(icon, document.createTextNode('Lưu'));
    }
}

async function createInitialInventoryBatch(product, quantity) {
    const variant = defaultVariant(product);
    if (!variant.id) throw new Error('Sản phẩm đã tạo nhưng chưa xác định được biến thể để nhập tồn kho.');
    const shelfLifeMonths = Math.max(1, numberValue('productShelfLifeMonths', 36));
    const payload = {
        batchCode: `INITIAL-${variant.sku}`,
        manufacturedDate: todayIso(),
        expiryDate: dateAfterMonthsIso(shelfLifeMonths),
        quantityOnHand: quantity,
        active: true
    };
    const response = await fetch(`/api/admin/variants/${variant.id}/batches`, {
        method: 'POST', headers: tokenHeaders(true), body: JSON.stringify(payload)
    });
    if (!response.ok) {
        throw new Error(await responseError(response, 'Sản phẩm đã tạo nhưng không thể nhập lô tồn kho ban đầu.'));
    }
}

function productPayload() {
    return {
        name: value('productName'), slug: nullable('productSlug'), brandId: nullableNumber('productBrand'), categoryId: nullableNumber('productCategory'),
        description: nullable('productDescription'), benefits: nullable('productBenefits'), inci: nullable('productInci'),
        directions: nullable('productDirections'), warnings: nullable('productWarnings'), origin: nullable('productOrigin'), material: nullable('productMaterial'),
        warrantyMonths: nullableNumber('productWarrantyMonths'), specifications: nullable('productSpecifications'), spf: nullable('productSpf'),
        paoMonths: nullableNumber('productPaoMonths'), shelfLifeMonths: nullableNumber('productShelfLifeMonths'), facetIds: [],
        featured: document.getElementById('productFeatured').checked, active: document.getElementById('productActive').checked
    };
}

function variantPayload() {
    return {
        sku: value('productSku').toUpperCase(), barcode: nullable('productBarcode'), label: nullable('productVariantLabel'),
        shadeName: nullable('productShadeName'), shadeHex: value('productShadeName') ? value('productShadeHex') : null,
        sizeValue: nullableNumber('productVolume'), sizeUnit: nullable('productUnit'), price: numberValue('productPrice', 0),
        discountPrice: nullableNumber('productDiscountPrice'), lowStockThreshold: numberValue('productLowStockThreshold', 10), active: true
    };
}

async function saveExistingVariant(id, payload) {
    const response = await fetch(`/api/admin/variants/${id}`, { method: 'PUT', headers: tokenHeaders(true), body: JSON.stringify({ ...payload, defaultVariant: true }) });
    if (!response.ok) {
        const error = await response.json().catch(() => ({}));
        throw new Error(error.message || 'Sản phẩm đã lưu nhưng không thể cập nhật biến thể.');
    }
}

async function createVariantFromForm() {
    const productId = value('productId');
    if (!productId) { showToast('Hãy lưu sản phẩm trước khi thêm biến thể.', 'warning'); return; }
    const payload = { ...variantPayload(), defaultVariant: false };
    if (!payload.sku) { showToast('SKU biến thể là bắt buộc.', 'warning'); return; }
    try {
        const response = await fetch(`/api/admin/products/${productId}/variants`, { method: 'POST', headers: tokenHeaders(true), body: JSON.stringify(payload) });
        const result = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(result.message || 'Không thể tạo biến thể.');
        showToast('Đã tạo biến thể. Bạn có thể nhập lô tồn kho ngay.', 'success');
        await loadVariants(productId);
        openBatchModal(result);
    } catch (error) { showToast(error.message, 'danger'); }
}

async function loadVariants(productId) {
    const section = document.getElementById('existingVariantsSection');
    const body = document.getElementById('variantsTableBody');
    section.classList.remove('d-none');
    body.replaceChildren();
    try {
        const response = await fetch(`/api/admin/products/${productId}/variants?includeInactive=true`, { headers: tokenHeaders() });
        if (!response.ok) throw new Error();
        const variants = await response.json();
        populateImageVariantOptions(variants);
        variants.forEach(variant => {
            variantDetailsCache.set(String(variant.id), variant);
            body.appendChild(createVariantRow(variant));
        });
        if (!variants.length) body.appendChild(tableMessageRow('Chưa có biến thể.', 5));
    } catch (_) { body.appendChild(tableMessageRow('Không thể tải biến thể.', 5)); }
}

function populateImageVariantOptions(variants) {
    const select = document.getElementById('productImageVariantScope');
    if (!select) return;
    const selected = select.value;
    select.replaceChildren(option('', 'Gallery chung của sản phẩm'));
    variants.forEach(variant => select.appendChild(option(variant.id, `${variant.sku || `Variant #${variant.id}`}${variant.active === false ? ' (đã ẩn)' : ''}`)));
    if ([...select.options].some(item => item.value === selected)) select.value = selected;
}

function createVariantRow(variant) {
    const row = document.createElement('tr');
    row.append(textElement('td', '', variant.sku), textElement('td', '', variant.label || [variant.shadeName, variant.sizeValue && `${variant.sizeValue} ${variant.sizeUnit || ''}`].filter(Boolean).join(' · ') || 'Tiêu chuẩn'), textElement('td', '', formatPrice(variant.discountPrice || variant.price)), textElement('td', '', variant.active === false ? 'Đã ẩn' : 'Đang bán'));
    const actions = document.createElement('td');
    const edit = iconButton('Sửa biến thể', 'btn-outline-primary btn-sm', 'fa-pen');
    edit.addEventListener('click', () => fillVariantForm(variant));
    const visibility = iconButton(variant.active === false ? 'Khôi phục biến thể' : 'Ẩn biến thể', `${variant.active === false ? 'btn-outline-success' : 'btn-outline-warning'} btn-sm`, variant.active === false ? 'fa-rotate-left' : 'fa-eye-slash');
    visibility.addEventListener('click', () => toggleVariant(variant));
    const batches = iconButton('Quản lý lô tồn kho', 'btn-outline-info btn-sm', 'fa-boxes-stacked');
    batches.addEventListener('click', () => openBatchModal(variant));
    actions.append(edit, batches, visibility);
    row.appendChild(actions);
    return row;
}

async function toggleVariant(variant) {
    const active = variant.active === false;
    try {
        const response = await fetch(`/api/admin/variants/${variant.id}/visibility?active=${active}`, { method: 'PATCH', headers: tokenHeaders() });
        if (!response.ok) throw new Error();
        loadVariants(value('productId'));
        loadInventoryAlerts();
    } catch (_) { showToast('Không thể đổi trạng thái biến thể.', 'danger'); }
}

async function openBatchModal(variant) {
    currentBatchVariant = variant;
    variantDetailsCache.set(String(variant.id), variant);
    setValue('batchVariantId', variant.id);
    document.getElementById('batchVariantLabel').textContent = `${variant.sku || 'SKU chưa đặt'}${variant.label ? ` · ${variant.label}` : ''}`;
    resetBatchForm();
    const showBatchModal = () => bootstrap.Modal.getOrCreateInstance(document.getElementById('batchModal')).show();
    const productModalElement = document.getElementById('productModal');
    if (productModalElement.classList.contains('show')) {
        returnToProductModalAfterBatch = true;
        productModalElement.addEventListener('hidden.bs.modal', showBatchModal, { once: true });
        bootstrap.Modal.getInstance(productModalElement)?.hide();
    } else {
        returnToProductModalAfterBatch = false;
        showBatchModal();
    }
    await loadBatches(variant.id);
}

function resetBatchForm() {
    const variantId = currentBatchVariant?.id || value('batchVariantId');
    document.getElementById('batchForm').reset();
    setValue('batchId', '');
    setValue('batchVariantId', variantId);
    setValue('batchQuantityReserved', 0);
    document.getElementById('batchActive').checked = true;
    document.getElementById('saveBatchBtn').textContent = 'Lưu lô mới';
}

async function loadBatches(variantId) {
    const body = document.getElementById('batchesTableBody');
    body.replaceChildren(tableMessageRow('Đang tải dữ liệu lô...', 8));
    try {
        const response = await fetch(`/api/admin/variants/${encodeURIComponent(variantId)}/batches`, { headers: tokenHeaders() });
        if (!response.ok) throw new Error(await responseError(response, 'Không thể tải dữ liệu lô.'));
        const batches = await response.json();
        body.replaceChildren();
        batches.forEach(batch => body.appendChild(createBatchRow(batch)));
        if (!batches.length) body.appendChild(tableMessageRow('Biến thể này chưa có lô tồn kho.', 8));
    } catch (error) {
        body.replaceChildren(tableMessageRow(error.message, 8));
    }
}

function createBatchRow(batch) {
    const row = document.createElement('tr');
    if (batch.active === false) row.className = 'table-secondary';
    const statusCell = document.createElement('td');
    statusCell.appendChild(batchStatusBadge(batch));
    const actions = document.createElement('td');
    actions.className = 'text-end';
    const edit = iconButton('Sửa lô', 'btn-outline-primary btn-sm', 'fa-pen');
    edit.addEventListener('click', () => fillBatchForm(batch));
    const visibility = iconButton(batch.active === false ? 'Khôi phục lô' : 'Ẩn lô', `${batch.active === false ? 'btn-outline-success' : 'btn-outline-warning'} btn-sm`, batch.active === false ? 'fa-rotate-left' : 'fa-eye-slash');
    visibility.addEventListener('click', () => toggleBatchVisibility(batch));
    const group = document.createElement('div');
    group.className = 'btn-group btn-group-sm';
    group.append(edit, visibility);
    actions.appendChild(group);
    row.append(
        textElement('td', 'fw-semibold', batch.batchCode || '—'),
        textElement('td', '', formatDateOnly(batch.manufacturedDate)),
        textElement('td', '', formatDateOnly(batch.expiryDate)),
        textElement('td', '', String(batch.quantityOnHand ?? 0)),
        textElement('td', '', String(batch.quantityReserved ?? 0)),
        textElement('td', 'fw-semibold', String(batch.availableQuantity ?? Math.max(0, Number(batch.quantityOnHand || 0) - Number(batch.quantityReserved || 0)))),
        statusCell,
        actions
    );
    return row;
}

function fillBatchForm(batch) {
    setValue('batchId', batch.id);
    setValue('batchVariantId', batch.variantId);
    setValue('batchCode', batch.batchCode);
    setValue('batchManufacturedDate', batch.manufacturedDate);
    setValue('batchExpiryDate', batch.expiryDate);
    setValue('batchQuantityOnHand', batch.quantityOnHand);
    setValue('batchQuantityReserved', batch.quantityReserved ?? 0);
    document.getElementById('batchActive').checked = batch.active !== false;
    document.getElementById('saveBatchBtn').textContent = 'Cập nhật lô';
    document.getElementById('batchCode').focus();
}

async function saveBatch(event) {
    event.preventDefault();
    const form = event.currentTarget;
    if (!form.reportValidity()) return;
    const id = value('batchId');
    const variantId = value('batchVariantId');
    const manufacturedDate = nullable('batchManufacturedDate');
    const expiryDate = value('batchExpiryDate');
    const quantityOnHand = numberValue('batchQuantityOnHand', 0);
    if (manufacturedDate && manufacturedDate > expiryDate) {
        showToast('Ngày sản xuất không được sau hạn sử dụng.', 'warning');
        return;
    }
    if (!id && expiryDate < todayIso()) {
        showToast('Không thể nhập lô mới đã hết hạn.', 'warning');
        return;
    }
    const payload = {
        batchCode: value('batchCode').toUpperCase(), manufacturedDate, expiryDate,
        quantityOnHand, active: document.getElementById('batchActive').checked
    };
    const button = document.getElementById('saveBatchBtn');
    button.disabled = true;
    try {
        const response = await fetch(id ? `/api/admin/batches/${id}` : `/api/admin/variants/${variantId}/batches`, {
            method: id ? 'PUT' : 'POST', headers: tokenHeaders(true), body: JSON.stringify(payload)
        });
        if (!response.ok) throw new Error(await responseError(response, 'Không thể lưu lô tồn kho.'));
        showToast(id ? 'Đã cập nhật lô tồn kho.' : 'Đã thêm lô tồn kho.', 'success');
        resetBatchForm();
        await Promise.allSettled([loadBatches(variantId), loadInventoryAlerts(), loadProducts()]);
    } catch (error) {
        showToast(error.message, 'danger');
    } finally {
        button.disabled = false;
    }
}

async function toggleBatchVisibility(batch) {
    const active = batch.active === false;
    if (!confirm(`${active ? 'Khôi phục' : 'Ẩn'} lô “${batch.batchCode || ''}”?`)) return;
    try {
        const response = await fetch(`/api/admin/batches/${batch.id}/visibility?active=${active}`, { method: 'PATCH', headers: tokenHeaders() });
        if (!response.ok) throw new Error(await responseError(response, 'Không thể đổi trạng thái lô.'));
        showToast(active ? 'Đã khôi phục lô.' : 'Đã ẩn lô.', 'success');
        await Promise.allSettled([loadBatches(batch.variantId), loadInventoryAlerts(), loadProducts()]);
    } catch (error) {
        showToast(error.message, 'danger');
    }
}

function batchStatusBadge(batch) {
    let label = 'Đang bán';
    let style = 'bg-success';
    if (batch.active === false) {
        label = 'Đã ẩn';
        style = 'bg-secondary';
    } else if (batch.expiryDate && batch.expiryDate < todayIso()) {
        label = 'Đã hết hạn';
        style = 'bg-dark';
    } else if (batch.expiryDate && batch.expiryDate <= dateAfterDaysIso(90)) {
        label = 'Sắp hết hạn';
        style = 'bg-info text-dark';
    } else if (Number(batch.availableQuantity ?? 0) <= 0) {
        label = 'Không khả dụng';
        style = 'bg-danger';
    }
    return textElement('span', `badge ${style}`, label);
}

async function uploadProductImage(productId, file, firstImage) {
    const form = new FormData();
    form.append('image', file);
    form.append('altText', value('productName'));
    form.append('primary', String(Boolean(firstImage)));
    form.append('sortOrder', '0');
    if (value('productImageVariantScope')) form.append('variantId', value('productImageVariantScope'));
    const response = await fetch(`/api/admin/products/${productId}/images/upload`, { method: 'POST', headers: tokenHeaders(), body: form });
    if (!response.ok) throw new Error('Sản phẩm đã lưu nhưng tải ảnh thất bại.');
}

async function loadGallery(productId) {
    const section = document.getElementById('galleryManagementSection');
    const list = document.getElementById('galleryList');
    section.classList.remove('d-none');
    list.replaceChildren(textElement('p', 'text-muted col-12', 'Đang tải gallery...'));
    try {
        const response = await fetch(`/api/admin/products/${encodeURIComponent(productId)}/images`, { headers: tokenHeaders() });
        if (!response.ok) throw new Error(await responseError(response, 'Không thể tải gallery.'));
        const images = await response.json();
        list.replaceChildren();
        images.forEach(image => list.appendChild(createGalleryCard(productId, image)));
        if (!images.length) list.appendChild(textElement('p', 'text-muted col-12', 'Sản phẩm chưa có ảnh trong gallery.'));
    } catch (error) {
        list.replaceChildren(textElement('p', 'text-danger col-12', error.message));
    }
}

function createGalleryCard(productId, imageData) {
    const column = document.createElement('div');
    column.className = 'col-sm-6 col-lg-4';
    const card = document.createElement('article');
    card.className = 'card h-100 gallery-admin-card';
    const image = document.createElement('img');
    image.className = 'card-img-top';
    image.src = safeImage(imageData.url);
    image.alt = imageData.altText || 'Ảnh sản phẩm';
    image.loading = 'lazy';
    const body = document.createElement('div');
    body.className = 'card-body p-3';
    const altLabel = textElement('label', 'form-label small mb-1', 'Alt text');
    const alt = document.createElement('input');
    alt.className = 'form-control form-control-sm mb-2';
    alt.value = imageData.altText || '';
    alt.maxLength = 300;
    alt.required = true;
    altLabel.htmlFor = `galleryAlt-${imageData.id}`;
    alt.id = `galleryAlt-${imageData.id}`;
    const orderRow = document.createElement('div');
    orderRow.className = 'd-flex align-items-center gap-2 mb-2';
    const orderLabel = textElement('label', 'form-label small mb-0', 'Thứ tự');
    const order = document.createElement('input');
    order.className = 'form-control form-control-sm';
    order.id = `galleryOrder-${imageData.id}`;
    order.type = 'number';
    order.min = '0';
    order.value = String(imageData.sortOrder ?? 0);
    order.style.maxWidth = '90px';
    orderLabel.htmlFor = order.id;
    orderRow.append(orderLabel, order);
    const metadata = document.createElement('div');
    metadata.className = 'd-flex flex-wrap gap-1 mb-3';
    if (imageData.variantId) metadata.appendChild(textElement('span', 'badge bg-light text-dark border', `Variant #${imageData.variantId}`));
    if (imageData.primary) metadata.appendChild(textElement('span', 'badge bg-success', 'Ảnh chính'));
    const actions = document.createElement('div');
    actions.className = 'd-flex flex-wrap gap-2';
    const save = textElement('button', 'btn btn-sm btn-outline-primary flex-grow-1', 'Lưu nội dung/thứ tự');
    save.type = 'button';
    save.addEventListener('click', () => updateGalleryImage(productId, imageData, alt, order, save));
    const primary = textElement('button', 'btn btn-sm btn-outline-success flex-grow-1', imageData.primary ? 'Đang là ảnh chính' : 'Đặt ảnh chính');
    primary.type = 'button';
    primary.disabled = Boolean(imageData.primary);
    primary.addEventListener('click', () => setGalleryPrimary(productId, imageData.id));
    const remove = textElement('button', 'btn btn-sm btn-outline-danger', 'Xóa');
    remove.type = 'button';
    remove.addEventListener('click', () => deleteGalleryImage(productId, imageData));
    actions.append(save, primary, remove);
    body.append(altLabel, alt, orderRow, metadata, actions);
    card.append(image, body);
    column.appendChild(card);
    return column;
}

async function updateGalleryImage(productId, imageData, altInput, orderInput, button) {
    const altText = altInput.value.trim();
    const sortOrder = Math.max(0, Number(orderInput.value) || 0);
    if (!altText) {
        altInput.focus();
        showToast('Alt text của ảnh không được để trống.', 'warning');
        return;
    }
    button.disabled = true;
    try {
        const response = await fetch(`/api/admin/products/${productId}/images/${imageData.id}`, {
            method: 'PUT', headers: tokenHeaders(true), body: JSON.stringify({
                url: imageData.url, altText, sortOrder, primary: Boolean(imageData.primary), variantId: imageData.variantId ?? null
            })
        });
        if (!response.ok) throw new Error(await responseError(response, 'Không thể cập nhật ảnh.'));
        showToast('Đã cập nhật nội dung và thứ tự ảnh.', 'success');
        await loadGallery(productId);
    } catch (error) {
        showToast(error.message, 'danger');
        button.disabled = false;
    }
}

async function setGalleryPrimary(productId, imageId) {
    try {
        const response = await fetch(`/api/admin/products/${productId}/images/${imageId}/primary`, { method: 'PATCH', headers: tokenHeaders() });
        if (!response.ok) throw new Error(await responseError(response, 'Không thể đặt ảnh chính.'));
        showToast('Đã cập nhật ảnh chính.', 'success');
        await loadGallery(productId);
    } catch (error) {
        showToast(error.message, 'danger');
    }
}

async function deleteGalleryImage(productId, imageData) {
    if (!confirm(`Xóa ảnh “${imageData.altText || `#${imageData.id}`}”? Asset Cloudinary tương ứng cũng sẽ được dọn.`)) return;
    try {
        const response = await fetch(`/api/admin/products/${productId}/images/${imageData.id}`, { method: 'DELETE', headers: tokenHeaders() });
        if (!response.ok) throw new Error(await responseError(response, 'Không thể xóa ảnh.'));
        showToast('Đã xóa ảnh khỏi gallery.', 'success');
        await loadGallery(productId);
    } catch (error) {
        showToast(error.message, 'danger');
    }
}

async function loadInventoryAlerts(kind = currentInventoryAlertKind) {
    currentInventoryAlertKind = kind;
    const body = document.getElementById('inventoryAlertsBody');
    const summary = document.getElementById('inventoryAlertSummary');
    if (!body || !summary) return;
    body.replaceChildren(tableMessageRow('Đang tải cảnh báo tồn kho...', 8));
    summary.textContent = '';
    try {
        if (kind === 'low' || kind === 'out') {
            const threshold = Math.max(1, numberValue('lowStockAlertThreshold', 10));
            const path = kind === 'low' ? `/api/admin/products/low-stock?page=0&size=200&threshold=${threshold}` : '/api/admin/products/out-of-stock?page=0&size=200';
            const response = await fetch(path, { headers: tokenHeaders() });
            if (!response.ok) throw new Error(await responseError(response, 'Không thể tải cảnh báo tồn kho.'));
            const page = await response.json();
            const rows = productStockAlertRows(page.content || [], kind, threshold);
            body.replaceChildren(...rows);
            if (!rows.length) body.appendChild(tableMessageRow(kind === 'low' ? 'Không có sản phẩm sắp hết.' : 'Không có sản phẩm hết hàng.', 8));
            summary.textContent = `${kind === 'low' ? 'Sắp hết' : 'Hết hàng'}: ${page.totalElements ?? rows.length} sản phẩm${Number(page.totalElements || 0) > 200 ? ' (đang hiển thị 200 sản phẩm đầu)' : ''}.`;
            return;
        }

        const path = kind === 'expired' ? '/api/admin/inventory/expired' : '/api/admin/inventory/expiring?days=90';
        const response = await fetch(path, { headers: tokenHeaders() });
        if (!response.ok) throw new Error(await responseError(response, 'Không thể tải cảnh báo hạn sử dụng.'));
        const batches = await response.json();
        const variants = await loadVariantDetailsForBatches(batches);
        const rows = batches.map(batch => createInventoryBatchAlertRow(batch, variants.get(String(batch.variantId))));
        body.replaceChildren(...rows);
        if (!rows.length) body.appendChild(tableMessageRow(kind === 'expired' ? 'Không có lô hết hạn.' : 'Không có lô hết hạn trong 90 ngày tới.', 8));
        summary.textContent = `${kind === 'expired' ? 'Đã hết hạn' : 'Hết hạn trong 90 ngày'}: ${rows.length} lô.`;
    } catch (error) {
        body.replaceChildren(tableMessageRow(error.message, 8));
        summary.textContent = 'Không thể cập nhật cảnh báo.';
    }
}

function productStockAlertRows(products, kind, threshold) {
    const rows = [];
    products.forEach(product => {
        let variants = (product.variants || []).filter(variant => variant.active !== false);
        if (!variants.length) variants = [defaultVariant(product)];
        const matching = variants.filter(variant => {
            const stock = Number(variant.availableStock ?? product.totalAvailableStock ?? 0);
            return kind === 'out' ? stock <= 0 : stock > 0 && stock <= Number(variant.lowStockThreshold ?? threshold);
        });
        (matching.length ? matching : [defaultVariant(product)]).forEach(variant => rows.push(createProductStockAlertRow(product, variant, kind)));
    });
    return rows;
}

function createProductStockAlertRow(product, variant, kind) {
    const row = document.createElement('tr');
    const identity = document.createElement('td');
    identity.append(textElement('strong', 'd-block', product.name || 'Sản phẩm'), textElement('small', 'text-muted', variant.sku || '—'));
    const stock = Number(variant.availableStock ?? product.totalAvailableStock ?? 0);
    const status = document.createElement('td');
    status.appendChild(textElement('span', `badge ${kind === 'out' ? 'bg-danger' : 'bg-warning text-dark'}`, kind === 'out' ? 'Hết hàng' : 'Sắp hết'));
    const actions = document.createElement('td');
    actions.className = 'text-end';
    const edit = textElement('button', 'btn btn-sm btn-outline-primary', 'Mở sản phẩm');
    edit.type = 'button';
    edit.addEventListener('click', () => editProduct(product.id));
    actions.appendChild(edit);
    row.append(identity, textElement('td', 'text-muted', '—'), textElement('td', 'text-muted', '—'), textElement('td', 'text-muted', '—'), textElement('td', 'text-muted', '—'), textElement('td', 'fw-semibold', String(stock)), status, actions);
    return row;
}

async function loadVariantDetailsForBatches(batches) {
    const ids = [...new Set(batches.map(batch => String(batch.variantId)).filter(Boolean))];
    await Promise.allSettled(ids.map(async id => {
        if (variantDetailsCache.has(id)) return;
        const response = await fetch(`/api/admin/variants/${encodeURIComponent(id)}`, { headers: tokenHeaders() });
        if (response.ok) variantDetailsCache.set(id, await response.json());
    }));
    return variantDetailsCache;
}

function createInventoryBatchAlertRow(batch, variant) {
    const row = document.createElement('tr');
    const identity = document.createElement('td');
    identity.append(textElement('strong', 'd-block', variant?.label || variant?.shadeName || 'Biến thể'), textElement('small', 'text-muted', variant?.sku || `Variant #${batch.variantId}`));
    const status = document.createElement('td');
    status.appendChild(batchStatusBadge(batch));
    const actions = document.createElement('td');
    actions.className = 'text-end';
    const manage = textElement('button', 'btn btn-sm btn-outline-primary', 'Quản lý lô');
    manage.type = 'button';
    manage.addEventListener('click', () => openBatchModal(variant || { id: batch.variantId, sku: `Variant #${batch.variantId}` }));
    actions.appendChild(manage);
    row.append(
        identity,
        textElement('td', 'fw-semibold', batch.batchCode || '—'),
        textElement('td', '', formatDateOnly(batch.expiryDate)),
        textElement('td', '', String(batch.quantityOnHand ?? 0)),
        textElement('td', '', String(batch.quantityReserved ?? 0)),
        textElement('td', 'fw-semibold', String(batch.availableQuantity ?? 0)),
        status,
        actions
    );
    return row;
}

async function toggleProductVisibility(product, makeActive) {
    const id = Number(product?.id);
    if (!Number.isSafeInteger(id) || id <= 0) return;
    const warnings = [];
    if (makeActive && product.brand?.active === false) warnings.push('thương hiệu vẫn đang ẩn');
    if (makeActive && (product.category?.active === false
            || (product.categoryBreadcrumb || []).some(category => category.active === false))) {
        warnings.push('danh mục hoặc danh mục cha vẫn đang ẩn');
    }
    if (makeActive && !(product.variants || []).some(variant => variant.active !== false)) {
        warnings.push('sản phẩm chưa có biến thể đang hoạt động; hãy khôi phục biến thể trước');
    }
    const warningText = warnings.length ? `\n\nLưu ý: ${warnings.join('; ')}.` : '';
    if (!confirm(`Bạn có chắc muốn ${makeActive ? 'khôi phục' : 'ẩn'} sản phẩm này?${warningText}`)) return;
    try {
        const response = await fetch(`/api/admin/products/${id}/visibility?active=${makeActive}`, { method: 'PATCH', headers: tokenHeaders() });
        if (!response.ok) throw new Error();
        showToast(makeActive ? 'Đã khôi phục sản phẩm.' : 'Đã ẩn sản phẩm.', 'success');
        loadProducts();
    } catch (_) { showToast('Không thể đổi trạng thái sản phẩm.', 'danger'); }
}

function previewImage(event) {
    const file = event.target.files?.[0];
    if (!file) return;
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size > 10 * 1024 * 1024) {
        showToast('Ảnh phải là JPEG, PNG hoặc WebP và không vượt quá 10MB.', 'warning');
        event.target.value = '';
        return;
    }
    document.getElementById('imagePreview').src = URL.createObjectURL(file);
    document.getElementById('imagePreviewContainer').style.display = 'block';
    document.getElementById('imageFileName').textContent = file.name;
    document.getElementById('imageFileSize').textContent = `(${(file.size / 1024 / 1024).toFixed(1)} MB)`;
}

function showExistingImage(url) {
    document.getElementById('imagePreview').src = safeImage(url);
    document.getElementById('imagePreviewContainer').style.display = 'block';
    document.getElementById('imageFileName').textContent = 'Ảnh hiện tại';
    document.getElementById('existingImageUrl').value = url;
    document.getElementById('productImageFile').value = '';
}

function removeImage() {
    const preview = document.getElementById('imagePreview');
    if (preview?.src.startsWith('blob:')) URL.revokeObjectURL(preview.src);
    if (preview) preview.removeAttribute('src');
    document.getElementById('imagePreviewContainer').style.display = 'none';
    document.getElementById('existingImageUrl').value = '';
    document.getElementById('productImageFile').value = '';
}

function searchProducts() {
    currentSearch = document.getElementById('searchInput')?.value.trim() || '';
    currentPage = 0;
    loadProducts();
}

function handleSearchKeypress(event) {
    if (event.key === 'Enter') {
        event.preventDefault();
        searchProducts();
    }
}

function clearSearch() {
    const input = document.getElementById('searchInput');
    if (input) input.value = '';
    currentSearch = '';
    currentPage = 0;
    loadProducts();
}

function filterByStock() { currentStockFilter = document.getElementById('stockFilter').value; currentPage = 0; loadProducts(); }
function toggleIncludeInactive() { currentIncludeInactive = document.getElementById('includeInactive').checked; currentPage = 0; loadProducts(); }

function updatePagination(data) {
    const container = document.getElementById('pagination');
    container.replaceChildren();
    const pages = Number(data.totalPages || 0);
    if (pages <= 1) return;
    for (let page = 0; page < pages; page += 1) {
        if (pages > 9 && Math.abs(page - currentPage) > 2 && page !== 0 && page !== pages - 1) continue;
        const item = document.createElement('li');
        item.className = `page-item${page === currentPage ? ' active' : ''}`;
        const button = textElement('button', 'page-link', String(page + 1));
        button.type = 'button';
        button.addEventListener('click', () => { currentPage = page; loadProducts(); });
        item.appendChild(button);
        container.appendChild(item);
    }
}

function showTableMessage(message, iconClass) {
    const tbody = document.getElementById('productsTableBody');
    const row = tableMessageRow(message, 8);
    const icon = document.createElement('i');
    icon.className = `${iconClass} me-2`;
    row.querySelector('td').prepend(icon);
    tbody.replaceChildren(row);
}

function tableMessageRow(message, colspan) {
    const row = document.createElement('tr');
    const cell = textElement('td', 'text-center py-5 text-muted', message);
    cell.colSpan = colspan;
    row.appendChild(cell);
    return row;
}

function defaultVariant(product) {
    const variants = (product.variants || []).filter(item => item.active !== false);
    return variants.find(item => item.defaultVariant) || variants[0] || {};
}

function productStock(product) { return Number(product.totalAvailableStock ?? (product.variants || []).reduce((sum, variant) => sum + Number(variant.availableStock || 0), 0)); }
function formatPrice(price) { return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(Number(price) || 0); }
function formatDateOnly(valueToFormat) {
    if (!valueToFormat) return '—';
    const date = new Date(`${valueToFormat}T00:00:00`);
    return Number.isNaN(date.getTime()) ? String(valueToFormat) : new Intl.DateTimeFormat('vi-VN').format(date);
}
function dateToLocalIso(date) {
    const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
    return local.toISOString().slice(0, 10);
}
function todayIso() { return dateToLocalIso(new Date()); }
function dateAfterDaysIso(days) { const date = new Date(); date.setDate(date.getDate() + days); return dateToLocalIso(date); }
function dateAfterMonthsIso(months) { const date = new Date(); date.setMonth(date.getMonth() + months); return dateToLocalIso(date); }
function safeImage(url) { try { const parsed = new URL(url || '/images/beauty/hero-beautystore-v2.webp', location.origin); return ['http:', 'https:'].includes(parsed.protocol) ? parsed.href : '/images/beauty/hero-beautystore-v2.webp'; } catch (_) { return '/images/beauty/hero-beautystore-v2.webp'; } }
function value(id) { return document.getElementById(id)?.value?.trim() || ''; }
function nullable(id) { return value(id) || null; }
function nullableNumber(id) { return value(id) === '' ? null : Number(value(id)); }
function numberValue(id, fallback) { const number = Number(value(id)); return Number.isFinite(number) ? number : fallback; }
function setValue(id, valueToSet) { const input = document.getElementById(id); if (input) input.value = valueToSet ?? ''; }
function textElement(tag, className, text) { const node = document.createElement(tag); node.className = className; node.textContent = text ?? ''; return node; }

function showToast(message, type = 'success') {
    const toast = textElement('div', `alert alert-${type} position-fixed top-0 start-50 translate-middle-x mt-3 shadow`, message);
    toast.setAttribute('role', 'status');
    toast.style.zIndex = '2000';
    document.body.appendChild(toast);
    setTimeout(() => toast.remove(), 3000);
}
