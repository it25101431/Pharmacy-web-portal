package com.smartcare.controller;

import com.smartcare.config.CurrentUser;
import com.smartcare.model.*;
import com.smartcare.repository.*;
import com.smartcare.service.*;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;


import jakarta.validation.Valid;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequestMapping("/pharmacist")
@RequiredArgsConstructor
public class PharmacistController {
    private final MedicineRepository medicines;
    private final PrescriptionRepository prescriptions;
    private final OrderRepository orders;
    private final OrderService orderService;
    private final FileStorageService files;
    private final CurrentUser current;

    // ---------- Medicine CRUD ----------
    @GetMapping("/medicines")
    public String list(@RequestParam(defaultValue = "") String q, Model m) {
        m.addAttribute("meds", medicines.search(q.trim(), ""));
        m.addAttribute("q", q);
        return "pharmacist/medicines";
    }

    @GetMapping("/medicines/new")
    public String newForm(Model m) {
        m.addAttribute("medicine", new Medicine());
        return "pharmacist/medicine-form";
    }

    @GetMapping("/medicines/{id}/edit")
    public String editForm(@PathVariable Long id, Model m) {
        m.addAttribute("medicine", medicines.findById(id).orElseThrow(() -> new IllegalStateException("Medicine not found.")));
        return "pharmacist/medicine-form";
    }

    @PostMapping("/medicines")
    public String create(@Valid @ModelAttribute("medicine") Medicine form, BindingResult br,
                         @RequestParam(value = "photo", required = false) MultipartFile photo, RedirectAttributes ra) {
        if (form.getExpiryDate() != null && form.getExpiryDate().isBefore(LocalDate.now()))
            br.rejectValue("expiryDate", "expired", "Expiry date cannot be in the past");
        if (br.hasErrors()) return "pharmacist/medicine-form";
        Medicine m = new Medicine();
        copy(m, form);
        if (photo != null && !photo.isEmpty()) m.setImageFile(files.storeImage(photo));
        medicines.save(m);
        ra.addFlashAttribute("msg", m.getName() + " added to the catalog.");
        return "redirect:/pharmacist/medicines";
    }

    @PostMapping("/medicines/{id}")
    public String update(@PathVariable Long id, @Valid @ModelAttribute("medicine") Medicine form, BindingResult br,
                         @RequestParam(value = "photo", required = false) MultipartFile photo,
                         @RequestParam(defaultValue = "false") boolean removePhoto, RedirectAttributes ra) {
        Medicine m = medicines.findById(id).orElseThrow(() -> new IllegalStateException("Medicine not found."));
        form.setId(id);
        if (br.hasErrors()) {
            form.setStock(m.getStock()); // re-show the real stock, not the form's default
            form.setImageFile(m.getImageFile());
            return "pharmacist/medicine-form";
        }
        // Editing must NOT touch stock: customers may have bought units while this form was open,
        // so copying the form's stock would silently undo those sales. Stock only changes via
        // "Receive stock", checkout, or supplier delivery.
        int currentStock = m.getStock();
        copy(m, form);
        m.setStock(currentStock);
        if (photo != null && !photo.isEmpty()) m.setImageFile(files.storeImage(photo));
        else if (removePhoto) m.setImageFile(null);
        medicines.save(m);
        ra.addFlashAttribute("msg", m.getName() + " updated.");
        return "redirect:/pharmacist/medicines";
    }

    /** Soft delete: order history keeps referencing the medicine. */
    @PostMapping("/medicines/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        Medicine m = medicines.findById(id).orElseThrow(() -> new IllegalStateException("Medicine not found."));
        m.setActive(false);
        medicines.save(m);
        ra.addFlashAttribute("msg", m.getName() + " removed from the catalog.");
        return "redirect:/pharmacist/medicines";
    }

