package com.smartcare.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A product a supplier offers to the pharmacy, with their own price and lead time.
 * Suppliers own these rows outright: they create, edit and delete their own offers,
 * which is the Supplier module's CRUD requirement.
 */
@Entity
@Table(name = "supplier_offer")
@Getter @Setter @NoArgsConstructor
public class SupplierOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The supplier who owns this offer (a User with role SUPPLIER). */
    @ManyToOne(optional = false)
    private User supplier;

    @NotBlank(message = "Product name is required")
    @Size(max = 120)
    private String productName;

    @Size(max = 120)
    private String genericName;

    @Size(max = 120)
    private String manufacturer;

    @NotNull(message = "Unit price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than zero")
    @Digits(integer = 8, fraction = 2)
    private BigDecimal unitPrice;

    @Min(value = 1, message = "Minimum order quantity must be at least 1")
    private int minOrderQty = 1;

    @Min(value = 0, message = "Lead time cannot be negative")
    @Max(value = 365, message = "Lead time looks too large")
    private int leadTimeDays = 7;

    /** Supplier can take an offer off the catalogue without deleting its history. */
    private boolean available = true;

    private LocalDate priceValidUntil;

    @Size(max = 250)
    private String notes;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    /** True when the quoted price has passed its validity date. */
    public boolean isPriceExpired() {
        return priceValidUntil != null && priceValidUntil.isBefore(LocalDate.now());
    }
}
