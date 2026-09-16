package t4m.beauty_store;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.password.PasswordEncoder;
import t4m.beauty_store.auth.entity.Role;
import t4m.beauty_store.auth.entity.User;
import t4m.beauty_store.auth.repository.RoleRepository;
import t4m.beauty_store.auth.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.TimeZone;

@SpringBootApplication
@EnableScheduling
public class BeautyStoreApplication {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
    }

    private static final Logger logger = LoggerFactory.getLogger(BeautyStoreApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(BeautyStoreApplication.class, args);
    }

    @Bean
    @Order(1)
    public ApplicationRunner initRoles(RoleRepository roleRepository) {
        return args -> {
            List<String> roleNames = List.of("ROLE_USER", "ROLE_VENDOR", "ROLE_SHIPPER", "ROLE_ADMIN");

            for (String roleName : roleNames) {
                if (roleRepository.findByRname(roleName).isEmpty()) {
                    Role role = new Role();
                    role.setRname(roleName);
                    roleRepository.save(role);
                    logger.info("Initialized BeautyStore role: {}", roleName);
                }
            }
        };
    }

    @Bean
    @Order(2)
    @Profile({"dev", "test"})
    public ApplicationRunner initAdminUser(UserRepository userRepository,
                                           RoleRepository roleRepository,
                                           PasswordEncoder passwordEncoder) {
        return args -> {
            String adminEmail = "admin@beautystore.vn";
            if (userRepository.findByEmail(adminEmail).isPresent()
                    || hasUserWithRole(userRepository, "ROLE_ADMIN")) {
                return;
            }

            Role adminRole = roleRepository.findByRname("ROLE_ADMIN")
                    .orElseThrow(() -> new IllegalStateException("ROLE_ADMIN must be initialized first"));
            User adminUser = createDemoUser(
                    adminEmail,
                    passwordEncoder.encode("admin123"),
                    "BeautyStore Administrator",
                    "0123456789",
                    "Văn phòng BeautyStore"
            );
            adminUser.getRoles().add(adminRole);
            userRepository.save(adminUser);
            logger.info("Initialized BeautyStore development admin account");
        };
    }

    @Bean
    @Order(3)
    @Profile({"dev", "test"})
    public ApplicationRunner initShipperUsers(UserRepository userRepository,
                                              RoleRepository roleRepository,
                                              PasswordEncoder passwordEncoder) {
        return args -> {
            String shipperEmail = "shipper@beautystore.vn";
            if (userRepository.findByEmail(shipperEmail).isPresent()
                    || hasUserWithRole(userRepository, "ROLE_SHIPPER")) {
                return;
            }

            Role shipperRole = roleRepository.findByRname("ROLE_SHIPPER")
                    .orElseThrow(() -> new IllegalStateException("ROLE_SHIPPER must be initialized first"));
            User shipper = createDemoUser(
                    shipperEmail,
                    passwordEncoder.encode("shipper123"),
                    "BeautyStore Shipper",
                    "0123456789",
                    "Kho vận BeautyStore, TP. Hồ Chí Minh"
            );
            shipper.getRoles().add(shipperRole);
            userRepository.save(shipper);
            logger.info("Initialized BeautyStore development shipper account");
        };
    }

    @Bean
    @Order(4)
    @Profile({"dev", "test"})
    public ApplicationRunner initRegularUsers(UserRepository userRepository,
                                              RoleRepository roleRepository,
                                              PasswordEncoder passwordEncoder) {
        return args -> {
            String userEmail = "user@beautystore.vn";
            if (userRepository.findByEmail(userEmail).isPresent()
                    || hasUserWithRole(userRepository, "ROLE_USER")) {
                return;
            }

            Role userRole = roleRepository.findByRname("ROLE_USER")
                    .orElseThrow(() -> new IllegalStateException("ROLE_USER must be initialized first"));
            User demoUser = createDemoUser(
                    userEmail,
                    passwordEncoder.encode("user123"),
                    "BeautyStore Demo User",
                    "0123456789",
                    "Quận 3, TP. Hồ Chí Minh"
            );
            demoUser.getRoles().add(userRole);
            userRepository.save(demoUser);
            logger.info("Initialized BeautyStore development customer account");
        };
    }

    private User createDemoUser(String email, String encodedPassword, String name, String phone, String address) {
        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setEmail(email);
        user.setPasswd(encodedPassword);
        user.setName(name);
        user.setPhone(phone);
        user.setAddress(address);
        user.setActivated(true);
        user.setCreated(now);
        user.setUpdated(now);
        return user;
    }

    /**
     * A migrated installation can already have the legacy demo account or a
     * real account for a role.  Do not add another privileged account with a
     * well-known password merely because the branded email address changed.
     */
    private boolean hasUserWithRole(UserRepository userRepository, String roleName) {
        return userRepository.findAll().stream()
                .flatMap(user -> user.getRoles().stream())
                .anyMatch(role -> roleName.equals(role.getRname()));
    }
}
