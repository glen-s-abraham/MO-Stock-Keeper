# Interface Contracts: Manual Label Printer Endpoints

## 1. `GET /batches/manual-labels`

**Purpose**: Displays the Manual Label Printer page where users can:
1. View the current/last sequence counter and the next starting batch code (e.g., `M-0001`).
2. Select a Product and enter the quantity of manual stickers to generate and print a new set.
3. Reprint an existing sequence range (`seqStart` to `seqEnd`) for a Product without advancing the counter.
4. Adjust/reset the remembered sequence counter if needed.

- **Model Attributes**:
  - `products`: `List<Product>` — All products available for label printing
  - `lastSequence`: `int` — Current stored value of `manual_label_last_sequence` (e.g., `0`)
  - `nextSequence`: `int` — `lastSequence + 1` (e.g., `1`)
  - `nextBatchCode`: `String` — Formatted next code (e.g., `"M-0001"`)
- **View**: `batches/manual-labels`

---

## 2. `POST /batches/manual-labels/generate`

**Purpose**: Atomically allocates `quantity` new sequence numbers starting from `manual_label_last_sequence + 1`, persists the updated counter, logs an audit entry, and redirects to the print view.

- **Request Parameters**:
  - `productId` (`Long`, required): Target product ID
  - `quantity` (`Integer`, required): Number of stickers to generate (`1 <= quantity <= 500`)
- **Response**:
  - On success: `302 Redirect` to `/batches/manual-labels/print?productId={productId}&seqStart={startSeq}&seqEnd={endSeq}`
  - On validation error: `302 Redirect` to `/batches/manual-labels` with flash `error` message

---

## 3. `GET /batches/manual-labels/print`

**Purpose**: Renders the print-ready FSSAI label sheet (`batches/print.html`) for the specified product and manual sequence range (`seqStart` to `seqEnd`). Also serves as the target for the "Reprint Sequence Range" form without incrementing the counter.

- **Request Parameters**:
  - `productId` (`Long`, required)
  - `seqStart` (`Integer`, required, `>= 1`)
  - `seqEnd` (`Integer`, required, `>= seqStart`, `seqEnd - seqStart + 1 <= 500`)
- **Model Attributes**:
  - `batch`: Transient `HarvestBatch` (`batchDate = null`, `expiryDate = null`)
  - `units`: Transient `List<InventoryUnit>` with `uuid = M-XXXX`
  - `manualMode`: `true`
  - `datePlaceholder`: `"____________"`
  - `backUrl`: `"/batches/manual-labels"`
  - `serverPrintUrl`: `"/batches/manual-labels/server-print"`
  - Standard print settings & server print status attributes (`companyName`, `contactNumber`, `registeredOfficeAddress`, `customerCareAddress`, `labelSheetSize`, `customLabelWidth`, `customLabelHeight`, `printServerAvailable`, etc.)
- **View**: `batches/print`

---

## 4. `POST /batches/manual-labels/server-print`

**Purpose**: Generates a multi-page PDF for the requested manual label sequence range and dispatches it to the configured LAN print server (`PrintServerService`).

- **Request Parameters**:
  - `productId` (`Long`, required)
  - `seqStart` (`Integer`, required, `>= 1`)
  - `seqEnd` (`Integer`, required, `>= seqStart`, `seqEnd - seqStart + 1 <= 500`)
- **Response**: `application/json` (`ServerPrintResponse`)
  - `200 OK`: `{"success": true, "batchId": 0, "unitCount": N, "pdfByteSize": ..., "message": "..."}`
  - `400 Bad Request` / `404 Not Found` / `503 Service Unavailable` on validation or print server failure

---

## 5. `POST /batches/manual-labels/sequence`

**Purpose**: Allows operators to manually update or reset the remembered `manual_label_last_sequence` counter (e.g., if a print job was cancelled or sequence needs resetting).

- **Request Parameters**:
  - `lastSequence` (`Integer`, required, `>= 0`)
- **Response**:
  - `302 Redirect` to `/batches/manual-labels` with flash `success` or `error` message
