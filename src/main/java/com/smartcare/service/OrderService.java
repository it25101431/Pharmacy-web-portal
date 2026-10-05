package com.smartcare.service;

import com.smartcare.model.*;
import com.smartcare.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;

/** Business rules for orders, prescription verification and delivery. */
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orders;
    private final MedicineRepository medicines;
    private final PrescriptionRepository prescriptions;
    private final UserRepository users;
    private final NotificationService notes;
    private final SecureRandom random = new SecureRandom();

    /** Validates stock + prescription rules, deducts stock and creates the order in one transaction. */
    @Transactional
    public CustomerOrder checkout(User customer, Map<Long, Integer> cart, String address,
                                  PaymentMethod method, Long prescriptionId) {
        if (cart.isEmpty()) throw new IllegalStateException("Your cart is empty.");
        if (address == null || address.isBlank()) throw new IllegalStateException("Delivery address is required.");

        CustomerOrder order = new CustomerOrder();
        order.setCustomer(customer);
        order.setDeliveryAddress(address.trim());
        order.setPaymentMethod(method);

        boolean needsRx = false;
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> e : cart.entrySet()) {
            Medicine m = medicines.findById(e.getKey()).filter(Medicine::isActive)
                    .orElseThrow(() -> new IllegalStateException("A medicine in your cart is no longer available."));
            int qty = e.getValue();
            if (qty <= 0) throw new IllegalStateException("Invalid quantity for " + m.getName() + ".");
            if (m.isExpired()) throw new IllegalStateException(m.getName() + " has expired and cannot be ordered.");
            if (m.getStock() < qty)
                throw new IllegalStateException("Insufficient stock for " + m.getName() + " (available: " + m.getStock() + ").");
            m.setStock(m.getStock() - qty);
            if (m.isLowStock()) {
                notes.sendToRole(Role.STORE_MANAGER, "Low stock: " + m.getName() + " (" + m.getStock() + " left).");
                notes.sendToRole(Role.PHARMACIST, "Low stock: " + m.getName() + " (" + m.getStock() + " left).");
            }
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setMedicine(m);
            item.setQuantity(qty);
            item.setUnitPrice(m.getPrice());
            order.getItems().add(item);
            total = total.add(m.getPrice().multiply(BigDecimal.valueOf(qty)));
            needsRx |= m.isPrescriptionRequired();
        }

        if (needsRx) {
            if (prescriptionId == null)
                throw new IllegalStateException("Your cart contains prescription-only medicines. Please select an uploaded prescription.");
            Prescription p = prescriptions.findById(prescriptionId)
                    .filter(x -> x.getCustomer().getId().equals(customer.getId()))
                    .orElseThrow(() -> new IllegalStateException("Prescription not found."));
            if (p.getStatus() == PrescriptionStatus.REJECTED)
                throw new IllegalStateException("That prescription was rejected. Please upload a new one.");
            // A prescription authorises ONE dispensing only. Re-using an already-approved
            // prescription on a second order would let a customer obtain unlimited
            // prescription medicines - including different medicines - from a single
            // pharmacist approval, with no pharmacist ever seeing the new order.
            if (orders.existsByPrescription(p))
                throw new IllegalStateException("That prescription has already been used for another order. "
                        + "Please upload a new prescription for this order.");
            order.setPrescription(p);
            // Every prescription order waits for a pharmacist to review THIS order,
            // even when the prescription document was approved earlier.
            order.setStatus(OrderStatus.PLACED);
            if (p.getStatus() == PrescriptionStatus.APPROVED) {
                // Approved earlier but not yet tied to an order: send it back for review
                // against this specific order's medicines.
                p.setStatus(PrescriptionStatus.PENDING);
                p.setPharmacistNote(null);
                p.setVerifiedBy(null);
            }
            notes.sendToRole(Role.PHARMACIST, "New prescription order #" + order.getId()
                    + " is awaiting verification.");
        } else {
            order.setStatus(OrderStatus.VERIFIED);
        }

        order.setTotal(total);
        order.setPaymentStatus(method == PaymentMethod.COD ? PaymentStatus.PENDING : PaymentStatus.PAID);
        order.setDeliveryOtp(String.format("%04d", random.nextInt(10000)));
        orders.save(order);
        notes.send(customer, "Order #" + order.getId() + " placed. Status: " + order.getStatus() + ".");
        return order;
    }

    /** Pharmacist approves/rejects a prescription; linked orders move forward or are rejected (stock restored). */
    @Transactional
    public void decidePrescription(Long id, boolean approve, String note, User pharmacist) {
        Prescription p = prescriptions.findById(id).orElseThrow(() -> new IllegalStateException("Prescription not found."));
        if (p.getStatus() != PrescriptionStatus.PENDING) throw new IllegalStateException("This prescription was already reviewed.");
        String trimmed = note == null ? "" : note.trim();
        String n = trimmed.length() > 250 ? trimmed.substring(0, 250) : trimmed;
        if (!approve && n.isBlank()) throw new IllegalStateException("Please give a reason when rejecting a prescription.");
        p.setStatus(approve ? PrescriptionStatus.APPROVED : PrescriptionStatus.REJECTED);
        p.setPharmacistNote(n);
        p.setVerifiedBy(pharmacist);
        for (CustomerOrder o : orders.findByPrescription(p)) {
            if (o.getStatus() != OrderStatus.PLACED) continue;
            if (approve) {
                o.setStatus(OrderStatus.VERIFIED);
            } else {
                o.setStatus(OrderStatus.REJECTED);
                for (OrderItem i : o.getItems()) i.getMedicine().setStock(i.getMedicine().getStock() + i.getQuantity());
                // Card/mobile orders were charged at checkout; a rejected order must not stay "PAID".
                boolean wasPaid = o.getPaymentStatus() == PaymentStatus.PAID;
                if (wasPaid) o.setPaymentStatus(PaymentStatus.REFUNDED);
                notes.send(o.getCustomer(), "Order #" + o.getId() + " was rejected because its prescription was rejected."
                        + (wasPaid ? " Your payment has been refunded." : ""));
            }
        }
        notes.send(p.getCustomer(), "Your prescription #" + p.getId() + " was " + (approve ? "approved." : "rejected: " + n));
    }

    @Transactional
    public void pack(Long orderId) {
        CustomerOrder o = find(orderId);
        if (o.getStatus() != OrderStatus.VERIFIED) throw new IllegalStateException("Only verified orders can be packed.");
        o.setStatus(OrderStatus.PACKED);
        notes.send(o.getCustomer(), "Order #" + o.getId() + " has been packed.");
    }

    @Transactional
    public void assignDelivery(Long orderId, Long staffId) {
        CustomerOrder o = find(orderId);
        boolean reassign = o.getStatus() == OrderStatus.DISPATCHED && o.getDeliveryStatus() == DeliveryStatus.ISSUE;
        if (o.getStatus() != OrderStatus.PACKED && !reassign)
            throw new IllegalStateException("Only packed orders (or orders with a reported delivery issue) can be assigned.");
        User s = users.findById(staffId)
                .filter(u -> u.getRole() == Role.DELIVERY_STAFF && u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("Select a valid delivery staff member."));
        o.setDeliveryStaff(s);
        o.setDeliveryStatus(DeliveryStatus.ASSIGNED);
        o.setStatus(OrderStatus.DISPATCHED);
        o.setDeliveryIssue(null);
        notes.send(s, "Order #" + o.getId() + " was assigned to you for delivery.");
        notes.send(o.getCustomer(), "Order #" + o.getId() + " has been dispatched.");
    }

    @Transactional
    public void updateDelivery(Long orderId, User staff, DeliveryStatus target) {
        CustomerOrder o = ownedBy(orderId, staff);
        if (target != DeliveryStatus.PICKED_UP && target != DeliveryStatus.OUT_FOR_DELIVERY)
            throw new IllegalStateException("Invalid delivery status.");

        DeliveryStatus current = o.getDeliveryStatus();
        // Delivery only ever moves forward: ASSIGNED -> PICKED_UP -> OUT_FOR_DELIVERY -> DELIVERED.
        // Without this check a courier could set "Out for delivery" back to "Picked up",
        // or reopen an order that was already delivered or flagged with an issue.
        if (current == DeliveryStatus.DELIVERED)
            throw new IllegalStateException("This order was already delivered.");
        if (current == DeliveryStatus.ISSUE)
            throw new IllegalStateException("This order has a reported issue. An admin must reassign it first.");
        if (current == DeliveryStatus.NOT_ASSIGNED)
            throw new IllegalStateException("This order has not been assigned to you yet.");
        if (target.ordinal() <= current.ordinal())
            throw new IllegalStateException("Delivery status cannot move backwards from "
                    + label(current) + " to " + label(target) + ".");
        // One step at a time. Jumping ASSIGNED -> OUT_FOR_DELIVERY would skip the
        // pick-up record, so the order's history would not show when the courier
        // actually collected it. DELIVERED is reached through confirmDelivery(),
        // which also requires the customer's OTP, never through this method.
        if (target == DeliveryStatus.DELIVERED)
            throw new IllegalStateException("Enter the customer's confirmation OTP to mark this order delivered.");
        if (target.ordinal() != current.ordinal() + 1)
            throw new IllegalStateException("Delivery steps cannot be skipped. The next step after "
                    + label(current) + " is " + label(DeliveryStatus.values()[current.ordinal() + 1]) + ".");

        o.setDeliveryStatus(target);
        notes.send(o.getCustomer(), "Order #" + o.getId() + " delivery: " + label(target) + ".");
    }

    private static String label(DeliveryStatus s) {
        return s.name().replace('_', ' ').toLowerCase();
    }

    /** Delivery confirmation uses the 4-digit OTP the customer sees on the order page. */
    @Transactional
    public void confirmDelivery(Long orderId, User staff, String otp) {
        CustomerOrder o = ownedBy(orderId, staff);
        if (o.getDeliveryStatus() == DeliveryStatus.DELIVERED)
            throw new IllegalStateException("This order was already delivered.");
        if (o.getDeliveryStatus() != DeliveryStatus.OUT_FOR_DELIVERY)
            throw new IllegalStateException("Mark the order as out for delivery before confirming it.");
        if (otp == null || !o.getDeliveryOtp().equals(otp.trim())) throw new IllegalStateException("Incorrect confirmation OTP.");
        o.setDeliveryStatus(DeliveryStatus.DELIVERED);
        o.setStatus(OrderStatus.DELIVERED);
        o.setDeliveredAt(LocalDateTime.now());
        if (o.getPaymentStatus() == PaymentStatus.PENDING) o.setPaymentStatus(PaymentStatus.PAID); // cash on delivery collected
        notes.send(o.getCustomer(), "Order #" + o.getId() + " was delivered. Thank you!");
    }

    @Transactional
    public void reportIssue(Long orderId, User staff, String reason) {
        CustomerOrder o = ownedBy(orderId, staff);
        if (o.getDeliveryStatus() == DeliveryStatus.DELIVERED)
            throw new IllegalStateException("This order was already delivered and cannot be flagged.");
        if (reason == null || reason.isBlank()) throw new IllegalStateException("Please describe the delivery issue.");
        String r = reason.length() > 250 ? reason.substring(0, 250) : reason.trim();
        o.setDeliveryStatus(DeliveryStatus.ISSUE);
        o.setDeliveryIssue(r);
        notes.sendToRole(Role.ADMIN, "Delivery issue on order #" + o.getId() + ": " + r);
        notes.send(o.getCustomer(), "There was a problem delivering order #" + o.getId() + ": " + r + ". We will contact you.");
    }

    private CustomerOrder find(Long id) {
        return orders.findById(id).orElseThrow(() -> new IllegalStateException("Order not found."));
    }

    private CustomerOrder ownedBy(Long id, User staff) {
        CustomerOrder o = find(id);
        if (o.getDeliveryStaff() == null || !o.getDeliveryStaff().getId().equals(staff.getId()))
            throw new IllegalStateException("This delivery is not assigned to you.");
        if (o.getDeliveryStatus() == DeliveryStatus.DELIVERED) throw new IllegalStateException("This order is already delivered.");
        return o;
    }
}
