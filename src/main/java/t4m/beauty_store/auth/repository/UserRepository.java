package t4m.beauty_store.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import t4m.beauty_store.auth.entity.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    /** Serializes destructive administrator mutations so two requests cannot
     * concurrently remove the final active administrators. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select distinct user from User user join fetch user.roles role " +
           "where role.rname = 'ROLE_ADMIN'")
    List<User> findAdministratorsForUpdate();
}
