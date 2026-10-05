package com.smartcare.repository;

import com.smartcare.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.*;
import java.util.*;


public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {
    List<Prescription> findByCustomerOrderByUploadedAtDesc(User customer);
    List<Prescription> findByStatusOrderByUploadedAtAsc(PrescriptionStatus status);
    List<Prescription> findTop20ByStatusNotOrderByUploadedAtDesc(PrescriptionStatus status);

    // Used when deciding whether a user account can be deleted safely.
    boolean existsByCustomer(User customer);
    boolean existsByVerifiedBy(User verifiedBy);
}
