package com.smartcare;

import com.smartcare.model.*;
import com.smartcare.repository.*;
import com.smartcare.service.OrderService;
import com.smartcare.service.UserDeletionService;
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
 * The delete rules matter most when they say NO: an account with history must
 * never be removed. These tests run against the in-memory H2 database and are
 * rolled back afterwards.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserDeletionServiceTest {

    @Autowired UserDeletionService deletion;
    @Autowired OrderService orderService;
    @Autowired UserRepository users;
    @Autowired MedicineRepository medicines;
    @Autowired PasswordEncoder encoder;

    private User admin;

    @BeforeEach
    void setUp() {
        admin = newUser("t_admin", Role.ADMIN);
    }

    @Test
    @DisplayName("An account with no history can be deleted")
    void deletesUnusedAccount() {
        User unused = newUser("t_unused", Role.CUSTOMER);
        deletion.delete(unused.getId(), admin);
        assertFalse(users.existsById(unused.getId()));
    }

    @Test
    @DisplayName("A customer who has placed an order cannot be deleted")
    void refusesCustomerWithOrders() {
        User customer = newUser("t_buyer", Role.CUSTOMER);
        Medicine m = newMedicine();
        orderService.checkout(customer, Map.of(m.getId(), 1), "12 Test Road", PaymentMethod.CARD, null);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> deletion.delete(customer.getId(), admin));
        assertTrue(ex.getMessage().contains("placed orders"));
        assertTrue(users.existsById(customer.getId()), "the account and its history must survive");
    }

    @Test
    @DisplayName("An admin cannot delete their own account")
    void refusesSelfDelete() {
        newUser("t_admin2", Role.ADMIN); // so the last-admin rule is not what triggers
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> deletion.delete(admin.getId(), admin));
        assertTrue(ex.getMessage().contains("your own account"));
    }

    @Test
    @DisplayName("Another admin can be deleted when several admins exist")
    void allowsDeletingOneOfSeveralAdmins() {
        // The seed data also creates an 'admin' account, so count what really exists.
        long admins = users.countByRole(Role.ADMIN);
        User other = newUser("t_other_admin", Role.ADMIN);
        assertEquals(admins + 1, users.countByRole(Role.ADMIN));
        // With more than one admin, deleting another admin is allowed...
        assertTrue(deletion.canDelete(other, admin));
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

    private Medicine newMedicine() {
        Medicine m = new Medicine();
        m.setName("Test Medicine");
        m.setGenericName("Test");
        m.setCategory("Test");
        m.setManufacturer("Test Labs");
        m.setDosageForm("Tablet");
        m.setPrice(new BigDecimal("10.00"));
        m.setStock(20);
        m.setReorderLevel(5);
        m.setBatchNo("TST-0002");
        m.setExpiryDate(LocalDate.now().plusYears(1));
        m.setPrescriptionRequired(false);
        return medicines.save(m);
    }
}
