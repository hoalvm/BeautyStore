(function () {
    'use strict';

    const PLACEHOLDER = '/images/beauty/hero-beautystore-v2.webp';

    function text(value, fallback = '') {
        const normalized = String(value ?? '').trim();
        return normalized || fallback;
    }

    function escapeHtml(value) {
        return String(value ?? '')
            .replaceAll('&', '&amp;')
            .replaceAll('<', '&lt;')
            .replaceAll('>', '&gt;')
            .replaceAll('"', '&quot;')
            .replaceAll("'", '&#039;');
    }

    function safeUrl(value, fallback = PLACEHOLDER) {
        const candidate = text(value);
        if (!candidate) return fallback;
        try {
            const url = new URL(candidate, window.location.origin);
            if (['http:', 'https:'].includes(url.protocol)) {
                return url.href;
            }
        } catch (_) {
            // Invalid URLs use the local fallback below.
        }
        return fallback;
    }

    function brandName(product) {
        return text(product?.brand?.name || product?.brandName
            || (typeof product?.brand === 'string' ? product.brand : ''), 'BeautyStore');
    }

    function activeVariants(product) {
        return Array.isArray(product?.variants)
            ? product.variants.filter(variant => variant && variant.active !== false)
            : [];
    }

    function defaultVariant(product) {
        const variants = activeVariants(product);
        return variants.find(variant => variant.defaultVariant) || variants[0] || {};
    }

    function productImage(product, variant) {
        const selected = variant || defaultVariant(product);
        const gallery = Array.isArray(product?.gallery) ? product.gallery : [];
        const image = selected?.imageUrl
            || gallery.find(item => selected?.id != null && item?.variantId === selected.id && item?.primary)?.url
            || gallery.find(item => selected?.id != null && item?.variantId === selected.id)?.url
            || gallery.find(item => item?.variantId == null && item?.primary)?.url
            || gallery.find(item => item?.variantId == null)?.url;
        return safeUrl(image);
    }

    function currentPrice(product, variant) {
        const selected = variant || defaultVariant(product);
        const regular = Number(selected?.price ?? product?.minPrice ?? 0);
        const sale = selected?.discountPrice == null ? NaN : Number(selected.discountPrice);
        return Number.isFinite(sale) && sale >= 0 && sale < regular
            ? sale : (Number.isFinite(regular) && regular >= 0 ? regular : 0);
    }

    function variantLabel(variant) {
        const size = variant?.sizeValue ?? variant?.volume;
        const unit = variant?.sizeUnit ?? variant?.volumeUnit;
        return text(variant?.label || [variant?.shadeName, size != null && `${size} ${unit || ''}`]
            .filter(Boolean).join(' · '));
    }

    function isAvailable(product) {
        return product?.active !== false && product?.brand?.active !== false
            && product?.category?.active !== false
            && !(product?.categoryBreadcrumb || []).some(category => category.active === false)
            && activeVariants(product).some(variant => Number(variant.availableStock) > 0);
    }

    // Java LocalDateTime is store-local (+07:00), not the visitor's timezone.
    function parseBusinessDate(value) {
        if (typeof value !== 'string' || !value.trim()) return new Date(NaN);
        const normalized = value.trim();
        const localTimestamp = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d{1,9})?)?$/;
        return new Date(localTimestamp.test(normalized) ? `${normalized}+07:00` : normalized);
    }

    async function errorMessage(response, fallback = 'Không thể hoàn tất yêu cầu. Vui lòng thử lại.') {
        const payload = await response.json().catch(() => null);
        return typeof payload?.message === 'string' && payload.message.trim() ? payload.message : fallback;
    }

    function priceRange(product) {
        const variants = activeVariants(product);
        const prices = variants.map(variant => currentPrice(product, variant)).filter(Number.isFinite);
        if (!prices.length) {
            const fallback = currentPrice(product);
            return { min: fallback, max: fallback };
        }
        return { min: Math.min(...prices), max: Math.max(...prices) };
    }

    function formatPrice(value) {
        return new Intl.NumberFormat('vi-VN', {
            style: 'currency',
            currency: 'VND',
            maximumFractionDigits: 0
        }).format(Number(value) || 0);
    }

    function productHref(product) {
        return product?.slug
            ? `/products/${encodeURIComponent(product.slug)}`
            : `/product/${encodeURIComponent(product?.id ?? '')}`;
    }

    function facetLabels(product, types, limit = 2) {
        const allowed = new Set(types);
        return (Array.isArray(product?.facets) ? product.facets : [])
            .filter(facet => allowed.has(facet?.type))
            .map(facet => text(facet.label || facet.code))
            .filter(Boolean)
            .slice(0, limit);
    }

    function setBusy(element, busy) {
        if (!element) return;
        element.setAttribute('aria-busy', String(Boolean(busy)));
    }

    function announce(message) {
        let live = document.getElementById('beautyLiveRegion');
        if (!live) {
            live = document.createElement('div');
            live.id = 'beautyLiveRegion';
            live.className = 'visually-hidden';
            live.setAttribute('role', 'status');
            live.setAttribute('aria-live', 'polite');
            document.body.appendChild(live);
        }
        live.textContent = '';
        window.setTimeout(() => { live.textContent = text(message); }, 20);
    }

    window.BeautyUI = Object.freeze({
        text,
        escapeHtml,
        safeUrl,
        brandName,
        activeVariants,
        defaultVariant,
        variantLabel,
        isAvailable,
        productImage,
        currentPrice,
        priceRange,
        formatPrice,
        parseBusinessDate,
        errorMessage,
        productHref,
        facetLabels,
        setBusy,
        announce
    });
})();
