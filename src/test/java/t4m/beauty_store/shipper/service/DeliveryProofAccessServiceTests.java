package t4m.beauty_store.shipper.service;

import org.junit.jupiter.api.Test;
import t4m.beauty_store.auth.entity.User;
import t4m.beauty_store.auth.repository.UserRepository;
import t4m.beauty_store.config.ApiException;
import t4m.beauty_store.order.entity.Order;
import t4m.beauty_store.order.repository.OrderRepository;
import t4m.beauty_store.order.service.GuestOrderAccessService;
import t4m.beauty_store.shipper.entity.DeliveryProof;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeliveryProofAccessServiceTests {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final GuestOrderAccessService guest = mock(GuestOrderAccessService.class);
    private final DeliveryProofAccessService service = new DeliveryProofAccessService(orders, users, guest);

    @Test
    void onlyOwnerAssignedShipperAndAdminCanReadMemberProof() {
        User owner = account(1L, "owner@test.vn");
        User shipper = account(2L, "shipper@test.vn");
        User stranger = account(3L, "stranger@test.vn");
        Order order = Order.builder().id(4L).user(owner).shipper(shipper)
            .deliveryProof(DeliveryProof.builder().imageBytes(new byte[]{1, 2}).build()).build();
        when(orders.findById(4L)).thenReturn(Optional.of(order));
        for (User user : new User[]{owner, shipper, stranger}) {
            when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        }
        assertThat(service.read(4L, principal(owner.getEmail(), "USER"), null)).containsExactly(1, 2);
        assertThat(service.read(4L, principal(shipper.getEmail(), "SHIPPER"), null)).containsExactly(1, 2);
        assertThat(service.read(4L, principal("admin@test.vn", "ADMIN"), null)).containsExactly(1, 2);
        assertThatThrownBy(() -> service.read(4L, principal(stranger.getEmail(), "SHIPPER"), null))
            .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.read(4L, null, null)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.read(4L, null, "fake")).isInstanceOf(ApiException.class);
        verifyNoInteractions(guest);
    }

    @Test
    void guestRequiresExactOrderTokenAndLegacyOrdersHaveNoProof() {
        Order order = Order.builder().id(4L).orderNumber("ORD-GUEST").build();
        when(orders.findById(4L)).thenReturn(Optional.of(order));
        when(guest.requireOrderAccess("ORD-GUEST", "valid")).thenReturn(order);
        when(guest.requireOrderAccess("ORD-GUEST", "wrong")).thenThrow(new IllegalArgumentException());
        assertThatThrownBy(() -> service.read(4L, null, "wrong")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.read(4L, null, "valid"))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus().value()).isEqualTo(404));
        order.setDeliveryProof(DeliveryProof.builder().imageBytes(new byte[]{3}).build());
        assertThat(service.read(4L, null, "valid")).containsExactly(3);
    }

    private static User account(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        return user;
    }

    private static org.springframework.security.core.userdetails.UserDetails principal(String email, String role) {
        return org.springframework.security.core.userdetails.User.withUsername(email).password("unused").roles(role).build();
    }
}
