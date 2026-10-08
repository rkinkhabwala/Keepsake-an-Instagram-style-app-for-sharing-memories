package com.keepsake.user.repository;

import com.keepsake.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCaseOrUsernameIgnoreCase(String email, String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUsername(String username);
}
