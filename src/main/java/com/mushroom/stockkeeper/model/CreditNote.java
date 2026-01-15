package com.mushroom.stockkeeper.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "sk_credit_notes")
@Data
public class CreditNote {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String noteNumber;

    @ManyToOne
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne
    @JoinColumn(name = "invoice_id")
    private Invoice originalInvoice; // Optional link to original invoice

    @ManyToOne
    @JoinColumn(name = "payment_id")
    private Payment generatedFromPayment; // Link to source payment (e.g. overpayment)

    @Column(nullable = false)
    private BigDecimal amount;

    // Tax portion of the credit (for reporting)
    private BigDecimal taxAmount;

    private String reason;

    @Column(nullable = false)
    private LocalDate noteDate;

    private boolean isUsed = false; // Deprecated in favor of remainingAmount > 0 logic, but kept for compat

    private BigDecimal remainingAmount;

    @Version
    private Long version;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (noteDate == null)
            noteDate = LocalDate.now();
        if (remainingAmount == null)
            remainingAmount = amount;
    }
}
