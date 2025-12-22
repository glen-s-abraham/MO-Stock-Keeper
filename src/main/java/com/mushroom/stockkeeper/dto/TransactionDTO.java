package com.mushroom.stockkeeper.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class TransactionDTO {
    private LocalDate date;
    private String type; // "INVOICE", "PAYMENT", "CREDIT_NOTE", "REFUND"
    private String reference;
    private String description;

    private BigDecimal debit; // Increases Balance (Invoice)
    private BigDecimal credit; // Decreases Balance (Payment/CN)
    private BigDecimal balance; // Running Balance

    // For Payment Actions
    private Long paymentId;
    private boolean reversed;

    public TransactionDTO(LocalDate date, String type, String reference, String description, BigDecimal debit,
            BigDecimal credit) {
        this.date = date;
        this.type = type;
        this.reference = reference;
        this.description = description;
        this.debit = debit != null ? debit : BigDecimal.ZERO;
        this.credit = credit != null ? credit : BigDecimal.ZERO;
    }

    // Constructor with ID for Payments
    public TransactionDTO(LocalDate date, String type, String reference, String description, BigDecimal debit,
            BigDecimal credit, Long paymentId, boolean reversed) {
        this(date, type, reference, description, debit, credit);
        this.paymentId = paymentId;
        this.reversed = reversed;
    }
}
