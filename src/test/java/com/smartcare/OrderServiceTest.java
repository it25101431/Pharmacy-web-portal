package com.smartcare;

import com.smartcare.model.*;
import com.smartcare.repository.*;
import com.smartcare.service.OrderService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the ordering rules that matter most: stock must not go negative or be
 * silently lost, a prescription must not authorise more than the order it was
 * approved for, and a delivery must not move backwards or be confirmed without the
 * customer's OTP.
 *
 * Each test runs inside a transaction that is rolled back afterwards, against an
 * in-memory H2 database, so they never touch real data and need no MySQL server.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderServiceTest {

    @Autowired OrderService orderService;
    @Autowired UserRepository users;
    @Autowired MedicineRepository medicines;
    @Autowired PrescriptionRepository prescriptions;
    @Autowired OrderRepository orders;
    @Autowired PasswordEncoder encoder;

    private User customer, pharmacist, courier;
    private Medicine plainMedicine, rxMedicine;

    @BeforeEach
    void setUp() {
        customer   = newUser("t_customer",   Role.CUSTOMER);
        pharmacist = newUser("t_pharmacist", Role.PHARMACIST);
        courier    = newUser("t_courier",    Role.DELIVERY_STAFF);
        plainMedicine = newMedicine("Test Paracetamol", 20, false);
        rxMedicine    = newMedicine("Test Amoxicillin", 20, true);
    }

    // ---------------- stock ----------------

    @Test
    @DisplayName("Checkout reduces stock by exactly the quantity ordered")
    void checkoutReducesStock() {
        orderService.checkout(customer, Map.of(plainMedicine.getId(), 3),
                "12 Test Road", PaymentMethod.CARD, null);
        assertEquals(17, medicines.findById(plainMedicine.getId()).orElseThrow().getStock());
    }

    @Test
    @DisplayName("Checkout is refused when stock is insufficient, and stock is left untouched")
    void checkoutRefusesOverselling() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                orderService.checkout(customer, Map.of(plainMedicine.getId(), 999),
                        "12 Test Road", PaymentMethod.CARD, null));
        assertTrue(ex.getMessage().contains("Insufficient stock"));
        assertEquals(20, medicines.findById(plainMedicine.getId()).orElseThrow().getStock(),
                "a failed checkout must not consume stock");
    }

    @Test
    @DisplayName("Rejecting a prescription returns the reserved stock to the shelf")
    void rejectingPrescriptionRestoresStock() {
        Prescription p = newPrescription(PrescriptionStatus.PENDING);
        orderService.checkout(customer, Map.of(rxMedicine.getId(), 4),
                "12 Test Road", PaymentMethod.CARD, p.getId());
        assertEquals(16, medicines.findById(rxMedicine.getId()).orElseThrow().getStock());

        orderService.decidePrescription(p.getId(), false, "Illegible", pharmacist);
        assertEquals(20, medicines.findById(rxMedicine.getId()).orElseThrow().getStock(),
                "stock must be returned when the order is rejected");
    }

    // ---------------- prescriptions ----------------

    @Test
    @DisplayName("A prescription-only medicine cannot be ordered with no prescription")
    void prescriptionRequired() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                orderService.checkout(customer, Map.of(rxMedicine.getId(), 1),
                        "12 Test Road", PaymentMethod.CARD, null));
        assertTrue(ex.getMessage().contains("prescription"));
    }

    @Test
    @DisplayName("An approved prescription cannot be reused on a second order")
    void prescriptionCannotBeReused() {
        Prescription p = newPrescription(PrescriptionStatus.APPROVED);
        orderService.checkout(customer, Map.of(rxMedicine.getId(), 1),
                "12 Test Road", PaymentMethod.CARD, p.getId());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                orderService.checkout(customer, Map.of(rxMedicine.getId(), 1),
                        "12 Test Road", PaymentMethod.CARD, p.getId()));
        assertTrue(ex.getMessage().contains("already been used"),
                "one approval must authorise one dispensing only");
    }

    @Test
    @DisplayName("A prescription order waits for review even if the document was approved earlier")
    void previouslyApprovedPrescriptionStillNeedsReview() {
        Prescription p = newPrescription(PrescriptionStatus.APPROVED);
        CustomerOrder o = orderService.checkout(customer, Map.of(rxMedicine.getId(), 1),
                "12 Test Road", PaymentMethod.CARD, p.getId());

        assertEquals(OrderStatus.PLACED, o.getStatus(), "must not skip pharmacist review");
        assertEquals(PrescriptionStatus.PENDING,
                prescriptions.findById(p.getId()).orElseThrow().getStatus(),
                "the prescription is sent back for review against this order");
    }

    @Test
    @DisplayName("A rejected prescription cannot be used to place an order")
    void rejectedPrescriptionCannotBeUsed() {
        Prescription p = newPrescription(PrescriptionStatus.REJECTED);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                orderService.checkout(customer, Map.of(rxMedicine.getId(), 1),
                        "12 Test Road", PaymentMethod.CARD, p.getId()));
        assertTrue(ex.getMessage().contains("rejected"));
    }

    // ---------------- delivery ----------------

    @Test
    @DisplayName("Delivery status cannot move backwards")
    void deliveryCannotMoveBackwards() {
        CustomerOrder o = assignedOrder();
        orderService.updateDelivery(o.getId(), courier, DeliveryStatus.PICKED_UP);
        orderService.updateDelivery(o.getId(), courier, DeliveryStatus.OUT_FOR_DELIVERY);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                orderService.updateDelivery(o.getId(), courier, DeliveryStatus.PICKED_UP));
        assertTrue(ex.getMessage().contains("cannot move backwards"));
    }

    @Test
    @DisplayName("A delivery cannot be confirmed with the wrong OTP")
    void wrongOtpIsRejected() {
        CustomerOrder o = assignedOrder();
        orderService.updateDelivery(o.getId(), courier, DeliveryStatus.PICKED_UP);
        orderService.updateDelivery(o.getId(), courier, DeliveryStatus.OUT_FOR_DELIVERY);

        assertThrows(IllegalStateException.class, () ->
                orderService.confirmDelivery(o.getId(), courier, "0000"));
        assertNotEquals(DeliveryStatus.DELIVERED,
                orders.findById(o.getId()).orElseThrow().getDeliveryStatus());
    }

    @Test
    @DisplayName("The correct OTP marks the order delivered")
    void correctOtpCompletesDelivery() {
        CustomerOrder o = assignedOrder();
        orderService.updateDelivery(o.getId(), courier, DeliveryStatus.PICKED_UP);
        orderService.updateDelivery(o.getId(), courier, DeliveryStatus.OUT_FOR_DELIVERY);

        orderService.confirmDelivery(o.getId(), courier, o.getDeliveryOtp());
        CustomerOrder done = orders.findById(o.getId()).orElseThrow();
        assertEquals(DeliveryStatus.DELIVERED, done.getDeliveryStatus());
        assertEquals(OrderStatus.DELIVERED, done.getStatus());
    }

    @Test
    @DisplayName("A courier cannot update an order assigned to someone else")
    void courierCannotTouchAnotherCouriersOrder() {
        CustomerOrder o = assignedOrder();
        User other = newUser("t_other_courier", Role.DELIVERY_STAFF);
        assertThrows(IllegalStateException.class, () ->
                orderService.updateDelivery(o.getId(), other, DeliveryStatus.PICKED_UP));
    }

    // ---------------- helpers ----------------

    private CustomerOrder assignedOrder() {
        CustomerOrder o = orderService.checkout(customer, Map.of(plainMedicine.getId(), 1),
                "12 Test Road", PaymentMethod.CARD, null);
        o.setDeliveryStaff(courier);
        o.setDeliveryStatus(DeliveryStatus.ASSIGNED);
        return orders.save(o);
    }

    private User newUser(String username, Role role) {
        User u = new User();
        u.setUsername(username);
        u.setPassword(encoder.encode("Password@123"));
        u.setFullName("Test " + role);
        u.setEmail(username + "@test.lk");
        u.setPhone("0770000000");
        u.setAddress("12 Test Road");
        u.setRole(role);
        u.setStatus(UserStatus.ACTIVE);
        return users.save(u);
    }

    private Medicine newMedicine(String name, int stock, boolean rx) {
        Medicine m = new Medicine();
        m.setName(name);
        m.setGenericName(name);
        m.setCategory("Test");
        m.setManufacturer("Test Labs");
        m.setDosageForm("Tablet");
        m.setPrice(new BigDecimal("10.00"));
        m.setStock(stock);
        m.setReorderLevel(5);
        m.setBatchNo("TST-0001");
        m.setExpiryDate(LocalDate.now().plusYears(1));
        m.setPrescriptionRequired(rx);
        return medicines.save(m);
    }

    private Prescription newPrescription(PrescriptionStatus status) {
        Prescription p = new Prescription();
        p.setCustomer(customer);
        p.setDoctorName("Dr Test");
        p.setOriginalName("rx.jpg");
        p.setFileName("test-rx.jpg");
        p.setStatus(status);
        return prescriptions.save(p);
    }
}
