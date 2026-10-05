package com.smartcare.config;

import com.smartcare.model.*;
import com.smartcare.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Seeds demo accounts and medicines on first start (only when tables are empty). */
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {
    private final UserRepository users;
    private final MedicineRepository medicines;
    private final PasswordEncoder encoder;

    @Override
    public void run(String... args) {
        if (users.count() == 0) {
            user("admin", "System Admin", Role.ADMIN);
            user("pharmacist", "Priya Pharmacist", Role.PHARMACIST);
            user("customer", "Chamara Customer", Role.CUSTOMER);
            user("supplier", "MedSupply Lanka", Role.SUPPLIER);
            user("manager", "Nimal Store Manager", Role.STORE_MANAGER);
            user("delivery", "Dinesh Delivery", Role.DELIVERY_STAFF);
        }
        if (medicines.count() == 0) {
            LocalDate far = LocalDate.now().plusMonths(18);
            med("Paracetamol 500mg", "Paracetamol", "Pain Relief", "GSK", "Tablet", "12.50", 200, 50, "PCM-1001", far, false);
            med("Ibuprofen 400mg", "Ibuprofen", "Pain Relief", "Cipla", "Tablet", "18.00", 120, 40, "IBU-2210", far, false);
            med("Cetirizine 10mg", "Cetirizine", "Allergy", "Sun Pharma", "Tablet", "9.75", 90, 30, "CET-3302", far, false);
            med("ORS Sachet", "Oral Rehydration Salts", "Digestive", "Hemas", "Powder", "35.00", 60, 25, "ORS-0044", far, false);
            med("Vitamin C 500mg", "Ascorbic Acid", "Vitamins", "Nature's Best", "Tablet", "22.00", 75, 20, "VTC-5510", LocalDate.now().plusDays(15), false);
            med("Amoxicillin 500mg", "Amoxicillin", "Antibiotics", "Aurobindo", "Capsule", "45.00", 8, 20, "AMX-7781", far, true);
            med("Metformin 500mg", "Metformin", "Diabetes", "Lupin", "Tablet", "16.00", 150, 40, "MET-6620", far, true);
            med("Atorvastatin 20mg", "Atorvastatin", "Cardiac", "Pfizer", "Tablet", "38.00", 100, 30, "ATV-9012", far, true);
            med("Salbutamol Inhaler", "Salbutamol", "Respiratory", "Cipla", "Inhaler", "480.00", 25, 10, "SAL-1188", far, true);
            med("Old Cough Syrup", "Dextromethorphan", "Cough & Cold", "Hemas", "Syrup", "210.00", 30, 10, "CGH-0007", LocalDate.now().minusDays(10), false);
        }
    }

    private void user(String username, String fullName, Role role) {
        User u = new User();
        u.setUsername(username);
        u.setPassword(encoder.encode("Password@123"));
        u.setFullName(fullName);
        u.setEmail(username + "@smartcare.lk");
        u.setPhone("0771234567");
        u.setAddress("No. 12, Galle Road, Colombo 03");
        u.setRole(role);
        u.setStatus(UserStatus.ACTIVE);
        users.save(u);
    }

    private void med(String name, String generic, String cat, String mfr, String form, String price,
                     int stock, int reorder, String batch, LocalDate expiry, boolean rx) {
        Medicine m = new Medicine();
        m.setName(name); m.setGenericName(generic); m.setCategory(cat); m.setManufacturer(mfr);
        m.setDosageForm(form); m.setPrice(new BigDecimal(price)); m.setStock(stock); m.setReorderLevel(reorder);
        m.setBatchNo(batch); m.setExpiryDate(expiry); m.setPrescriptionRequired(rx);
        medicines.save(m);
    }
}
