
package com.smartcare.repository;

import com.smartcare.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.*;
import java.util.*;


public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    List<PurchaseOrder> findBySupplierOrderByCreatedAtDesc(User supplier);
    List<PurchaseOrder> findAllByOrderByCreatedAtDesc();

    // Used when deciding whether a user account can be deleted safely.
    boolean existsBySupplier(User supplier);
}