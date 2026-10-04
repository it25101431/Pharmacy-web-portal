package com.smartcare.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;


@Entity
@Getter @Setter @NoArgsConstructor
public class Medicine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version
    private Long version;

    @NotBlank(message = "Name is required") @Size(max = 100)
    @Column(length = 100)
    private String name;
    @Size(max = 100) @Column(length = 100)
    private String genericName;
    @NotBlank(message = "Category is required")
    private String category;
    private String manufacturer;
    private String dosageForm;
    @NotNull(message = "Price is required") @DecimalMin(value = "0.01", message = "Price must be positive")
    @Column(precision = 10, scale = 2)
    private BigDecimal price;
    @Min(value = 0, message = "Stock cannot be negative")
    private int stock;
    @Min(value = 0, message = "Reorder level cannot be negative")
    private int reorderLevel = 10;
    private String batchNo;
    @NotNull(message = "Expiry date is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate expiryDate;
    private boolean prescriptionRequired;
    private boolean active = true;
    /** Stored file name of an optional product photo uploaded by the pharmacist (null = use illustration). */
    private String imageFile;

    public boolean isLowStock() { return stock <= reorderLevel; }
    public boolean isExpired() { return expiryDate != null && expiryDate.isBefore(LocalDate.now()); }
    public boolean isNearExpiry() { return expiryDate != null && !isExpired() && expiryDate.isBefore(LocalDate.now().plusDays(31)); }

    /** Illustration shown when there is no photo, chosen from the dosage form. */
    public String getArtwork() {
        String f = dosageForm == null ? "" : dosageForm.toLowerCase();
        if (f.contains("tab")) return "tablet";
        if (f.contains("cap")) return "capsule";
        if (f.contains("syrup") || f.contains("liquid") || f.contains("suspension")) return "syrup";
        if (f.contains("inhal")) return "inhaler";
        if (f.contains("powder") || f.contains("sachet")) return "powder";
        return "box";
    }

    /** A stable background tint per category (tone-0 .. tone-4) so the shop shelf is easy to scan. */
    public String getTone() {
        return "tone-" + Math.floorMod(category == null ? 0 : category.hashCode(), 5);
    }
}
