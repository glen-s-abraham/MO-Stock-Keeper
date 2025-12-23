# Deep-Dive Application Analysis Report

**Date:** 2025-12-23
**Persona:** Senior Systems Architect / Financial Auditor

## Executive Summary
This report expands on the initial audit, focusing on subtle business logic flaws that could lead to financial discrepancies, audit failures, and operational deadlocks. While the application handles the "Happy Path" well, its handling of reversals, historical data, and complex inventory states is fragile.

## 1. Security & Permissions (New)
*   **Strengths**:
    *   H2 Console is properly protected.
    *   Admin Controllers (`UserController`, `Settings`) are correctly annotated with `@PreAuthorize("hasRole('ADMIN')")`.
*   **Weaknesses (IDOR Risk)**:
    *   `CollectionsController` and `SalesController` accept `customerId` and `orderId` directly. An authenticated user (e.g., "Packer") could guess IDs to view Competitor Invoices if role separation isn't strict (currently all "Users" can see Sales).
    *   **Recommendation**: Implement strict Data Ownership rules if multi-tenancy or strict role separation (Sales vs. Warehouse) is required later.

## 2. Data Integrity & Cascades (New)
*   **Critical Flaw**: `Customer` entity lacks `CascadeType.ALL` or `OrphanRemoval` on its relationships.
    *   **Scenario**: If an Admin deletes a Customer, their Invoices and Sales Orders remain in the database pointing to a non-existent `customer_id` (if DB constraint doesn't stop it) or throw a generic `DataIntegrityViolationException`.
    *   **Business Impact**: "Ghost" financial records.
    *   **Fix**: Implement "Soft Delete" (`isHidden = true`) for Customers. **Never** hard-delete entities with financial history.

## 3. Financial Integrity Risks (Critical)

### A. The "Debt Shifting" Anomaly (Payment Reversal)
*   **The Flaw**: In `PaymentService.voidPayment()`, the system restores debt to the customer's account by iterating through *any* recent paid invoices and adding the balance back.
*   **The Scenario**: 
    1.  Customer pays **$50 for Invoice A** (Oldest).
    2.  Customer pays **$50 for Invoice B** (Newest) via a separate transaction.
    3.  Admin voids the payment for **Invoice A**.
    4.  **Result**: The logic iterates invoices by date (descending/ascending). It might find Invoice B first and mark it as "Unpaid" to restore the $50 debt, leaving Invoice A as "Paid".
*   **Business Impact**: Customers receive statements showing they owe money for the wrong items. Legal/Compliance risk.
*   **Fix**: `Payment` entity must strictly link to the specific `Invoice`(s) it settled (Many-to-Many or One-to-Many Allocation table).

### B. Loss of Audit Trail (Credit Note Usage)
*   **The Flaw**: `PaymentService.settleAccount()` consumes Credit Notes by modifying their `remainingAmount` **in-place**.
*   **The Scenario**: A $100 Credit Note is used to pay five different $20 invoices over a month.
*   **Result**: At the end of the month, the Credit Note simply shows `remainingAmount: 0`. There is **no database link** proving *which* invoices consumed that credit. Usage is only visible in unstructured text logs (`AuditService`).
*   **Business Impact**: Impossible to generate an accurate "Credit Note History" report or audit specific redemptions.
*   **Fix**: Introduce a `CreditNoteUsage` entity (CreditNoteID, InvoiceID, Amount, Date).

## 4. Inventory Lifecycle & Traceability (Major)

### A. The "One-Way" Return Valve
*   **The Flaw**: `InventoryUnit` items marked as `RETURNED` enter a dead state. They cannot be restocked (if purely a shipping error) or written off (if damaged).
*   **Business Impact**: Inventory counts drift permanently. Warehouse staff cannot "fix" mistakes.
*   **Fix**: Implement `Restock (Return -> Available)` and `Spoil (Return -> Spoiled)` workflows.

### B. Race Conditions (Confirmed Critical)
*   **Allocation**: Two users scanning the same QR code simultaneously -> Double Allocation.
*   **Invoice Numbering**: Two users finalizing sales simultaneously -> `UniqueConstraintViolation` crash.
*   **Fix**: Optimistic Locking (`@Version`) and Database Sequences.

### C. Tax Calculation Rounding
*   **The Flaw**: `ReturnsService` calculates tax refunds using proportional ratios: `(Unit Price / Invoice Total) * Total Tax`.
*   **Risk**: Repeated partial returns of a large invoice may result in the sum of tax refunds varying by +/- 1 cent compared to the original tax paid due to rounding differences.
*   **Fix**: Store `taxAmount` explicitly per `InventoryUnit` (or SalesOrderLine) at the time of sale, rather than deriving it on return.

## Strategic Recommendations

### Phase 1: Integrity & Safety (Immediate)
1.  **Stop "Debt Shifting"**: Modify `PaymentService` to only reverse debt to the *specific invoices* that were originally paid (requires schema change: `Payment_Invoice_Allocation` table).
2.  **Fix Race Conditions**: Apply `@Version` to `InventoryUnit` and `SalesOrder`.
3.  **Prevent Hard Deletes**: Enforce "Soft Delete" for Customers in Service layer.

### Phase 2: Workflow Completion (Short Term)
4.  **Fix Returns**: Add "Triaging" UI for returned items (Restock/Spoil).
5.  **Audit Trail**: Create `CreditNoteUsage` entity to track redemptions explicitly.

### Phase 3: Scalability (Medium Term)
6.  **Refactor Reporting**: Move aggregation logic (sums/counts) to SQL `GROUP BY` queries entirely. Implement Server-Side Pagination for DataTables.
