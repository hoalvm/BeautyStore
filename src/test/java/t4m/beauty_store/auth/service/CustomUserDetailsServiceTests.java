package t4m.beauty_store.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import t4m.beauty_store.auth.entity.Role;
import t4m.beauty_store.auth.entity.User;
import t4m.beauty_store.auth.repository.UserRepository;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomUserDetailsServiceTests {

    @Test
    void reloadsCurrentStatusAndRolesForEveryJwtRequest() {
        UserRepository userRepository = mock(UserRepository.class);
        CustomUserDetailsService service = new CustomUserDetailsService(userRepository);
        User original = user(true, "ROLE_USER");
        User revoked = user(false, "ROLE_ADMIN");
        when(userRepository.findByEmail("customer@example.com"))
            .thenReturn(Optional.of(original), Optional.of(revoked));

        UserDetails firstLoad = service.loadUserByUsername("customer@example.com");
        UserDetails secondLoad = service.loadUserByUsername("customer@example.com");

        assertThat(firstLoad.isEnabled()).isTrue();
        assertThat(firstLoad.getAuthorities())
            .extracting(GrantedAuthority::getAuthority)
            .containsExactly("ROLE_USER");
        assertThat(secondLoad.isEnabled()).isFalse();
        assertThat(secondLoad.getAuthorities())
            .extracting(GrantedAuthority::getAuthority)
            .containsExactly("ROLE_ADMIN");
        verify(userRepository, times(2)).findByEmail("customer@example.com");
    }

    private static User user(boolean activated, String roleName) {
        Role role = new Role();
        role.setRname(roleName);
        User user = new User();
        user.setEmail("customer@example.com");
        user.setPasswd("unused");
        user.setActivated(activated);
        user.setRoles(Set.of(role));
        return user;
    }
}
