package com.smartcare.repository;

import com.smartcare.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.*;
import java.util.*;


public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    List<User> findByRoleAndStatus(Role role, UserStatus status);
    List<User> findAllByOrderByCreatedAtDesc();

    // Used when deciding whether a user account can be deleted safely.
    long countByRole(Role role);
}
