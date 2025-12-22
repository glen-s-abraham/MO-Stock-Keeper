package com.mushroom.stockkeeper.dto;

import java.math.BigDecimal;

public class CustomerInvoiceSummary {
    private Long customerId;
    private String customerName;
    private BigDecimal totalBalanceDue;
    private long unpaidCount;
    private long partialCount;
    private long paidCount;
    private BigDecimal totalCreditAmount;

    public CustomerInvoiceSummary(Long customerId, String customerName, BigDecimal totalBalanceDue, long unpaidCount,
            long partialCount, long paidCount, BigDecimal totalCreditAmount) {
        this.customerId = customerId;
        this.customerName = customerName;
        this.totalBalanceDue = totalBalanceDue;
        this.unpaidCount = unpaidCount;
        this.partialCount = partialCount;
        this.paidCount = paidCount;
        this.totalCreditAmount = totalCreditAmount;
    }

    // Getters and Setters
    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public BigDecimal getTotalBalanceDue() {
        return totalBalanceDue;
    }

    public void setTotalBalanceDue(BigDecimal totalBalanceDue) {
        this.totalBalanceDue = totalBalanceDue;
    }

    public BigDecimal getTotalCreditAmount() {
        return totalCreditAmount;
    }

    public void setTotalCreditAmount(BigDecimal totalCreditAmount) {
        this.totalCreditAmount = totalCreditAmount;
    }

    public long getUnpaidCount() {
        return unpaidCount;
    }

    public void setUnpaidCount(long unpaidCount) {
        this.unpaidCount = unpaidCount;
    }

    public long getPartialCount() {
        return partialCount;
    }

    public void setPartialCount(long partialCount) {
        this.partialCount = partialCount;
    }

    public long getPaidCount() {
        return paidCount;
    }

    public void setPaidCount(long paidCount) {
        this.paidCount = paidCount;
    }
}
