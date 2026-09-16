package t4m.beauty_store.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import t4m.beauty_store.auth.repository.UserRepository;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    private static final Logger logger = LoggerFactory.getLogger(CustomUserDetailsService.class);

    private final UserRepository userRepository;

    @Autowired
    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        logger.debug("Loading authenticated user details");
        try {
            return userRepository.findByEmail(email).orElseThrow(() -> {
                logger.warn("Authenticated user lookup failed");
                return new UsernameNotFoundException("User not found with email: " + email);
            });
        } catch (UsernameNotFoundException exception) {
            throw exception;
        } catch (Exception e) {
            logger.error("Authenticated user lookup failed: {}", e.getClass().getSimpleName());
            throw new UsernameNotFoundException("Error loading user", e);
        }
    }
}
