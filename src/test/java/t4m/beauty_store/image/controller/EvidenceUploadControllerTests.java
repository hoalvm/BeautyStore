package t4m.beauty_store.image.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.UserDetails;
import t4m.beauty_store.auth.entity.User;
import t4m.beauty_store.auth.repository.UserRepository;
import t4m.beauty_store.image.entity.EvidenceKind;
import t4m.beauty_store.image.service.EvidenceUploadService;
import t4m.beauty_store.order.entity.Order;
import t4m.beauty_store.order.entity.OrderItem;
import t4m.beauty_store.order.entity.OrderStatus;
import t4m.beauty_store.order.repository.OrderItemRepository;
import t4m.beauty_store.order.service.GuestOrderAccessService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class EvidenceUploadControllerTests {
    private EvidenceUploadService evidenceUploadService;
    private OrderItemRepository orderItemRepository;
    private UserRepository userRepository;
    private GuestOrderAccessService guestOrderAccessService;
    private EvidenceUploadController controller;

    @BeforeEach
    void setUp() {
        evidenceUploadService = mock(EvidenceUploadService.class);
        orderItemRepository = mock(OrderItemRepository.class);
        userRepository = mock(UserRepository.class);
        guestOrderAccessService = mock(GuestOrderAccessService.class);
        controller = new EvidenceUploadController(
            evidenceUploadService, orderItemRepository, userRepository, guestOrderAccessService);
    }

    @Test
    void memberCannotUploadForAnotherCustomersOrder() {
        User owner = user(1L, "owner@example.com");
        User intruder = user(2L, "intruder@example.com");
        OrderItem item = item(memberOrder(owner));
        UserDetails principal = mock(UserDetails.class);
        when(principal.getUsername()).thenReturn(intruder.getEmail());
        when(userRepository.findByEmail(intruder.getEmail())).thenReturn(Optional.of(intruder));
        when(orderItemRepository.findByIdWithOrder(20L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> controller.uploadReviewImage(20L, image(), principal, null))
            .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(evidenceUploadService);
    }

    @Test
    void guestTokenMustResolveToTheSameOrderItemOrder() throws Exception {
        Order order = Order.builder().id(10L).orderNumber("BEA-10")
            .status(OrderStatus.DELIVERED).build();
        OrderItem item = item(order);
        when(orderItemRepository.findByIdWithOrder(20L)).thenReturn(Optional.of(item));
        when(guestOrderAccessService.requireOrderAccess("BEA-10", "scoped-token"))
            .thenReturn(order);
        when(evidenceUploadService.upload(eq(EvidenceKind.RETURN), eq(20L), any()))
            .thenReturn(new EvidenceUploadService.UploadedEvidence("https://cdn.example/proof.webp"));

        EvidenceUploadService.UploadedEvidence response = controller.uploadReturnEvidence(
            20L, image(), null, "scoped-token");

        assertThat(response.url()).isEqualTo("https://cdn.example/proof.webp");
        verify(evidenceUploadService).upload(eq(EvidenceKind.RETURN), eq(20L), any());
    }

    private static Order memberOrder(User owner) {
        return Order.builder().id(10L).orderNumber("BEA-10").user(owner)
            .status(OrderStatus.DELIVERED).build();
    }

    private static OrderItem item(Order order) {
        return OrderItem.builder().id(20L).order(order).build();
    }

    private static User user(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        return user;
    }

    private static MockMultipartFile image() {
        return new MockMultipartFile("image", "proof.png", "image/png", new byte[]{1});
    }
}
