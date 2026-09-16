package t4m.beauty_store.auth.controller;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;
import t4m.beauty_store.auth.dto.AuthResponse;
import t4m.beauty_store.auth.dto.LoginRequest;
import t4m.beauty_store.auth.dto.RegisterRequest;
import t4m.beauty_store.auth.dto.OtpRequest;
import t4m.beauty_store.auth.dto.ResetPasswordRequest;
import t4m.beauty_store.auth.dto.UpdateProfileRequest;
import t4m.beauty_store.auth.dto.UserProfileResponse;
import t4m.beauty_store.auth.service.UserService;
import t4m.beauty_store.auth.service.OtpService;
import t4m.beauty_store.auth.service.AuthCookieService;
import t4m.beauty_store.auth.exception.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private final UserService userService;
    private final OtpService otpService;
    private final AuthCookieService authCookieService;

    public AuthController(UserService userService, OtpService otpService,
                          AuthCookieService authCookieService) {
        this.userService = userService;
        this.otpService = otpService;
        this.authCookieService = authCookieService;
    }

    /**
     * Register a new user and send OTP for activation.
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest dto,
                                                 HttpServletRequest request) {
        try {
            otpService.checkClientRequestLimit(request.getRemoteAddr());
            userService.register(dto);
            return ResponseEntity.ok(new AuthResponse(dto.getEmail(), dto.getRole(), "Registration successful, please check your email for OTP"));
        } catch (EmailAlreadyExistsException | InvalidRoleException e) {
            logger.warn("Registration request rejected");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    /**
     * Send OTP for account activation.
     */
    @PostMapping("/active-account")
    public ResponseEntity<AuthResponse> activeAccount(@RequestBody Map<String, String> requestBody,
                                                      HttpServletRequest request) {
        try {
            otpService.checkClientRequestLimit(request.getRemoteAddr());
            String email = requestBody.get("email");
            if (email == null || email.trim().isEmpty()) {
                throw new IllegalArgumentException("Email is required");
            }
            userService.sendActivationOtp(email);
            return ResponseEntity.ok(new AuthResponse(email, null, "OTP sent to your email for activation"));
        } catch (UserNotFoundException | AccountNotActivatedException | IllegalArgumentException e) {
            logger.warn("Activation OTP request rejected");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    /**
     * Verify OTP and activate account.
     */
    @PostMapping("/verify-account")
    public ResponseEntity<AuthResponse> verifyOtp(@Valid @RequestBody OtpRequest dto,
                                                  HttpServletRequest request) {
        try {
            otpService.checkClientRequestLimit(request.getRemoteAddr());
            userService.verifyOtpAndActivate(dto.getEmail(), dto.getOtp());
            return ResponseEntity.ok(new AuthResponse(dto.getEmail(), null, "Account activated successfully"));
        } catch (UserNotFoundException | OtpInvalidException | OtpExpiredException | AccountNotActivatedException e) {
            logger.warn("OTP verification request rejected");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    /**
     * Login user and return JWT.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest dto) {
        try {
            String token = userService.login(dto.getEmail(), dto.getPassword());
            String role = userService.getUserRole(dto.getEmail());
            return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, authCookieService.issue(token).toString())
                .body(new AuthResponse(dto.getEmail(), role, "Login successful", token));
        } catch (UserNotFoundException | InvalidCredentialsException | AccountNotActivatedException e) {
            logger.warn("Login request rejected");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage(), e);
        }
    }

    /**
     * Send OTP for password reset.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<AuthResponse> forgotPassword(@RequestBody Map<String, String> requestBody,
                                                       HttpServletRequest request) {
        try {
            otpService.checkClientRequestLimit(request.getRemoteAddr());
            String email = requestBody.get("email");
            if (email == null || email.trim().isEmpty()) {
                throw new IllegalArgumentException("Email is required");
            }
            userService.sendForgotPasswordOtp(email);
            return ResponseEntity.ok(new AuthResponse(email, null, "OTP sent to your email"));
        } catch (UserNotFoundException | IllegalArgumentException e) {
            logger.warn("Password reset OTP request rejected");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    /**
     * Reset password with OTP.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<AuthResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest dto,
                                                      HttpServletRequest request) {
        try {
            otpService.checkClientRequestLimit(request.getRemoteAddr());
            userService.resetPassword(dto.getEmail(), dto.getOtp(), dto.getNewPassword());
            return ResponseEntity.ok(new AuthResponse(dto.getEmail(), null, "Password reset successfully"));
        } catch (UserNotFoundException | OtpInvalidException | OtpExpiredException e) {
            logger.warn("Password reset request rejected");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    /**
     * Get user profile information.
     */
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(Authentication authentication) {
        String email = authentication.getName();
        try {
            UserProfileResponse profile = userService.getUserProfile(email);
            return ResponseEntity.ok(profile);
        } catch (UserNotFoundException e) {
            logger.warn("Profile fetch request rejected");
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    /**
     * Update user profile information.
     */
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest dto) {
        String email = authentication.getName();
        try {
            userService.updateUserProfile(email, dto);
            return ResponseEntity.ok(Map.of(
                "message", "Profile updated successfully",
                "email", email
            ));
        } catch (UserNotFoundException e) {
            logger.warn("Profile update request rejected");
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    /**
     * Change password for authenticated user.
     */
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            Authentication authentication,
            @RequestBody Map<String, String> request) {
        String email = authentication.getName();
        try {
            String currentPassword = request.get("currentPassword");
            String newPassword = request.get("newPassword");
            
            if (currentPassword == null || newPassword == null) {
                throw new IllegalArgumentException("Mật khẩu hiện tại và mật khẩu mới là bắt buộc");
            }
            
            if (newPassword.length() < 6) {
                throw new IllegalArgumentException("Mật khẩu mới phải có ít nhất 6 ký tự");
            }
            
            userService.changePassword(email, currentPassword, newPassword);
            
            return ResponseEntity.ok(Map.of(
                "message", "Password changed successfully",
                "email", email
            ));
        } catch (InvalidCredentialsException e) {
            logger.warn("Password change request rejected");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu hiện tại không đúng", e);
        } catch (UserNotFoundException e) {
            logger.warn("Password change request rejected");
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Password change request failed");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Không thể đổi mật khẩu", e);
        }
    }

    /**
     * Get user profile information (legacy endpoint).
     */
    @GetMapping("/user")
    @Deprecated(forRemoval = true)
    public ResponseEntity<UserProfileResponse> getUserProfile(Authentication authentication) {
        String email = authentication.getName();
        try {
            UserProfileResponse profile = userService.getUserProfile(email);
            return ResponseEntity.ok(profile);
        } catch (UserNotFoundException e) {
            logger.warn("Profile fetch request rejected");
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    /**
     * Update user profile information (legacy endpoint).
     */
    @PutMapping("/update-profile")
    @Deprecated(forRemoval = true)
    public ResponseEntity<AuthResponse> updateProfileLegacy(Authentication authentication,
                                                            @Valid @RequestBody UpdateProfileRequest dto) {
        String email = authentication.getName();
        try {
            userService.updateUserProfile(email, dto);
            return ResponseEntity.ok(new AuthResponse(email, null, "Profile updated successfully"));
        } catch (UserNotFoundException e) {
            logger.warn("Profile update request rejected");
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    /** Clears the HttpOnly page-navigation credential even if the JWT expired. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
            .header(HttpHeaders.SET_COOKIE, authCookieService.clear().toString())
            .build();
    }

}
