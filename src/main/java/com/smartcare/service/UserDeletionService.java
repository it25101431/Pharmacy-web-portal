package com.smartcare.service;

import com.smartcare.model.Role;
import com.smartcare.model.User;
import com.smartcare.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Permanently deletes a user account, but only when doing so cannot damage the
 * pharmacy's records.
 *
 * A user who has taken part in real business - placed an order, had a
 * prescription verified, supplied stock, carried a delivery - is part of the
 * dispensing and financial history, which a pharmacy must keep. Deleting them
 * would either be blocked by the database's foreign keys or erase that history.
 * Those accounts must be DEACTIVATED instead, which removes access but keeps the
 * record.
 *
 * An account with no history - a rejected registration, a duplicate, an account
 * created by mistake - can be removed completely. Its notifications and any
 * supplier price list are not history, so they are removed with it.
 *
 * The same check is used to decide whether the Delete button is shown and to
 * enforce the rule when the request arrives, so the two can never disagree.
 */
@Service
@RequiredArgsConstructor
public class UserDeletionService {

    private final UserRepository users;
    private final OrderRepository orders;
    private final PrescriptionRepository prescriptions;
    private final PurchaseOrderRepository purchaseOrders;
    private final DeliveryNoteRepository deliveryNotes;
    private final NotificationRepository notifications;
    private final SupplierOfferRepository offers;

    /**
     * Explains why this user cannot be deleted, or returns null when they can.
     *
     * @param target the account the admin wants to delete
     * @param admin  the admin making the request
     */
    @Transactional(readOnly = true)
    public String blockReason(User target, User admin) {
        if (target.getId().equals(admin.getId()))
            return "You cannot delete your own account.";
        if (target.getRole() == Role.ADMIN && users.countByRole(Role.ADMIN) <= 1)
            return "This is the only admin account. Create another admin before deleting it.";
        if (orders.existsByCustomer(target))
            return "This customer has placed orders.";
        if (orders.existsByDeliveryStaff(target))
            return "This courier has delivery history.";
        if (prescriptions.existsByCustomer(target))
            return "This customer has uploaded prescriptions.";
        if (prescriptions.existsByVerifiedBy(target))
            return "This pharmacist has reviewed prescriptions.";
        if (purchaseOrders.existsBySupplier(target))
            return "This supplier has purchase orders.";
        if (deliveryNotes.existsByStaff(target))
            return "This courier has written delivery notes.";
        return null;
    }

    /** True when the account has no history and may be removed permanently. */
    @Transactional(readOnly = true)
    public boolean canDelete(User target, User admin) {
        return blockReason(target, admin) == null;
    }

    /**
     * Deletes the account permanently, after re-checking the rule inside the same
     * transaction (the user might have placed an order since the page loaded).
     */
    @Transactional
    public void delete(Long id, User admin) {
        User target = users.findById(id)
                .orElseThrow(() -> new IllegalStateException("User not found."));
        String reason = blockReason(target, admin);
        if (reason != null)
            throw new IllegalStateException(reason + " Their records must be kept, so deactivate the account instead.");

        // Not history, so these go with the account.
        notifications.deleteByUser(target);
        offers.deleteBySupplier(target);
        users.delete(target);
    }
}
