package t4m.beauty_store.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import t4m.beauty_store.auth.entity.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    @EntityGraph(attributePaths = "roles")
    @Query(value = "select distinct u from User u left join u.roles r where " +
        "(:search = '' or lower(coalesce(u.name, '')) like lower(concat('%', :search, '%')) " +
        "or lower(u.email) like lower(concat('%', :search, '%')) or coalesce(u.phone, '') like concat('%', :search, '%')) " +
        "and (:role = '' or r.rname = :role) " +
        "and (:status = '' or (:status = 'active' and u.activated = true) " +
        "or (:status = 'inactive' and u.activated = false))",
        countQuery = "select count(distinct u.id) from User u left join u.roles r where " +
        "(:search = '' or lower(coalesce(u.name, '')) like lower(concat('%', :search, '%')) " +
        "or lower(u.email) like lower(concat('%', :search, '%')) or coalesce(u.phone, '') like concat('%', :search, '%')) " +
        "and (:role = '' or r.rname = :role) " +
        "and (:status = '' or (:status = 'active' and u.activated = true) " +
        "or (:status = 'inactive' and u.activated = false))")
    Page<User> findAdminPage(@Param("search") String search, @Param("role") String role,
        @Param("status") String status, Pageable pageable);

    /** Serializes destructive administrator mutations so two requests cannot
     * concurrently remove the final active administrators. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select distinct user from User user join fetch user.roles role " +
           "where role.rname = 'ROLE_ADMIN'")
    List<User> findAdministratorsForUpdate();
}
