# Feature Specification: Manual Label Printer

**Feature Branch**: `[007-manual-label-printer]`

**Created**: 2026-10-29

**Status**: Draft

**Input**: User description: "so i came across an issue with the label printing lateley when the power was out for a while since stock entry andt label printing is online i couldn't take prinouts. so i am planning to add a new section to the application a manual label printer section where you can enter the number of manual sticker required which i can pre print and keep in case of such emergencies. the layout and everything is same as the online sticker itself the changes is use a new sequence which is remebered and the new set whill start from that which is to be used as the batch code. and a place holder to manually write the pkd and expiry dates so the rest remains the same."

## Clarifications

### Session 2026-10-29

- Q: How should the remembered sequence number increment when printing a set of manual stickers? → A: Each individual sticker gets its own sequential code (e.g., printing 10 stickers generates `M-0001` to `M-0010`; next print starts at `M-0011`).
- Q: What format should be used for the manual Batch No on the sticker? → A: `M-0001` (prefixed and zero-padded to at least 4 digits, e.g., `M-0001`, `M-0002`...).
- Q: How should manual label print runs be tracked in the system? → A: Pre-printing only (tracks/increments the sequence counter and supports reprinting a specific sequence range, without adding stock to live inventory).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Pre-Print Emergency Manual Labels with Remembered Sequence (Priority: P1)

As a farm operator preparing for power or internet outages, I want to navigate to a dedicated Manual Label Printer section, select a product, and enter the number of manual stickers required so that the system generates a print sheet of pre-numbered stickers starting from the next remembered sequence number (e.g., `M-0001`, `M-0002`...) and automatically updates the remembered sequence counter for future print runs.

**Why this priority**: Core capability that enables pre-printing emergency labels with unique sequential batch codes before an outage occurs.

**Independent Test**: Can be fully tested by opening the Manual Label Printer page, selecting a product, entering a sticker count (e.g., 10), generating the labels, verifying the labels display sequential codes `M-0001` through `M-0010` and blank placeholders for `Pkd` and `Exp` dates, and confirming the next starting sequence on the Manual Label Printer page advances to `M-0011` (11).

**Acceptance Scenarios**:

1. **Given** the manual label sequence has not been used yet (current counter is 0, next sequence is 1), **When** the user navigates to the Manual Label Printer section, selects a product, enters a quantity of 10, and submits to generate labels, **Then** 10 labels are rendered for printing with `Batch No:` values `M-0001` through `M-0010`, and the persisted sequence counter is updated to 10 (so the next print run starts at `M-0011`).
2. **Given** the persisted manual label sequence counter is at 10, **When** the user returns to the Manual Label Printer section, **Then** the UI clearly displays that the next manual sticker set will start from `M-0011` (sequence 11), and generating 5 new stickers produces `M-0011` through `M-0015` and advances the counter to 15.
3. **Given** a user generates a set of manual stickers, **When** they check live harvest batches and inventory stock counts, **Then** no new `HarvestBatch` or `InventoryUnit` stock records have been created from the manual label generation.

---

### User Story 2 - FSSAI Label Layout with Write-In Date Placeholders & Print Support (Priority: P1)

As a farm operator packing goods during an emergency outage using pre-printed stickers, I want the manual stickers to have the exact same FSSAI-compliant layout, product details, nutrition table rules, and auto-scaling as the online batch stickers, except with clear write-in placeholders for `Pkd:` and `Exp:` dates, and support both direct browser printing and LAN Server Print.

**Why this priority**: Essential so that pre-printed stickers remain 100% compliant with FSSAI standards, fit existing thermal/sheet label sizes, can be printed either locally or via the LAN print server, and provide adequate blank space to hand-write packaging and expiry dates.

**Independent Test**: Can be tested by generating manual labels for a product with nutrition values on a 75x75mm (or A4/thermal) layout and verifying that all product/company/FSSAI details and conditional nutrition tables match the online label, `Pkd:` and `Exp:` show write-in blank line placeholders instead of dates, and both client-side `Print` and remote `Server Print` work identically to batch printing.

**Acceptance Scenarios**:

1. **Given** a product with FSSAI details, weight, MRP, storage instructions, and conditional nutrition values, **When** manual labels are rendered on the print view, **Then** the visual layout, 10mm vegetarian symbol placeholder, company details, nutrition table inclusion rules, and `autoFitLabels()` dynamic scaling match the online batch label layout.
2. **Given** a rendered manual label on the print view (and in server-side generated PDFs), **When** inspecting the batch and date block, **Then** `Batch No:` displays the sticker's individual manual sequence code (e.g., `M-0001`), while `Pkd:` and `Exp:` display clear write-in placeholders (e.g., `____________` or `____/____/____`) suitable for manual pen entry.
3. **Given** the manual label print view is open, **When** the user clicks either direct `Print` (`window.print()`) or `Server Print` (when the LAN print server is online), **Then** the labels print with the exact manual sequence codes and write-in date placeholders.

---

### User Story 3 - Reprint Specific Sequence Range or Adjust Sequence Counter (Priority: P2)

As a farm operator or administrator, if a thermal printer jams or runs out of ribbon mid-print, I want to be able to reprint a specific manual sequence range (e.g., `seqStart` to `seqEnd`) without advancing the remembered sequence counter, or manually adjust/reset the next starting sequence number if needed.

