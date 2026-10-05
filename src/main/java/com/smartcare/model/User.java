package com.smartcare.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;


@Entity @Table(name = "users")
@Getter @Setter @NoArgsConstructor
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 50)
    private String username;
    @Column(nullable = false)
    private String password;
    @Column(nullable = false, length = 100)
    private String fullName;
    @Column(length = 100)
    private String email;
    @Column(length = 20)
    private String phone;
    private String address;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Role role;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.PENDING;
    private LocalDateTime createdAt = LocalDateTime.now();
}
