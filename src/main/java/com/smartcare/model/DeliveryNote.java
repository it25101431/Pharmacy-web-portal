package com.smartcare.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A courier's own log entry against an order they are delivering: attempted visits,
 * customer instructions, access notes. Delivery staff create, view, edit and delete
 * their own notes, which is the Delivery module's CRUD requirement.
 */
@Entity
@Table(name = "delivery_note")
@Getter @Setter @NoArgsConstructor
public class DeliveryNote {

    public enum NoteType { ATTEMPT, INSTRUCTION, DELAY, OTHER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The order this note is about. */
    @ManyToOne(optional = false)
    private CustomerOrder order;

    /** The courier who wrote it (a User with role DELIVERY_STAFF). */
    @ManyToOne(optional = false)
    private User staff;

    @Enumerated(EnumType.STRING)
    @NotNull(message = "Please choose a note type")
    private NoteType type = NoteType.ATTEMPT;

    @NotBlank(message = "Please write the note")
    @Size(max = 250, message = "Keep the note under 250 characters")
    private String note;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public String getTypeLabel() {
        switch (type) {
            case ATTEMPT:     return "Delivery attempt";
            case INSTRUCTION: return "Customer instruction";
            case DELAY:       return "Delay";
            default:          return "Other";
        }
    }
}
