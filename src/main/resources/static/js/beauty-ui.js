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
            if (['http:', 'https:'].includes(url.protocol) || url.origin === window.location.origin) {
                return url.href;
            }
        } catch (_) {
            // Invalid URLs use the local fallback below.
        }
        return fallback;
    }

    function brandName(product) {
        return text(product?.brand?.name || product?.brandName || product?.brand, 'BeautyStore');
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
            || gallery.find(item => item?.variantId === selected?.id && item?.primary)?.url
            || gallery.find(item => item?.variantId === selected?.id)?.url
            || gallery.find(item => item?.primary)?.url
            || gallery[0]?.url;
        return safeUrl(image);
    }

    function currentPrice(product, variant) {
        const selected = variant || defaultVariant(product);
        const regular = Number(selected?.price ?? product?.minPrice ?? 0);
        const sale = Number(selected?.discountPrice ?? 0);
        return sale > 0 && sale < regular ? sale : regular;
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
        productImage,
        currentPrice,
        priceRange,
        formatPrice,
        productHref,
        facetLabels,
        setBusy,
        announce
    });
})();
