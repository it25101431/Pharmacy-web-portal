package com.smartcare.repository;

import com.smartcare.model.SupplierOffer;
import com.smartcare.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupplierOfferRepository extends JpaRepository<SupplierOffer, Long> {

    /** A supplier's own catalogue, newest first. */
    List<SupplierOffer> findBySupplierOrderByIdDesc(User supplier);

    /** Offers the pharmacy can actually buy right now - used by the store manager. */
    List<SupplierOffer> findByAvailableTrueOrderByProductNameAsc();

    /** Stops the same supplier listing the same product twice. */
    boolean existsBySupplierAndProductNameIgnoreCase(User supplier, String productName);

    /** Same check for an edit: ignores the row being edited, so saving it unchanged is allowed. */
    boolean existsBySupplierAndProductNameIgnoreCaseAndIdNot(User supplier, String productName, Long id);

    // Used when deciding whether a user account can be deleted safely.
    void deleteBySupplier(User supplier);
}