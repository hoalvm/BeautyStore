package t4m.beauty_store.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import t4m.beauty_store.auth.entity.Role;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByRname(String rname);
}