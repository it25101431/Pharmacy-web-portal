package com.smartcare.repository;

import com.smartcare.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.*;
import java.util.*;


public interface OrderRepository extends JpaRepository<CustomerOrder, Long> {
    List<CustomerOrder> findByCustomerOrderByCreatedAtDesc(User customer);
    List<CustomerOrder> findByDeliveryStaffOrderByCreatedAtDesc(User staff);
    List<CustomerOrder> findByStatusOrderByCreatedAtAsc(OrderStatus status);
    List<CustomerOrder> findByPrescription(Prescription prescription);

    /** True when this prescription is already attached to an order (one prescription = one dispensing). */
    boolean existsByPrescription(Prescription prescription);
    List<CustomerOrder> findAllByOrderByCreatedAtDesc();
    List<CustomerOrder> findByCreatedAtBetweenAndStatusNot(LocalDateTime from, LocalDateTime to, OrderStatus status);

    // Used when deciding whether a user account can be deleted safely.
    boolean existsByCustomer(User customer);
    boolean existsByDeliveryStaff(User deliveryStaff);
}
