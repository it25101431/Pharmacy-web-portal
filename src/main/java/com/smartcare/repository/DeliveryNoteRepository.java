package com.smartcare.repository;

import com.smartcare.model.CustomerOrder;
import com.smartcare.model.DeliveryNote;
import com.smartcare.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeliveryNoteRepository extends JpaRepository<DeliveryNote, Long> {

    /** Every note this courier has written, newest first. */
    List<DeliveryNote> findByStaffOrderByIdDesc(User staff);

    /** Notes on one order - shown to the admin when investigating a delivery issue. */
    List<DeliveryNote> findByOrderOrderByIdDesc(CustomerOrder order);

    // Used when deciding whether a user account can be deleted safely.
    boolean existsByStaff(User staff);
}