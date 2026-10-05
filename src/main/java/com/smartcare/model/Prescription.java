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
public class Prescription {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private User customer;
    private String doctorName;
    private String fileName;
    private String originalName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private PrescriptionStatus status = PrescriptionStatus.PENDING;
    private String pharmacistNote;
    private LocalDateTime uploadedAt = LocalDateTime.now();
    @ManyToOne
    private User verifiedBy;
}
