package com.smartcare.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;


@Entity @Table(name = "orders")
@Getter @Setter @NoArgsConstructor
public class CustomerOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private User customer;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PLACED;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private PaymentMethod paymentMethod;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;
    @Column(precision = 12, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;
    private String deliveryAddress;
    @ManyToOne
    private Prescription prescription;
    private LocalDateTime createdAt = LocalDateTime.now();
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();
    @ManyToOne
    private User deliveryStaff;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private DeliveryStatus deliveryStatus = DeliveryStatus.NOT_ASSIGNED;
    private String deliveryOtp;
    private String deliveryIssue;
    private LocalDateTime deliveredAt;
}
