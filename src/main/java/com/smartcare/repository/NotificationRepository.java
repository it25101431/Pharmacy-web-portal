package com.smartcare.repository;

import com.smartcare.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.*;
import java.util.*;


public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserOrderByCreatedAtDesc(User user);
    long countByUserAndSeenFalse(User user);

    // Used when deciding whether a user account can be deleted safely.
    void deleteByUser(User user);
}

