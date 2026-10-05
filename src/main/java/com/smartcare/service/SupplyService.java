
package com.smartcare.service;

import com.smartcare.model.*;
import com.smartcare.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Purchase-order lifecycle between the pharmacy and its suppliers. */
@Service
@RequiredArgsConstructor
public class SupplyService {
    private final PurchaseOrderRepository pos;
    private final MedicineRepository medicines;
    private final UserRepository users;
    private final NotificationService notes;

    @Transactional
    public PurchaseOrder create(Long supplierId, Long medicineId, int qty) {
        if (qty <= 0) throw new IllegalStateException("Quantity must be greater than zero.");
        User s = users.findById(supplierId)
                .filter(u -> u.getRole() == Role.SUPPLIER && u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("Select a valid supplier."));
        Medicine m = medicines.findById(medicineId).orElseThrow(() -> new IllegalStateException("Medicine not found."));
        PurchaseOrder po = new PurchaseOrder();
        po.setSupplier(s);
        po.setMedicine(m);
        po.setQuantity(qty);
        po.setDueDate(LocalDate.now().plusDays(7));
        pos.save(po);
        notes.send(s, "New purchase order #" + po.getId() + ": " + m.getName() + " x " + qty + ".");
        return po;
    }

    @Transactional
    public void setAvailability(Long id, User supplier, PoStatus status) {
        PurchaseOrder po = own(id, supplier);
        if (po.getStatus() == PoStatus.DELIVERED) throw new IllegalStateException("This order is already delivered.");
        if (status != PoStatus.IN_STOCK && status != PoStatus.OUT_OF_STOCK) throw new IllegalStateException("Invalid availability status.");
        po.setStatus(status);
        if (status == PoStatus.OUT_OF_STOCK)
            notes.sendToRole(Role.STORE_MANAGER, "Supplier " + supplier.getFullName() + " cannot supply PO #" + po.getId()
                    + " (" + po.getMedicine().getName() + "). Consider an alternate supplier.");
    }

    /** Confirming shipment delivery adds the received quantity to the medicine stock. */
    @Transactional
    public void deliver(Long id, User supplier) {
        PurchaseOrder po = own(id, supplier);
        if (po.getStatus() != PoStatus.IN_STOCK) throw new IllegalStateException("Mark the order as In Stock before confirming delivery.");
        po.setStatus(PoStatus.DELIVERED);
        po.setDeliveredAt(LocalDateTime.now());
        Medicine m = po.getMedicine();
        m.setStock(m.getStock() + po.getQuantity());
        notes.sendToRole(Role.STORE_MANAGER, "PO #" + po.getId() + " delivered: " + m.getName() + " x " + po.getQuantity() + ".");
        notes.sendToRole(Role.PHARMACIST, "Stock received for " + m.getName() + " (+" + po.getQuantity() + "). Please verify batch and expiry.");
    }

    @Transactional
    public void setPayment(Long id, SupplierPayment payment) {
        PurchaseOrder po = pos.findById(id).orElseThrow(() -> new IllegalStateException("Purchase order not found."));
        po.setPaymentStatus(payment);
        notes.send(po.getSupplier(), "Payment status for PO #" + po.getId() + " is now " + payment + ".");
    }

    /**
     * Cancels a purchase order the pharmacy no longer wants. Only an order the
     * supplier has not yet delivered can be cancelled: once stock has arrived,
     * the record is part of the inventory and payment history and must be kept.
     */
    @Transactional
    public void cancel(Long id) {
        PurchaseOrder po = pos.findById(id).orElseThrow(() -> new IllegalStateException("Purchase order not found."));
        if (po.getStatus() == PoStatus.DELIVERED)
            throw new IllegalStateException("PO #" + po.getId() + " was already delivered and cannot be cancelled. "
                    + "Its stock and payment history must be kept.");
        // Money already moved, so the record is financial history even though the
        // goods never arrived. Deleting it would leave a payment with nothing to
        // account for; the shortage should be settled with the supplier instead.
        if (po.getPaymentStatus() != SupplierPayment.PENDING)
            throw new IllegalStateException("PO #" + po.getId() + " is marked " + po.getPaymentStatus()
                    + " and cannot be cancelled. Settle the payment with the supplier first.");
        notes.send(po.getSupplier(), "Purchase order #" + po.getId() + " was cancelled by the pharmacy.");
        pos.delete(po);
    }

    private PurchaseOrder own(Long id, User supplier) {
        PurchaseOrder po = pos.findById(id).orElseThrow(() -> new IllegalStateException("Purchase order not found."));
        if (!po.getSupplier().getId().equals(supplier.getId())) throw new IllegalStateException("This purchase order is not yours.");
        return po;
    }
}