    @PostMapping("/medicines/{id}/stock")
    public String receiveStock(@PathVariable Long id, @RequestParam int qty, @RequestParam(defaultValue = "") String batchNo,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate,
                               RedirectAttributes ra) {
        if (qty <= 0) throw new IllegalStateException("Received quantity must be greater than zero.");
        Medicine m = medicines.findById(id).orElseThrow(() -> new IllegalStateException("Medicine not found."));
        if (expiryDate != null && expiryDate.isBefore(LocalDate.now())) throw new IllegalStateException("Expiry date cannot be in the past.");
        m.setStock(m.getStock() + qty);
        if (!batchNo.isBlank()) m.setBatchNo(batchNo.trim());
        if (expiryDate != null) m.setExpiryDate(expiryDate);
        medicines.save(m);
        ra.addFlashAttribute("msg", "Stock updated: " + m.getName() + " now has " + m.getStock() + " units.");
        return "redirect:/pharmacist/medicines";
    }

    private void copy(Medicine t, Medicine s) {
        t.setName(s.getName().trim()); t.setGenericName(s.getGenericName()); t.setCategory(s.getCategory().trim());
        t.setManufacturer(s.getManufacturer()); t.setDosageForm(s.getDosageForm()); t.setPrice(s.getPrice());
        t.setStock(s.getStock()); t.setReorderLevel(s.getReorderLevel()); t.setBatchNo(s.getBatchNo());
        t.setExpiryDate(s.getExpiryDate()); t.setPrescriptionRequired(s.isPrescriptionRequired());
    }

    // ---------- Prescriptions ----------
    @GetMapping("/prescriptions")
    public String prescriptionQueue(Model m) {
        // A pharmacist cannot judge a prescription without seeing what it is being
        // used to buy, so each pending item is paired with the order that is
        // waiting on it, including the medicines and quantities requested.
        List<PrescriptionReview> pending = prescriptions
                .findByStatusOrderByUploadedAtAsc(PrescriptionStatus.PENDING).stream()
                .map(p -> new PrescriptionReview(p, orders.findByPrescription(p).stream().findFirst().orElse(null)))
                .toList();
        m.addAttribute("pending", pending);
        m.addAttribute("history", prescriptions.findTop20ByStatusNotOrderByUploadedAtDesc(PrescriptionStatus.PENDING));
        return "pharmacist/prescriptions";
    }

    /**
     * One row of the review queue: the uploaded prescription together with the
     * order it belongs to. The order may be null if the customer uploaded the
     * document before placing an order.
     */
    @Getter
    @AllArgsConstructor
    public static class PrescriptionReview {
        private final Prescription prescription;
        private final CustomerOrder order;
    }

    @PostMapping("/prescriptions/{id}/decision")
    public String decide(@PathVariable Long id, @RequestParam boolean approve, @RequestParam(defaultValue = "") String note, RedirectAttributes ra) {
        orderService.decidePrescription(id, approve, note, current.get());
        ra.addFlashAttribute("msg", "Prescription #" + id + (approve ? " approved." : " rejected."));
        return "redirect:/pharmacist/prescriptions";
    }

    // ---------- Orders to pack ----------
    @GetMapping("/orders")
    public String toPack(Model m) {
        m.addAttribute("waiting", orders.findByStatusOrderByCreatedAtAsc(OrderStatus.PLACED));
        m.addAttribute("verified", orders.findByStatusOrderByCreatedAtAsc(OrderStatus.VERIFIED));
        return "pharmacist/orders";
    }

    @PostMapping("/orders/{id}/pack")
    public String pack(@PathVariable Long id, RedirectAttributes ra) {
        orderService.pack(id);
        ra.addFlashAttribute("msg", "Order #" + id + " marked as packed.");
        return "redirect:/pharmacist/orders";
    }

    // ---------- Alerts ----------
    @GetMapping("/alerts")
    public String alerts(Model m) {
        m.addAttribute("expiring", medicines.findByActiveTrueAndExpiryDateBeforeOrderByExpiryDateAsc(LocalDate.now().plusDays(31)));
        m.addAttribute("lowStock", medicines.lowStock());
        return "pharmacist/alerts";
    }
}