**Why this priority**: Prevents sequence gaps or wasted sequence numbers when physical printing issues occur during pre-printing.

**Independent Test**: Can be tested by using the reprint range option on the Manual Label Printer page (e.g., from sequence 5 to 10) and verifying that labels `M-0005` through `M-0010` are rendered without incrementing the stored sequence counter.

**Acceptance Scenarios**:

1. **Given** the stored manual sequence counter is 20, **When** the user chooses to reprint an existing range from `5` to `10` for a selected product, **Then** labels `M-0005` through `M-0010` are displayed for printing and the stored sequence counter remains `20`.
2. **Given** an authorized user on the Manual Label Printer section (or settings), **When** they explicitly update/reset the next sequence counter value, **Then** subsequent new manual sticker sets start from the updated sequence value.

---

### Edge Cases

- **Concurrent Manual Print Requests**: What happens if two users click "Generate & Print Manual Labels" at the same time? Sequence allocation MUST be atomic/synchronized at the backend service/database level so two print runs never receive overlapping `M-XXXX` sequence numbers.
- **Sequence Numbers Exceeding 9999**: How does formatting behave when the sequence passes `9999`? Zero-padding to at least 4 digits (`M-%04d`) formats `1` as `M-0001` and `10000` naturally as `M-10000` without truncating digits.
- **Large Sticker Count Request**: What happens if a user enters an excessive number of stickers (e.g., 50,000) or 0/negative numbers? The system MUST validate that the requested sticker count is within safe bounds (e.g., minimum 1, maximum 500 per print run) to prevent browser/PDF rendering memory exhaustion.
- **Invalid Reprint Range**: If a user requests a reprint where `seqStart > seqEnd`, or `seqStart < 1`, or the range exceeds the maximum per-run limit, the system MUST reject the request with a clear validation message.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide a dedicated "Manual Labels" (Manual Label Printer) section accessible from the application navigation (e.g., under the Batches workflow and/or main navigation).
- **FR-002**: System MUST persist a remembered manual label sequence counter in the database (e.g., via `AppSetting` key `manual_label_last_sequence`, defaulting to `0` when uninitialized).
- **FR-003**: System MUST display the current/last sequence number and the next starting sequence code (e.g., `M-0001`) on the Manual Label Printer form so the user always knows where the next set will start.
- **FR-004**: System MUST allow the user to select a Product and enter the number of manual stickers required (validated between 1 and 500 per print run) to generate a new set of manual labels.
- **FR-005**: When generating a new set of $N$ manual stickers, the system MUST atomically advance the persisted sequence counter by $N$ and assign each individual sticker its own sequential batch code formatted as `M-XXXX` (zero-padded to at least 4 digits, e.g., `M-0001`, `M-0002`, ..., `M-9999`, `M-10000`).
- **FR-006**: Generating or printing manual labels MUST NOT create `HarvestBatch` or `InventoryUnit` records in live inventory.
- **FR-007**: System MUST render manual labels using the exact same layout, dimensions, typography, company information, 10mm vegetarian symbol placeholder, conditional nutrition table logic, and `autoFitLabels()` dynamic scaling as the online batch label (`batches/print.html`).
- **FR-008**: On manual labels, `Batch No:` MUST display the sticker's individual manual sequence code (`M-XXXX`), while `Pkd:` and `Exp:` MUST display a clear write-in placeholder (e.g., `____________`) instead of pre-filled dates so operators can write dates by hand during an emergency.
- **FR-009**: The manual label print view MUST support both client-side browser printing (`window.print()`) and remote LAN Server Printing (`PrintServerService`), rendering the exact manual sequence codes and write-in date placeholders in both HTML and server-generated PDFs.
- **FR-010**: System MUST allow users to reprint a specific sequence range (`seqStart` to `seqEnd`) for a selected product without advancing the persisted sequence counter, and allow resetting/updating the stored sequence counter when needed.

### Key Entities *(include if feature involves data)*

- **ManualLabelSequence (`AppSetting`)**: Stores the last used manual label integer sequence (`manual_label_last_sequence`) in persistent application settings so it survives application restarts.
- **ManualLabelPrintRequest / View Model**: Transient representation of a manual print run containing the selected `Product`, sequence range (`startSeq` to `endSeq`), generated list of formatted manual codes (`M-0001`...), and blank date placeholders for rendering in the print template and PDF generator.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Operators can select a product, enter a sticker quantity, and reach the print-ready manual label preview in under 15 seconds.
- **SC-002**: 100% of generated manual stickers in a new set receive unique, contiguous, sequentially incrementing `M-XXXX` batch codes starting immediately after the previously remembered sequence number, even across application restarts.
- **SC-003**: 100% of manual labels match the configured sheet/thermal dimensions and FSSAI layout of online labels while providing legible blank placeholders for handwritten `Pkd` and `Exp` dates.
- **SC-004**: Generating manual labels results in zero phantom stock additions to live inventory batches or unit counts.

## Assumptions

- Operators will select a specific product when pre-printing manual stickers because product name, net weight, MRP, FSSAI license, storage instructions, and nutrition facts are printed on the sticker.
- A single global sequence (`M-0001`, `M-0002`, ...) across manual stickers is simpler and avoids collision regardless of which product is printed.
- Both direct browser printing and LAN Server Print should work on the manual label print view just as they do on the regular batch print view.
