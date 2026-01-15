package com.mushroom.stockkeeper.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "sk_invoices")
@Data
public class Invoice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String invoiceNumber;

    @OneToOne
    @JoinColumn(name = "sales_order_id", nullable = false)
    private SalesOrder salesOrder;

    @ManyToOne
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer; // Denormalized for query perf

    @OneToMany(mappedBy = "originalInvoice", fetch = FetchType.LAZY)
    private java.util.List<CreditNote> creditNotes;

    @Column(nullable = false)
    private LocalDate invoiceDate;

    private BigDecimal totalAmount; // Gross Amount
    private BigDecimal taxAmount;
    private BigDecimal netAmount;
    private BigDecimal amountPaid;
    private BigDecimal balanceDue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status;

    @Version
    private Long version;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (amountPaid == null)
            amountPaid = BigDecimal.ZERO;
        if (balanceDue == null)
            balanceDue = totalAmount;
        if (taxAmount == null)
            taxAmount = BigDecimal.ZERO;
        if (netAmount == null)
            netAmount = totalAmount;
        if (status == null)
            status = InvoiceStatus.UNPAID;
    }
}
