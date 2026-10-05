package com.smartcare.controller;

import com.smartcare.config.CurrentUser;
import com.smartcare.model.*;
import com.smartcare.repository.*;
import com.smartcare.service.*;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;


@Controller
@RequestMapping("/customer")
@RequiredArgsConstructor
public class CustomerController {
    private final MedicineRepository medicines;
    private final CartService cart;
    private final OrderService orderService;
    private final OrderRepository orders;
    private final PrescriptionRepository prescriptions;
    private final FileStorageService files;
    private final NotificationService notes;
    private final CurrentUser current;

    @Getter @AllArgsConstructor
    public static class Line {
        private final Medicine medicine;
        private final int quantity;
        public BigDecimal getSubtotal() { return medicine.getPrice().multiply(BigDecimal.valueOf(quantity)); }
    }

    @GetMapping("/medicines")
    public String catalog(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "") String category, Model m) {
        m.addAttribute("meds", medicines.search(q.trim(), category));
        m.addAttribute("categories", medicines.categories());
        m.addAttribute("q", q);
        m.addAttribute("category", category);
        m.addAttribute("cartCount", cart.count());
        return "customer/medicines";
    }

    @PostMapping("/cart/add")
    public String add(@RequestParam Long id, @RequestParam(defaultValue = "1") int qty, RedirectAttributes ra) {
        Medicine m = medicines.findById(id).filter(Medicine::isActive)
                .orElseThrow(() -> new IllegalStateException("Medicine not found."));
        if (qty < 1) qty = 1;
        if (m.isExpired()) throw new IllegalStateException(m.getName() + " has expired.");
        if (cart.getItems().getOrDefault(id, 0) + qty > m.getStock())
            throw new IllegalStateException("Only " + m.getStock() + " units of " + m.getName() + " are available.");
        cart.add(id, qty);
        ra.addFlashAttribute("msg", m.getName() + " added to your cart.");
        return "redirect:/customer/medicines";
    }

    @GetMapping("/cart")
    public String cartPage(Model m) {
        List<Line> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        boolean rx = false;
        for (Map.Entry<Long, Integer> e : cart.getItems().entrySet()) {
            Medicine med = medicines.findById(e.getKey()).orElse(null);
            if (med == null) continue;
            Line l = new Line(med, e.getValue());
            lines.add(l);
            total = total.add(l.getSubtotal());
            rx |= med.isPrescriptionRequired();
        }
        User u = current.get();
        m.addAttribute("lines", lines);
        m.addAttribute("total", total);
        m.addAttribute("needsRx", rx);
        m.addAttribute("prescriptions", prescriptions.findByCustomerOrderByUploadedAtDesc(u).stream()
                .filter(p -> p.getStatus() != PrescriptionStatus.REJECTED).collect(Collectors.toList()));
        m.addAttribute("methods", PaymentMethod.values());
        m.addAttribute("address", u.getAddress());
        return "customer/cart";
    }

    @PostMapping("/cart/update")
    public String update(@RequestParam Long id, @RequestParam int qty) {
        Medicine m = medicines.findById(id).orElseThrow(() -> new IllegalStateException("Medicine not found."));
        if (qty > m.getStock()) throw new IllegalStateException("Only " + m.getStock() + " units of " + m.getName() + " are available.");
        cart.set(id, qty);
        return "redirect:/customer/cart";
    }

    @PostMapping("/checkout")
    public String checkout(@RequestParam String address, @RequestParam PaymentMethod paymentMethod,
                           @RequestParam(required = false) Long prescriptionId, RedirectAttributes ra) {
        CustomerOrder o = orderService.checkout(current.get(), new LinkedHashMap<>(cart.getItems()), address, paymentMethod, prescriptionId);
        cart.clear();
        ra.addFlashAttribute("msg", "Order #" + o.getId() + " placed successfully.");
        return "redirect:/customer/orders/" + o.getId();
    }

    @GetMapping("/prescriptions")
    public String prescriptionsPage(Model m) {
        m.addAttribute("items", prescriptions.findByCustomerOrderByUploadedAtDesc(current.get()));
        return "customer/prescriptions";
    }

    @PostMapping("/prescriptions")
    public String upload(@RequestParam("file") MultipartFile file, @RequestParam(defaultValue = "") String doctorName, RedirectAttributes ra) {
        Prescription p = new Prescription();
        p.setCustomer(current.get());
        p.setDoctorName(cleanDoctorName(doctorName));
        String original = file.getOriginalFilename() == null ? "prescription" : file.getOriginalFilename();
        p.setOriginalName(original.length() > 200 ? original.substring(0, 200) : original); // DB column is VARCHAR(255)
        p.setFileName(files.store(file));
        prescriptions.save(p);
        notes.sendToRole(Role.PHARMACIST, "New prescription #" + p.getId() + " is waiting for verification.");
        ra.addFlashAttribute("msg", "Prescription uploaded. A pharmacist will review it shortly.");
        return "redirect:/customer/prescriptions";
    }

    /**
     * Withdraw an uploaded prescription. Only one that is still PENDING and not yet
     * attached to an order may be removed: once a pharmacist has ruled on it, or an
     * order depends on it, the record is part of the dispensing audit trail and must
     * be kept. The stored file is deleted with it so the image does not linger on disk.
     */
    @PostMapping("/prescriptions/{id}/delete")
    public String deletePrescription(@PathVariable Long id, RedirectAttributes ra) {
        Prescription p = prescriptions.findById(id)
                .filter(x -> x.getCustomer().getId().equals(current.get().getId()))
                .orElseThrow(() -> new IllegalStateException("Prescription not found."));
        if (p.getStatus() != PrescriptionStatus.PENDING)
            throw new IllegalStateException("A prescription a pharmacist has already reviewed cannot be withdrawn.");
        if (orders.existsByPrescription(p))
            throw new IllegalStateException("This prescription is attached to an order and cannot be withdrawn.");
        files.delete(p.getFileName());
        prescriptions.delete(p);
        ra.addFlashAttribute("msg", "Prescription withdrawn.");
        return "redirect:/customer/prescriptions";
    }

    /**
     * UPDATE - correct a prescription that is still awaiting review: fix the
     * doctor's name, or replace a photo that turned out to be blurred. Only a
     * PENDING prescription may be changed, for the same reason it may be
     * withdrawn: once a pharmacist has ruled on it, it is dispensing evidence.
     * Replacing the file deletes the old one so it does not linger on disk.
     */
    @PostMapping("/prescriptions/{id}")
    public String updatePrescription(@PathVariable Long id,
                                     @RequestParam(defaultValue = "") String doctorName,
                                     @RequestParam(value = "file", required = false) MultipartFile file,
                                     RedirectAttributes ra) {
        Prescription p = prescriptions.findById(id)
                .filter(x -> x.getCustomer().getId().equals(current.get().getId()))
                .orElseThrow(() -> new IllegalStateException("Prescription not found."));
        if (p.getStatus() != PrescriptionStatus.PENDING)
            throw new IllegalStateException("A prescription a pharmacist has already reviewed cannot be changed.");

        p.setDoctorName(cleanDoctorName(doctorName));

        // Order matters here. The old file is deleted only AFTER the database
        // update has succeeded. Deleting it first meant that if the save failed,
        // the customer's original document was gone while the row still pointed
        // at it.
        String oldFile = null;
        String newFile = null;
        if (file != null && !file.isEmpty()) {
            oldFile = p.getFileName();
            newFile = files.store(file);            // 1. write the new file
            String original = file.getOriginalFilename() == null ? "prescription" : file.getOriginalFilename();
            p.setFileName(newFile);
            p.setOriginalName(original.length() > 200 ? original.substring(0, 200) : original);
        }
        try {
            prescriptions.save(p);                  // 2. commit the database change
        } catch (RuntimeException e) {
            // The save failed: the row still points at the old file, which is
            // untouched. Remove the new file so it is not left orphaned on disk.
            if (newFile != null) files.delete(newFile);
            throw e;
        }
        if (oldFile != null) files.delete(oldFile); // 3. only now remove the old one
        ra.addFlashAttribute("msg", "Prescription updated.");
        return "redirect:/customer/prescriptions";
    }

    /** Longest doctor's name accepted; the form field enforces the same limit. */
    private static final int MAX_DOCTOR_NAME = 100;

    /**
     * Trims a doctor's name and rejects one that is too long. Used by both upload
     * and edit, so the two paths cannot disagree. Rejecting is deliberate: the old
     * upload path silently cut the name off, which could store a different name
     * from the one the customer typed without telling them.
     */
    private String cleanDoctorName(String doctorName) {
        String name = doctorName == null ? "" : doctorName.trim();
        if (name.length() > MAX_DOCTOR_NAME)
            throw new IllegalStateException("Doctor's name must be " + MAX_DOCTOR_NAME + " characters or fewer.");
        return name;
    }

    @GetMapping("/orders")
    public String myOrders(Model m) {
        m.addAttribute("items", orders.findByCustomerOrderByCreatedAtDesc(current.get()));
        return "customer/orders";
    }

    @GetMapping("/orders/{id}")
    public String detail(@PathVariable Long id, Model m) {
        User u = current.get();
        CustomerOrder o = orders.findById(id).filter(x -> x.getCustomer().getId().equals(u.getId()))
                .orElseThrow(() -> new IllegalStateException("Order not found."));
        m.addAttribute("o", o);
        return "customer/order-detail";
    }
}
