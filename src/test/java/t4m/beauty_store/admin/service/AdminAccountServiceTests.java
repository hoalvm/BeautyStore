package t4m.beauty_store.admin.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import t4m.beauty_store.admin.dto.AccountUpdateRequest;
import t4m.beauty_store.admin.dto.BulkActionRequest;
import t4m.beauty_store.auth.entity.Role;
import t4m.beauty_store.auth.entity.User;
import t4m.beauty_store.auth.repository.RoleRepository;
import t4m.beauty_store.auth.repository.UserRepository;
import t4m.beauty_store.auth.service.EmailService;
import t4m.beauty_store.auth.service.OtpService;
import t4m.beauty_store.cart.repository.CartRepository;
import t4m.beauty_store.config.StoreProperties;
import t4m.beauty_store.favorite.repository.FavoriteRepository;
import t4m.beauty_store.order.repository.OrderRepository;
import t4m.beauty_store.rating.repository.RatingRepository;
import t4m.beauty_store.review.repository.ReviewRepository;
import t4m.beauty_store.returns.repository.ReturnRequestRepository;
import t4m.beauty_store.voucher.repository.VoucherUsageRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAccountServiceTests {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmailService emailService;
    @Mock
    private OtpService otpService;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private CartRepository cartRepository;
    @Mock
    private FavoriteRepository favoriteRepository;
    @Mock
    private RatingRepository ratingRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private ReturnRequestRepository returnRequestRepository;
    @Mock
    private VoucherUsageRepository voucherUsageRepository;

    private AdminAccountService service;

    @BeforeEach
    void setUp() {
        service = new AdminAccountService(userRepository, roleRepository, passwordEncoder,
            emailService, otpService, orderRepository, cartRepository, favoriteRepository,
            ratingRepository, reviewRepository, returnRequestRepository, voucherUsageRepository,
            new StoreProperties());
    }

    @Test
    void adminPasswordResetCreatesStoresAndSendsPublicResetOtp() {
        User user = user(7L, "customer@example.com", true, "ROLE_USER");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        service.resetPassword(7L);

        verify(otpService).issueOtp("customer@example.com", "forgot-password");
        verifyNoInteractions(emailService);
    }

    @Test
    void accountListingUsesRepositoryPaginationAndStatusFilter() {
        User customer = user(7L, "customer@example.com", true, "ROLE_USER");
        var pageable = PageRequest.of(1, 20, Sort.by("email").ascending());
        when(userRepository.findAdminPage("customer", "ROLE_USER", "active", pageable))
            .thenReturn(new PageImpl<>(List.of(customer), pageable, 21));

        var result = service.getAccounts(" customer ", "ROLE_USER", "ACTIVE",
            1, 20, "email", "asc");

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(21);
        verify(userRepository, never()).findAll();
    }

    @Test
    void adminCannotBanOwnAuthenticatedAccount() {
        User actor = user(1L, "Admin@BeautyStore.vn", true, "ROLE_ADMIN");
        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));

        assertThatThrownBy(() -> service.banAccount(1L, "admin@beautystore.vn"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("khóa");

        verify(userRepository, never()).save(actor);
        verify(userRepository, never()).findAdministratorsForUpdate();
    }

    @Test
    void adminCannotDeleteOwnAuthenticatedAccount() {
        User actor = user(1L, "admin@beautystore.vn", true, "ROLE_ADMIN");
        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));

        assertThatThrownBy(() -> service.deleteAccount(1L, "ADMIN@BEAUTYSTORE.VN"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("xóa");

        verifyNoInteractions(orderRepository, cartRepository, favoriteRepository);
        verify(userRepository, never()).delete(actor);
    }

    @Test
    void adminCannotDemoteOwnAuthenticatedAccount() {
        User actor = user(1L, "admin@beautystore.vn", true, "ROLE_ADMIN");
        AccountUpdateRequest request = demotionRequest();
        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));

        assertThatThrownBy(() -> service.updateAccount(1L, request, "ADMIN@BEAUTYSTORE.VN"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("quyền");

        verifyNoInteractions(roleRepository);
        verify(userRepository, never()).save(actor);
    }

    @Test
    void cannotBanTheLastActiveAdmin() {
        User target = user(2L, "last-admin@beautystore.vn", true, "ROLE_ADMIN");
        User inactiveAdmin = user(3L, "inactive-admin@beautystore.vn", false, "ROLE_ADMIN");
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.findAdministratorsForUpdate())
            .thenReturn(List.of(target, inactiveAdmin));

        assertThatThrownBy(() -> service.banAccount(2L, "operator@beautystore.vn"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("quản trị viên");

        verify(userRepository, never()).save(target);
    }

    @Test
    void cannotDeleteTheLastActiveAdmin() {
        User target = user(2L, "last-admin@beautystore.vn", true, "ROLE_ADMIN");
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.findAdministratorsForUpdate()).thenReturn(List.of(target));

        assertThatThrownBy(() -> service.deleteAccount(2L, "operator@beautystore.vn"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("quản trị viên");

        verifyNoInteractions(orderRepository, cartRepository, favoriteRepository);
        verify(userRepository, never()).delete(target);
    }

    @Test
    void cannotDemoteTheLastActiveAdmin() {
        User target = user(2L, "last-admin@beautystore.vn", true, "ROLE_ADMIN");
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.findAdministratorsForUpdate()).thenReturn(List.of(target));

        assertThatThrownBy(() -> service.updateAccount(
                2L, demotionRequest(), "operator@beautystore.vn"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("quản trị viên");

        verifyNoInteractions(roleRepository);
        verify(userRepository, never()).save(target);
    }

    @Test
    void bulkBanCannotIncludeAuthenticatedAdmin() {
        User actor = user(1L, "admin@beautystore.vn", true, "ROLE_ADMIN");
        User customer = user(2L, "customer@beautystore.vn", true, "ROLE_USER");
        BulkActionRequest request = bulk("ban", 1L, 2L);
        when(userRepository.findAllById(Set.of(1L, 2L))).thenReturn(List.of(actor, customer));

        assertThatThrownBy(() -> service.bulkAction(request, "ADMIN@BEAUTYSTORE.VN"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("khóa");

        verify(userRepository, never()).saveAll(List.of(actor, customer));
        verify(userRepository, never()).findAdministratorsForUpdate();
    }

    @Test
    void bulkDeleteCannotIncludeAuthenticatedAdmin() {
        User actor = user(1L, "admin@beautystore.vn", true, "ROLE_ADMIN");
        User customer = user(2L, "customer@beautystore.vn", true, "ROLE_USER");
        BulkActionRequest request = bulk("delete", 1L, 2L);
        when(userRepository.findAllById(Set.of(1L, 2L))).thenReturn(List.of(actor, customer));

        assertThatThrownBy(() -> service.bulkAction(request, "ADMIN@BEAUTYSTORE.VN"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("xóa");

        verify(userRepository, never()).findAdministratorsForUpdate();
        verify(userRepository, never()).delete(actor);
        verify(userRepository, never()).delete(customer);
        verifyNoInteractions(orderRepository, cartRepository, favoriteRepository);
    }

    @Test
    void bulkBanCannotDeactivateAllActiveAdmins() {
        User firstAdmin = user(1L, "first@beautystore.vn", true, "ROLE_ADMIN");
        User secondAdmin = user(2L, "second@beautystore.vn", true, "ROLE_ADMIN");
        BulkActionRequest request = bulk("ban", 1L, 2L);
        when(userRepository.findAllById(Set.of(1L, 2L)))
            .thenReturn(List.of(firstAdmin, secondAdmin));
        when(userRepository.findAdministratorsForUpdate())
            .thenReturn(List.of(firstAdmin, secondAdmin));

        assertThatThrownBy(() -> service.bulkAction(request, "operator@beautystore.vn"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("quản trị viên");

        verify(userRepository, never()).saveAll(List.of(firstAdmin, secondAdmin));
        verify(userRepository, never()).save(firstAdmin);
        verify(userRepository, never()).save(secondAdmin);
    }

    @Test
    void bulkDeleteCannotRemoveAllActiveAdmins() {
        User firstAdmin = user(1L, "first@beautystore.vn", true, "ROLE_ADMIN");
        User secondAdmin = user(2L, "second@beautystore.vn", true, "ROLE_ADMIN");
        BulkActionRequest request = bulk("delete", 1L, 2L);
        when(userRepository.findAllById(Set.of(1L, 2L)))
            .thenReturn(List.of(firstAdmin, secondAdmin));
        when(userRepository.findAdministratorsForUpdate())
            .thenReturn(List.of(firstAdmin, secondAdmin));

        assertThatThrownBy(() -> service.bulkAction(request, "operator@beautystore.vn"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("quản trị viên");

        verifyNoInteractions(orderRepository, cartRepository, favoriteRepository);
        verify(userRepository, never()).delete(firstAdmin);
        verify(userRepository, never()).delete(secondAdmin);
    }

    @Test
    void bulkDeleteAppliesTheSingleDeleteOrderInvariant() {
        User customer = user(9L, "customer@beautystore.vn", true, "ROLE_USER");
        BulkActionRequest request = bulk("delete", 9L);
        when(userRepository.findAllById(Set.of(9L))).thenReturn(List.of(customer));
        when(userRepository.findById(9L)).thenReturn(Optional.of(customer));
        when(orderRepository.existsByUserIdOrShipperId(9L, 9L)).thenReturn(true);

        assertThatThrownBy(() -> service.bulkAction(request, "admin@beautystore.vn"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("lịch sử");

        verify(orderRepository).existsByUserIdOrShipperId(9L, 9L);
        verifyNoInteractions(cartRepository, favoriteRepository);
        verify(userRepository, never()).delete(customer);
    }

    @Test
    void bulkDeleteChecksAllUserHistoryForeignKeysBeforeDeleting() {
        User customer = user(10L, "history@beautystore.vn", true, "ROLE_USER");
        BulkActionRequest request = bulk("delete", 10L);
        when(userRepository.findAllById(Set.of(10L))).thenReturn(List.of(customer));
        when(userRepository.findById(10L)).thenReturn(Optional.of(customer));
        when(voucherUsageRepository.existsByUserId(10L)).thenReturn(true);

        assertThatThrownBy(() -> service.bulkAction(request, "admin@beautystore.vn"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("lịch sử");

        verify(orderRepository).existsByUserIdOrShipperId(10L, 10L);
        verify(ratingRepository).existsByUserId(10L);
        verify(reviewRepository).existsByUserId(10L);
        verify(returnRequestRepository).existsByUserId(10L);
        verify(voucherUsageRepository).existsByUserId(10L);
        verifyNoInteractions(cartRepository, favoriteRepository);
        verify(userRepository, never()).delete(customer);
    }

    private static AccountUpdateRequest demotionRequest() {
        AccountUpdateRequest request = new AccountUpdateRequest();
        request.setName("Admin");
        request.setEmail("last-admin@beautystore.vn");
        request.setRole("ROLE_USER");
        return request;
    }

    private static BulkActionRequest bulk(String action, Long... ids) {
        BulkActionRequest request = new BulkActionRequest();
        request.setAction(action);
        request.setUserIds(List.of(ids));
        return request;
    }

    private static User user(Long id, String email, boolean activated, String roleName) {
        Role role = new Role();
        role.setId(id + 100);
        role.setRname(roleName);
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setPasswd("unused");
        user.setName("Test user " + id);
        user.setActivated(activated);
        user.setRoles(Set.of(role));
        return user;
    }

}
