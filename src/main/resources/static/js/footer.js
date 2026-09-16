document.addEventListener('DOMContentLoaded', loadFooterCategories);

async function loadFooterCategories() {
    const container = document.getElementById('footerCategories');
    if (!container) return;
    const createLink = (name, href) => {
        const item = document.createElement('li');
        item.className = 'mb-2';
        const link = document.createElement('a');
        link.className = 'text-light text-decoration-none hover-danger';
        link.href = href;
        link.textContent = name;
        item.appendChild(link);
        return item;
    };
    try {
        const response = await fetch('/api/products/categories');
        if (!response.ok) throw new Error();
        const payload = await response.json();
        const categories = (Array.isArray(payload) ? payload : payload.content || [])
            .filter(category => category?.active !== false && !category?.parentId)
            .slice(0, 6);
        container.replaceChildren(createLink('Tất cả sản phẩm', '/products'), ...categories.map(category =>
            createLink(category.name || 'Danh mục', `/products?category=${encodeURIComponent(category.slug || category.id)}`)
        ));
    } catch (_) {
        container.replaceChildren(createLink('Khám phá tất cả mỹ phẩm', '/products'));
    }
}
