package t4m.beauty_store.admin.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import t4m.beauty_store.admin.dto.AccountCreateRequest;
import t4m.beauty_store.admin.dto.AccountDTO;
import t4m.beauty_store.admin.dto.AccountUpdateRequest;
import t4m.beauty_store.admin.dto.BulkActionRequest;
import t4m.beauty_store.auth.entity.Role;
import t4m.beauty_store.auth.entity.User;
import t4m.beauty_store.auth.repository.RoleRepository;
import t4m.beauty_store.auth.repository.UserRepository;
import t4m.beauty_store.auth.service.EmailService;
import t4m.beauty_store.auth.service.OtpService;
import t4m.beauty_store.order.repository.OrderRepository;
import t4m.beauty_store.cart.repository.CartRepository;
import t4m.beauty_store.favorite.repository.FavoriteRepository;
import t4m.beauty_store.rating.repository.RatingRepository;
import t4m.beauty_store.config.StoreProperties;
import t4m.beauty_store.review.repository.ReviewRepository;
import t4m.beauty_store.returns.repository.ReturnRequestRepository;
import t4m.beauty_store.voucher.repository.VoucherUsageRepository;
import t4m.beauty_store.auth.validation.PasswordPolicy;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminAccountService {
    private static final Logger logger = LoggerFactory.getLogger(AdminAccountService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final OtpService otpService;
    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final FavoriteRepository favoriteRepository;
    private final RatingRepository ratingRepository;
    private final ReviewRepository reviewRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final VoucherUsageRepository voucherUsageRepository;
    private final StoreProperties storeProperties;

    /**
     * Get all accounts with pagination, search, and filtering
     */
    public Page<AccountDTO> getAccounts(String search, String roleFilter, String statusFilter,
                                         int page, int size, String sortBy, String sortDir) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Phân trang không hợp lệ");
        }
        String property = switch (sortBy == null ? "" : sortBy) {
            case "name" -> "name";
            case "email" -> "email";
            default -> "created";
        };
        Sort sort = "asc".equalsIgnoreCase(sortDir)
            ? Sort.by(property).ascending() : Sort.by(property).descending();
        return userRepository.findAdminPage(clean(search), clean(roleFilter), normalizeStatus(statusFilter),
            PageRequest.of(page, size, sort)).map(this::convertToDTO);
    }

    /**
     * Get account by ID
     */
    public AccountDTO getAccountById(Long id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản với ID: " + id));
        return convertToDTO(user);
    }

    /**
     * Create new account
     */
    @Transactional
    public AccountDTO createAccount(AccountCreateRequest request) {
        PasswordPolicy.requireStrong(request.getPassword());
        // Validate
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Mật khẩu xác nhận không khớp");
        }

        // Check if email exists
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email đã tồn tại trong hệ thống");
        }

        // Create user
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswd(passwordEncoder.encode(request.getPassword()));
        user.setName(request.getName());
        user.setPhone(request.getPhone());
        user.setActivated(true); // Admin-created accounts are auto-activated
        user.setCreated(LocalDateTime.now());
        user.setUpdated(LocalDateTime.now());

        // Assign role
        Role role = roleRepository.findByRname(request.getRole())
            .orElseThrow(() -> new IllegalArgumentException("Vai trò không hợp lệ: " + request.getRole()));
        user.setRoles(new HashSet<>(Collections.singletonList(role)));

        User savedUser = userRepository.save(user);
        logger.info("Admin account created: userId={}", savedUser.getId());

        // Send welcome email
        try {
            emailService.sendWelcomeEmail(savedUser.getEmail(), savedUser.getName(), appUrl("/login"));
        } catch (Exception e) {
            logger.warn("Welcome email dispatch failed for userId={}", savedUser.getId());
        }

        return convertToDTO(savedUser);
    }

    /**
     * Update account
     */
    @Transactional
    public AccountDTO updateAccount(Long id, AccountUpdateRequest request, String actorEmail) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản với ID: " + id));

        String requestedRole = request.getRole();
        if (requestedRole != null && !requestedRole.isBlank()
                && isAdmin(user) && !"ROLE_ADMIN".equals(requestedRole)) {
            if (sameUser(user, actorEmail)) {
                throw new IllegalArgumentException("Bạn không thể tự gỡ quyền quản trị của mình");
            }
            requireAnotherActiveAdmin(Set.of(user.getId()));
        }

        // Update basic info
        user.setName(request.getName());
        user.setPhone(request.getPhone());
        user.setEmail(request.getEmail());
        user.setUpdated(LocalDateTime.now());

        // Update role if provided
        if (request.getRole() != null && !request.getRole().isEmpty()) {
            Role role = roleRepository.findByRname(request.getRole())
                .orElseThrow(() -> new IllegalArgumentException("Vai trò không hợp lệ: " + request.getRole()));
            boolean roleChanged = user.getRoles().stream()
                .noneMatch(current -> current.getRname().equals(role.getRname()));
            user.setRoles(new HashSet<>(Collections.singletonList(role)));
            if (roleChanged) user.setAuthVersion(user.getAuthVersion() + 1);
        }

        // Update password if provided
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            PasswordPolicy.requireStrong(request.getPassword());
            if (!request.getPassword().equals(request.getConfirmPassword())) {
                throw new IllegalArgumentException("Mật khẩu xác nhận không khớp");
            }
            user.setPasswd(passwordEncoder.encode(request.getPassword()));
            user.setAuthVersion(user.getAuthVersion() + 1);
        }

        User updatedUser = userRepository.save(user);
        logger.info("Admin account updated: userId={}", updatedUser.getId());

        return convertToDTO(updatedUser);
    }

    /**
     * Delete account (permanent)
     * Warning: This will delete all related data (cart, favorites, ratings)
     * but will NOT delete orders to maintain data integrity
     */
    @Transactional
    public void deleteAccount(Long id, String actorEmail) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản với ID: " + id));

        if (sameUser(user, actorEmail)) {
            throw new IllegalArgumentException("Bạn không thể tự xóa tài khoản đang đăng nhập");
        }
        if (user.isActivated() && isAdmin(user)) {
            requireAnotherActiveAdmin(Set.of(user.getId()));
        }

        // Preserve every historical reference, including assignments made while
        // acting as a shipper. Deactivation remains available for these accounts.
        if (hasHistoricalReferences(id)) {
            throw new IllegalArgumentException(
                "Không thể xóa tài khoản đã có lịch sử đơn hàng, giao hàng, đánh giá, đổi trả hoặc voucher. " +
                "Hãy khóa tài khoản để bảo toàn dữ liệu.");
        }

        // Delete related data in correct order to avoid constraint violations
        logger.info("Deleting related account data: userId={}", user.getId());
        
        // 1. Delete cart items (if exists)
        cartRepository.findByUserId(id).ifPresent(cart -> {
            cartRepository.delete(cart);
            logger.info("Account cart deleted: userId={}", user.getId());
        });

        // 2. Delete favorites
        long favoriteCount = favoriteRepository.countByUserId(id);
        if (favoriteCount > 0) {
            favoriteRepository.findByUserIdOrderByCreatedAtDesc(id)
                .forEach(favorite -> favoriteRepository.delete(favorite));
            logger.info("Account favorites deleted: userId={}, count={}", user.getId(), favoriteCount);
        }

        // 3. Delete ratings (if user has no orders, they shouldn't have ratings, but check anyway)
        // Note: Ratings are typically tied to orders, so this should be empty
        
        // 4. Finally delete the user
        userRepository.delete(user);
        logger.info("Admin account deleted: userId={}", user.getId());
    }

    /**
     * Ban account (deactivate)
     */
    @Transactional
    public void banAccount(Long id, String actorEmail) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản với ID: " + id));

        if (sameUser(user, actorEmail)) {
            throw new IllegalArgumentException("Bạn không thể tự khóa tài khoản đang đăng nhập");
        }
        if (user.isActivated() && isAdmin(user)) {
            requireAnotherActiveAdmin(Set.of(user.getId()));
        }

        user.setActivated(false);
        user.setAuthVersion(user.getAuthVersion() + 1);
        user.setUpdated(LocalDateTime.now());
        userRepository.save(user);
        logger.info("Admin account deactivated: userId={}", user.getId());
    }

    /**
     * Unban account (activate)
     */
    @Transactional
    public void unbanAccount(Long id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản với ID: " + id));

        user.setActivated(true);
        user.setAuthVersion(user.getAuthVersion() + 1);
        user.setUpdated(LocalDateTime.now());
        userRepository.save(user);
        logger.info("Admin account reactivated: userId={}", user.getId());
    }

    /**
     * Reset password (send email)
     */
    @Transactional
    public void resetPassword(Long id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản với ID: " + id));

        // Reuse the same short-lived, rate-limited OTP flow that the public
        // reset form validates. No unpersisted reset token is emailed.
        otpService.issueOtp(user.getEmail(), "forgot-password");
        logger.info("Admin password reset requested: userId={}", user.getId());
    }

    /**
     * Bulk actions
     */
    @Transactional
    public void bulkAction(BulkActionRequest request, String actorEmail) {
        if (request == null || request.getAction() == null || request.getAction().isBlank()
                || request.getUserIds() == null || request.getUserIds().isEmpty()) {
            throw new IllegalArgumentException("Vui lòng chọn tài khoản và hành động hợp lệ");
        }
        Set<Long> requestedIds = new LinkedHashSet<>(request.getUserIds());
        if (requestedIds.contains(null)) {
            throw new IllegalArgumentException("Danh sách tài khoản không hợp lệ");
        }
        List<User> users = userRepository.findAllById(requestedIds);
        if (users.size() != requestedIds.size()) {
            throw new IllegalArgumentException("Một hoặc nhiều tài khoản không tồn tại");
        }

        switch (request.getAction().toLowerCase()) {
            case "ban":
                rejectCurrentUser(users, actorEmail, "tự khóa");
                requireAnotherActiveAdmin(activeAdminIds(users));
                users.forEach(user -> {
                    user.setActivated(false);
                    user.setAuthVersion(user.getAuthVersion() + 1);
                    user.setUpdated(LocalDateTime.now());
                });
                userRepository.saveAll(users);
                logger.info("Admin banned {} accounts", users.size());
                break;

            case "unban":
                users.forEach(user -> {
                    user.setActivated(true);
                    user.setAuthVersion(user.getAuthVersion() + 1);
                    user.setUpdated(LocalDateTime.now());
                });
                userRepository.saveAll(users);
                logger.info("Admin unbanned {} accounts", users.size());
                break;

            case "delete":
                rejectCurrentUser(users, actorEmail, "tự xóa");
                requireAnotherActiveAdmin(activeAdminIds(users));
                // Apply the same order/FK and cleanup invariants as a single delete.
                users.forEach(user -> deleteAccount(user.getId(), actorEmail));
                logger.info("Admin deleted {} accounts", users.size());
                break;

            default:
                throw new IllegalArgumentException("Hành động không hợp lệ: " + request.getAction());
        }
    }

    private void rejectCurrentUser(List<User> users, String actorEmail, String action) {
        if (users.stream().anyMatch(user -> sameUser(user, actorEmail))) {
            throw new IllegalArgumentException("Bạn không thể " + action + " tài khoản đang đăng nhập");
        }
    }

    private Set<Long> activeAdminIds(Collection<User> users) {
        return users.stream()
            .filter(User::isActivated)
            .filter(this::isAdmin)
            .map(User::getId)
            .collect(Collectors.toSet());
    }

    private void requireAnotherActiveAdmin(Set<Long> excludedIds) {
        if (excludedIds.isEmpty()) return;
        // Lock all administrator rows before checking the invariant. Concurrent
        // ban/delete/demotion requests therefore observe the previous commit.
        boolean anotherAdminExists = userRepository.findAdministratorsForUpdate().stream()
            .filter(User::isActivated)
            .anyMatch(user -> !excludedIds.contains(user.getId()));
        if (!anotherAdminExists) {
            throw new IllegalArgumentException("Hệ thống phải còn ít nhất một quản trị viên đang hoạt động");
        }
    }

    private boolean isAdmin(User user) {
        return user.getRoles() != null && user.getRoles().stream()
            .anyMatch(role -> "ROLE_ADMIN".equals(role.getRname()));
    }

    private boolean hasHistoricalReferences(Long userId) {
        return orderRepository.existsByUserIdOrShipperId(userId, userId)
            || ratingRepository.existsByUserId(userId)
            || reviewRepository.existsByUserId(userId)
            || returnRequestRepository.existsByUserId(userId)
            || voucherUsageRepository.existsByUserId(userId);
    }

    private static boolean sameUser(User user, String email) {
        return email != null && user.getEmail() != null && user.getEmail().equalsIgnoreCase(email);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeStatus(String value) {
        String status = clean(value).toLowerCase(Locale.ROOT);
        return "active".equals(status) || "inactive".equals(status) ? status : "";
    }

    private String appUrl(String path) {
        String baseUrl = storeProperties.getBaseUrl().trim();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + path;
    }

    /**
     * Search accounts
     */
    public List<AccountDTO> searchAccounts(String keyword) {
        return userRepository.findAdminPage(clean(keyword), "", "",
            PageRequest.of(0, 100, Sort.by("created").descending()))
            .map(this::convertToDTO).getContent();
    }

    /**
     * Convert User entity to AccountDTO
     */
    private AccountDTO convertToDTO(User user) {
        AccountDTO dto = new AccountDTO();
        dto.setId(user.getId());
        dto.setEmail(user.getEmail());
        dto.setName(user.getName());
        dto.setPhone(user.getPhone());
        dto.setAddress(user.getAddress());
        dto.setActivated(user.isActivated());
        dto.setCreated(user.getCreated());
        dto.setUpdated(user.getUpdated());
        
        List<String> roleNames = user.getRoles().stream()
            .map(Role::getRname)
            .collect(Collectors.toList());
        dto.setRoles(roleNames);
        
        return dto;
    }
}
