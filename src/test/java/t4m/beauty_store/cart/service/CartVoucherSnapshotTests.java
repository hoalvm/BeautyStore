package t4m.beauty_store.cart.service;

import org.junit.jupiter.api.Test;
import t4m.beauty_store.auth.repository.UserRepository;
import t4m.beauty_store.cart.entity.Cart;
import t4m.beauty_store.cart.entity.CartItem;
import t4m.beauty_store.cart.repository.CartItemRepository;
import t4m.beauty_store.cart.repository.CartRepository;
import t4m.beauty_store.product.entity.Product;
import t4m.beauty_store.product.entity.ProductVariant;
import t4m.beauty_store.product.repository.ProductVariantRepository;
import t4m.beauty_store.product.service.InventoryService;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CartVoucherSnapshotTests {

    @Test
    void snapshotUsesCurrentVariantPriceInsteadOfStoredOrClientPrice() {
        CartRepository carts = mock(CartRepository.class);
        InventoryService inventory = mock(InventoryService.class);
        CartService service = new CartService(
            carts,
            mock(CartItemRepository.class),
            mock(UserRepository.class),
            mock(ProductVariantRepository.class),
            inventory);
        Product product = Product.builder().id(1L).active(true).build();
        ProductVariant variant = ProductVariant.builder()
            .id(2L)
            .product(product)
            .price(new BigDecimal("150000"))
            .discountPrice(new BigDecimal("120000"))
            .active(true)
            .build();
        Cart cart = Cart.builder().id(3L).guestTokenHash("b".repeat(64)).build();
        CartItem item = CartItem.builder()
            .id(4L)
            .cart(cart)
            .product(product)
            .variant(variant)
            .quantity(2)
            .price(BigDecimal.ONE)
            .build();
        cart.addItem(item);
        when(carts.findByGuestTokenHashForUpdate(cart.getGuestTokenHash()))
            .thenReturn(Optional.of(cart));
        when(inventory.getAvailableStock(variant.getId())).thenReturn(2);

        var snapshot = service.getVoucherCartSnapshot(
            new CartIdentity(null, cart.getGuestTokenHash()));

        assertThat(snapshot.subtotal()).isEqualByComparingTo("240000");
        assertThat(snapshot.lines()).singleElement()
            .satisfies(line -> {
                assertThat(line.product()).isSameAs(product);
                assertThat(line.variant()).isSameAs(variant);
                assertThat(line.lineTotal()).isEqualByComparingTo("240000");
            });
        assertThat(item.getPrice()).isEqualByComparingTo("120000");
    }
}
