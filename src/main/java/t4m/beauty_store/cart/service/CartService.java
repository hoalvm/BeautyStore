package t4m.beauty_store.cart.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import t4m.beauty_store.auth.entity.User;
import t4m.beauty_store.auth.exception.UserNotFoundException;
import t4m.beauty_store.auth.repository.UserRepository;
import t4m.beauty_store.cart.dto.AddToCartRequest;
import t4m.beauty_store.cart.dto.CartItemResponse;
import t4m.beauty_store.cart.dto.CartResponse;
import t4m.beauty_store.cart.dto.UpdateCartItemRequest;
import t4m.beauty_store.cart.entity.Cart;
import t4m.beauty_store.cart.entity.CartItem;
import t4m.beauty_store.cart.exception.CartItemNotFoundException;
import t4m.beauty_store.cart.exception.CartNotFoundException;
import t4m.beauty_store.cart.exception.InsufficientStockException;
import t4m.beauty_store.cart.repository.CartItemRepository;
import t4m.beauty_store.cart.repository.CartRepository;
import t4m.beauty_store.product.entity.Category;
import t4m.beauty_store.product.entity.Product;
import t4m.beauty_store.product.entity.ProductImage;
import t4m.beauty_store.product.entity.ProductVariant;
import t4m.beauty_store.product.repository.ProductVariantRepository;
import t4m.beauty_store.product.service.InventoryService;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CartService {
    private static final int MAX_CART_QUANTITY = 999;

    public record VoucherCartLine(
            Product product, ProductVariant variant, BigDecimal lineTotal) {
    }

    public record VoucherCartSnapshot(
            BigDecimal subtotal, List<VoucherCartLine> lines) {
    }

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryService inventoryService;

    @Transactional
    public CartResponse addToCart(CartIdentity identity, AddToCartRequest request) {
        requirePositiveQuantity(request == null ? null : request.getQuantity());
        Cart cart = resolveCart(identity, true);
        ProductVariant variant = resolveVariant(request);
        Product product = variant.getProduct();
        ensureCatalogVisible(product);

        int available = availableStock(product, variant);
        Optional<CartItem> existing = cartItemRepository
            .findByCartIdAndVariantId(cart.getId(), variant.getId());
        long combinedQuantity = (long) request.getQuantity()
            + existing.map(CartItem::getQuantity).orElse(0);
        if (combinedQuantity > MAX_CART_QUANTITY) {
            throw new IllegalArgumentException("Số lượng mỗi biến thể không được vượt quá 999");
        }
        int newQuantity = (int) combinedQuantity;
        ensureStock(product.getName(), available, newQuantity);

        CartItem item = existing.orElseGet(() -> CartItem.builder()
            .cart(cart)
            .product(product)
            .variant(variant)
            .quantity(0)
            .price(variant.getEffectivePrice())
            .build());
        item.setQuantity(newQuantity);
        item.setPrice(variant.getEffectivePrice());
        cartItemRepository.save(item);
        if (!cart.getCartItems().contains(item)) {
            cart.addItem(item);
        }
        return toResponse(cart);
    }

    @Transactional
    public CartResponse getCart(CartIdentity identity) {
        Cart cart = resolveCart(identity, false);
        return cart == null ? emptyResponse() : toResponse(cart);
    }

    /**
     * Returns a server-priced snapshot for voucher previews. Client supplied
     * totals are deliberately not part of this API.
     */
    @Transactional
    public VoucherCartSnapshot getVoucherCartSnapshot(CartIdentity identity) {
        Cart cart = resolveCart(identity, false);
        if (cart == null) {
            return new VoucherCartSnapshot(BigDecimal.ZERO, List.of());
        }

        List<VoucherCartLine> lines = cart.getCartItems().stream()
            .filter(item -> item.getQuantity() != null && item.getQuantity() > 0)
            .filter(item -> isSellable(item.getProduct(), item.getVariant()))
            .filter(item -> availableStock(item.getProduct(), item.getVariant()) >= item.getQuantity())
            .map(item -> {
                BigDecimal currentPrice = item.getVariant().getEffectivePrice();
                if (currentPrice == null || currentPrice.signum() < 0) return null;
                item.setPrice(currentPrice);
                return new VoucherCartLine(
                    item.getProduct(), item.getVariant(),
                    currentPrice.multiply(BigDecimal.valueOf(item.getQuantity())));
            })
            .filter(java.util.Objects::nonNull)
            .toList();
        BigDecimal subtotal = lines.stream()
            .map(VoucherCartLine::lineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new VoucherCartSnapshot(subtotal, lines);
    }

    @Transactional
    public CartResponse updateCartItem(
            CartIdentity identity, Long cartItemId, UpdateCartItemRequest request) {
        requirePositiveQuantity(request == null ? null : request.getQuantity());
        Cart cart = requireCart(resolveCart(identity, false));
        CartItem item = ownedItem(cart, cartItemId);
        ProductVariant variant = requireSellableVariant(item);
        int available = availableStock(item.getProduct(), variant);
        ensureStock(item.getProduct().getName(), available, request.getQuantity());
        item.setQuantity(request.getQuantity());
        item.setPrice(variant.getEffectivePrice());
        cartItemRepository.save(item);
        return toResponse(cart);
    }

    @Transactional
    public CartResponse removeCartItem(CartIdentity identity, Long cartItemId) {
        Cart cart = requireCart(resolveCart(identity, false));
        CartItem item = ownedItem(cart, cartItemId);
        cart.removeItem(item);
        cartItemRepository.delete(item);
        return toResponse(cart);
    }

    @Transactional
    public void clearCart(CartIdentity identity) {
        Cart cart = resolveCart(identity, false);
        if (cart != null) {
            cart.getCartItems().clear();
            cartRepository.save(cart);
        }
    }

    @Transactional
    public void mergeGuestCart(String userEmail, String guestTokenHash) {
        if (guestTokenHash == null || guestTokenHash.isBlank()) return;
        Cart guest = cartRepository.findByGuestTokenHashForUpdate(guestTokenHash).orElse(null);
        if (guest == null) return;
        Cart member = resolveCart(new CartIdentity(userEmail, null), true);

        for (CartItem source : List.copyOf(guest.getCartItems())) {
            ProductVariant variant = source.getVariant();
            if (variant == null || !isSellable(source.getProduct(), variant)) continue;
            Optional<CartItem> existing = cartItemRepository
                .findByCartIdAndVariantId(member.getId(), variant.getId());
            int available = availableStock(source.getProduct(), variant);
            long combinedQuantity = (long) source.getQuantity()
                + existing.map(CartItem::getQuantity).orElse(0);
            int mergedQuantity = (int) Math.min(
                Math.min((long) available, MAX_CART_QUANTITY), combinedQuantity);
            if (mergedQuantity <= 0) continue;

            CartItem target = existing.orElseGet(() -> CartItem.builder()
                .cart(member)
                .product(source.getProduct())
                .variant(variant)
                .quantity(0)
                .price(variant.getEffectivePrice())
                .build());
            target.setQuantity(mergedQuantity);
            target.setPrice(variant.getEffectivePrice());
            cartItemRepository.save(target);
            if (!member.getCartItems().contains(target)) {
                member.addItem(target);
            }
        }

        guest.getCartItems().clear();
        cartRepository.delete(guest);
    }

    @Transactional
    public CartResponse addToCart(String email, AddToCartRequest request) {
        return addToCart(new CartIdentity(email, null), request);
    }

    @Transactional
    public CartResponse getCartByUser(String email) {
        return getCart(new CartIdentity(email, null));
    }

    @Transactional
    public CartResponse updateCartItem(String email, Long id, UpdateCartItemRequest request) {
        return updateCartItem(new CartIdentity(email, null), id, request);
    }

    @Transactional
    public CartResponse removeCartItem(String email, Long id) {
        return removeCartItem(new CartIdentity(email, null), id);
    }

    @Transactional
    public void clearCart(String email) {
        clearCart(new CartIdentity(email, null));
    }

    private ProductVariant resolveVariant(AddToCartRequest request) {
        if (request == null || request.getVariantId() == null) {
            throw new IllegalArgumentException("Vui lòng chọn biến thể sản phẩm");
        }
        return variantRepository.findByIdAndActiveTrueAndProductActiveTrue(request.getVariantId())
            .orElseThrow(() -> new IllegalArgumentException("Biến thể sản phẩm không còn được bán"));
    }

    private Cart resolveCart(CartIdentity identity, boolean create) {
        if (identity == null) {
            throw new IllegalArgumentException("Không xác định được giỏ hàng");
        }
        if (identity.authenticated()) {
            User user = userRepository.findByEmail(identity.userEmail())
                .orElseThrow(() -> new UserNotFoundException("Không tìm thấy tài khoản"));
            return cartRepository.findByUserIdForUpdate(user.getId()).orElseGet(() -> {
                if (!create) return null;
                return cartRepository.save(Cart.builder().user(user).build());
            });
        }
        if (identity.guestTokenHash() == null || identity.guestTokenHash().isBlank()) {
            if (!create) return null;
            throw new IllegalArgumentException("Không xác định được giỏ hàng khách");
        }
        return cartRepository.findByGuestTokenHashForUpdate(identity.guestTokenHash()).orElseGet(() -> {
            if (!create) return null;
            return cartRepository.save(Cart.builder().guestTokenHash(identity.guestTokenHash()).build());
        });
    }

    private CartItem ownedItem(Cart cart, Long itemId) {
        return cartItemRepository.findById(itemId)
            .filter(item -> item.getCart().getId().equals(cart.getId()))
            .orElseThrow(() -> new CartItemNotFoundException("Không tìm thấy sản phẩm trong giỏ"));
    }

    private static Cart requireCart(Cart cart) {
        if (cart == null) {
            throw new CartNotFoundException("Không tìm thấy giỏ hàng");
        }
        return cart;
    }

    private static CartResponse emptyResponse() {
        return CartResponse.builder()
            .cartId(null)
            .items(List.of())
            .totalPrice(BigDecimal.ZERO)
            .totalItems(0)
            .build();
    }

    private ProductVariant requireSellableVariant(CartItem item) {
        ProductVariant variant = item.getVariant();
        if (variant == null || !isSellable(item.getProduct(), variant)) {
            throw new IllegalArgumentException("Sản phẩm không còn được bán");
        }
        return variant;
    }

    private int availableStock(Product product, ProductVariant variant) {
        if (!isSellable(product, variant)) return 0;
        return inventoryService.getAvailableStock(variant.getId());
    }

    private CartResponse toResponse(Cart cart) {
        List<CartItemResponse> items = cart.getCartItems().stream().map(this::toItemResponse).toList();
        BigDecimal total = items.stream()
            .filter(item -> Boolean.TRUE.equals(item.getAvailable()))
            .map(CartItemResponse::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        long itemCount = items.stream()
            .filter(item -> Boolean.TRUE.equals(item.getAvailable()))
            .mapToLong(CartItemResponse::getQuantity)
            .sum();
        int totalItems = (int) Math.min(Integer.MAX_VALUE, itemCount);
        return CartResponse.builder()
            .cartId(cart.getId())
            .items(items)
            .totalPrice(total)
            .totalItems(totalItems)
            .build();
    }

    private CartItemResponse toItemResponse(CartItem item) {
        Product product = item.getProduct();
        ProductVariant variant = item.getVariant();
        boolean catalogAvailable = isSellable(product, variant);
        int stock = catalogAvailable ? availableStock(product, variant) : 0;
        BigDecimal currentPrice = catalogAvailable ? variant.getEffectivePrice() : item.getPrice();
        boolean quantityAvailable = catalogAvailable && stock >= item.getQuantity();
        if (catalogAvailable) {
            item.setPrice(currentPrice);
        }
        return CartItemResponse.builder()
            .id(item.getId())
            .productId(product.getId())
            .variantId(variant == null ? null : variant.getId())
            .productName(product.getName())
            .productImageUrl(resolveImage(product, variant))
            .productSku(variant == null ? null : variant.getSku())
            .variantLabel(variant == null ? null : variant.getLabel())
            .shadeName(variant == null ? null : variant.getShadeName())
            .netContent(netContent(variant))
            .price(currentPrice)
            .quantity(item.getQuantity())
            .subtotal(currentPrice.multiply(BigDecimal.valueOf(item.getQuantity())))
            .availableStock(stock)
            .available(quantityAvailable)
            .build();
    }

    private static boolean isSellable(Product product, ProductVariant variant) {
        if (product == null || variant == null
                || !Boolean.TRUE.equals(product.getActive())
                || !Boolean.TRUE.equals(variant.getActive())) {
            return false;
        }
        if (product.getBrandEntity() != null
                && !Boolean.TRUE.equals(product.getBrandEntity().getActive())) {
            return false;
        }
        Category category = product.getCategory();
        int depth = 0;
        while (category != null && depth++ < 32) {
            if (!Boolean.TRUE.equals(category.getActive())) return false;
            category = category.getParent();
        }
        return category == null;
    }

    private static void ensureCatalogVisible(Product product) {
        if (product == null || !Boolean.TRUE.equals(product.getActive())) {
            throw new IllegalArgumentException("Sản phẩm không còn được bán");
        }
        if (product.getBrandEntity() != null
                && !Boolean.TRUE.equals(product.getBrandEntity().getActive())) {
            throw new IllegalArgumentException("Thương hiệu của sản phẩm đang tạm ẩn");
        }
        Category category = product.getCategory();
        int depth = 0;
        while (category != null && depth++ < 32) {
            if (!Boolean.TRUE.equals(category.getActive())) {
                throw new IllegalArgumentException("Danh mục của sản phẩm đang tạm ẩn");
            }
            category = category.getParent();
        }
        if (category != null) {
            throw new IllegalArgumentException("Cây danh mục sản phẩm không hợp lệ");
        }
    }

    private static void requirePositiveQuantity(Integer quantity) {
        if (quantity == null || quantity <= 0 || quantity > MAX_CART_QUANTITY) {
            throw new IllegalArgumentException("Số lượng phải từ 1 đến 999");
        }
    }

    private static void ensureStock(String name, int available, int requested) {
        if (available < requested) {
            throw new InsufficientStockException("Không đủ tồn kho cho " + name);
        }
    }

    private static String resolveImage(Product product, ProductVariant variant) {
        return product.getImages().stream()
            .filter(image -> variant != null && image.getVariant() != null
                && image.getVariant().getId().equals(variant.getId()))
            .min(Comparator.comparing(ProductImage::getSortOrder).thenComparing(ProductImage::getId))
            .or(() -> product.getImages().stream()
                .filter(image -> image.getVariant() == null)
                .min(Comparator.comparing(ProductImage::getSortOrder).thenComparing(ProductImage::getId)))
            .map(ProductImage::getUrl)
            .orElse(null);
    }

    private static String netContent(ProductVariant variant) {
        if (variant == null || variant.getSizeValue() == null) return null;
        return variant.getSizeValue().stripTrailingZeros().toPlainString()
            + (variant.getSizeUnit() == null ? "" : " " + variant.getSizeUnit());
    }
}
