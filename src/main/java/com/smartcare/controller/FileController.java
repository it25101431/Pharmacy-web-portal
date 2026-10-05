package com.smartcare.controller;

import com.smartcare.config.CurrentUser;
import com.smartcare.model.*;
import com.smartcare.repository.*;
import com.smartcare.service.*;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;


/** Prescription images are private: only the pharmacist role and the owning customer may open them. */
@Controller
@RequiredArgsConstructor
public class FileController {
    private final PrescriptionRepository prescriptions;
    private final MedicineRepository medicines;
    private final FileStorageService files;
    private final CurrentUser current;

    @GetMapping("/files/prescriptions/{id}")
    public ResponseEntity<Resource> prescription(@PathVariable Long id) {
        Prescription p = prescriptions.findById(id).orElseThrow(() -> new IllegalStateException("Prescription not found."));
        User u = current.get();
        boolean allowed = u.getRole() == Role.PHARMACIST
                || (u.getRole() == Role.CUSTOMER && p.getCustomer().getId().equals(u.getId()));
        if (!allowed) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        String name = p.getFileName();
        MediaType type = name.endsWith(".pdf") ? MediaType.APPLICATION_PDF
                : name.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().contentType(type).body(files.load(name));
    }

    /** Medicine product photos are not private: any signed-in user browsing the catalog may view them. */
    @GetMapping("/images/medicines/{id}")
    public ResponseEntity<Resource> medicinePhoto(@PathVariable Long id) {
        Medicine m = medicines.findById(id).orElse(null);
        if (m == null || m.getImageFile() == null) return ResponseEntity.notFound().build();
        String name = m.getImageFile();
        MediaType type = name.endsWith(".png") ? MediaType.IMAGE_PNG
                : name.endsWith(".webp") ? MediaType.parseMediaType("image/webp") : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().contentType(type)
                .header(HttpHeaders.CACHE_CONTROL, "max-age=3600")
                .body(files.load(name));
    }
}
