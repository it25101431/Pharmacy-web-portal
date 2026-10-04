package com.smartcare.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;


@Entity @Table(name = "purchase_orders")
@Getter @Setter @NoArgsConstructor
public class PurchaseOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private User supplier;
    @ManyToOne(optional = false)
    private Medicine medicine;
    private int quantity;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private PoStatus status = PoStatus.PENDING;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private SupplierPayment paymentStatus = SupplierPayment.PENDING;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDate dueDate;
    private LocalDateTime deliveredAt;

    public boolean isOnTime() {
        return status == PoStatus.DELIVERED && deliveredAt != null && dueDate != null
                && !deliveredAt.toLocalDate().isAfter(dueDate);
    }
}
