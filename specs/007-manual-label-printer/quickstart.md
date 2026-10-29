# Quickstart & Validation Guide: Manual Label Printer

## Prerequisites

- Java 21 LTS and Maven (`./mvnw` or `mvn`)
- Application running locally (`mvn spring-boot:run`) or tested via `mvn test`

## Automated Test Validation

Run the test suite for manual label printing and PDF generation:

```bash
mvn test -Dtest=ManualLabelPrintControllerTest,PrintServerServiceTest
```

## Manual End-to-End Validation Steps

1. **Navigate to Manual Label Printer**:
   - Log in to the application.
   - Click **Manual Labels** in the top navigation bar or on the **Batches** page (`/batches/manual-labels`).
   - Verify the page displays **Next Starting Batch Code: `M-0001`** (Last Sequence: `0`).

2. **Generate First Set of Manual Stickers**:
   - Select any Product (e.g., *Oyster Mushroom 200g*) and enter Quantity = `10`.
   - Click **Generate & Print Manual Labels**.
   - Verify you are redirected to `/batches/manual-labels/print?productId=...&seqStart=1&seqEnd=10`.
   - Verify 10 stickers are rendered on the page with:
     - `Batch No:` incrementing from `M-0001` to `M-0010`
     - `Pkd: ____________` and `Exp: ____________` write-in placeholders
     - All product details, FSSAI license, 10mm veg placeholder, and conditional nutrition table intact.

3. **Verify Sequence Persistence**:
   - Click **Back** to return to `/batches/manual-labels`.
   - Verify the page now shows **Last Used Sequence: `10`** and **Next Starting Batch Code: `M-0011`**.
   - Generate another set of `5` stickers and verify they start at `M-0011` and end at `M-0015`.

4. **Verify Reprint Range Without Incrementing Counter**:
   - On `/batches/manual-labels`, use the **Reprint Sequence Range** card to reprint `seqStart = 3` to `seqEnd = 6`.
   - Verify stickers `M-0003` through `M-0006` are rendered on the print view.
   - Return to `/batches/manual-labels` and confirm **Last Used Sequence** is still `15` (next code `M-0016`).

5. **Verify Live Inventory Unchanged**:
   - Navigate to `/batches` and `/dashboard` and confirm no `HarvestBatch` or `InventoryUnit` records were created by the manual label print runs.
