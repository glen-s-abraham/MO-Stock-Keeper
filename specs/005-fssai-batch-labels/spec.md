# Feature Specification: FSSAI Compliant Batch Labels

**Feature Branch**: `[###-fssai-batch-labels]`

**Created**: 2026-08-12

**Status**: Draft

**Input**: User description: "so in case of printing the batch labels. if the size of the paper if  75 or above for both height and width and nutrition values enabled you need to include the nutrition table along with the rest of the information. here's a sample label i am going for for the fssai complaince and somehere leave a placeholder 10mm for pasting a veg symbol. also verify the nameaddress and contact of the farm will be correctly included then then storage details packaged and expiry well aligned modern labeling for fssai complaince. i am planning to fix stuff in 75x75 label.so plan accordingly also add seed data for these."

## Clarifications

### Session 2026-08-12

- Q: What is the primary entity or container this batch label will be applied to? → A: Individual retail units (e.g., 200g packs).
- Q: What is the desired output format for the generated batch labels when triggering a print? → A: HTML/CSS rendered directly in the browser print dialog.
- Q: Should the QR code be included in the FSSAI label? → A: Remove the QR code for the time being.
- Q: Which specific label details should be emphasized? → A: Package date, expiry date, storage instructions, and the contact number must be emphasized.
- Q: How many items should the nutrition table contain and how should it behave? → A: It needs 5-6 items additionally and should be self-adjusting to fit the available space.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Print FSSAI Compliant Label (75x75mm or larger) (Priority: P1)

As a farm operator packing harvested goods, I want to print an FSSAI-compliant batch label of size 75x75mm (or larger) that automatically includes a nutrition table (if enabled for the product), so that the packaged goods comply with regulatory standards before dispatch.

**Why this priority**: Core compliance requirement for packaging products for retail or wholesale distribution.

**Independent Test**: Can be fully tested by generating a print preview for a product with nutrition data on a 75x75 paper size and verifying all required FSSAI fields are present and aligned.

**Acceptance Scenarios**:

1. **Given** a product with nutrition values enabled and farm details configured, **When** I trigger label printing for a 75x75mm paper size, **Then** the generated label includes the nutrition table, FSSAI license number, farm address, storage details, dates, and a 10mm placeholder for the veg symbol.

---

### User Story 2 - Print Standard FSSAI Label (under 75mm) (Priority: P2)

As a farm operator packing goods on smaller labels (under 75mm width/height), I want to print an FSSAI-compliant label that omits the nutrition table to save space, but retains all other mandatory FSSAI information.

**Why this priority**: Required for smaller packaging formats where 75x75mm labels cannot fit, ensuring regulatory compliance without layout breakage.

**Independent Test**: Can be tested by generating a label on 50x50mm paper and verifying the nutrition table is absent but farm, storage, and date details remain well-aligned.

**Acceptance Scenarios**:

1. **Given** a product with nutrition values enabled, **When** I trigger label printing for a 50x50mm paper size, **Then** the generated label excludes the nutrition table but includes all other FSSAI required fields and the veg symbol placeholder.

---

### User Story 3 - Database Seeding for Label Readiness (Priority: P2)

As a developer or system administrator, I want to deploy seed data containing sample farm details, FSSAI license numbers, and storage instructions so that the label printing feature can be tested and demonstrated immediately.

**Why this priority**: Ensures the label printing feature works out-of-the-box in development and testing environments without manual data entry.

**Independent Test**: Run the seeding script and verify that the database contains the required FSSAI and farm configuration data.

**Acceptance Scenarios**:

1. **Given** an empty or existing development database, **When** the seeding process is executed, **Then** farm contact, FSSAI number, and sample storage details are successfully populated in the database.

### Edge Cases

- What happens when a product has nutrition values enabled, the label is 75x75mm, but nutrition data is missing/empty? (Should handle gracefully, maybe hide table or show empty dashes).
- How does the system handle extremely long farm addresses or product names on a fixed 75x75mm layout? (Text should wrap or truncate cleanly without breaking alignment).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST support generating print-ready batch labels for inventory units.
- **FR-002**: System MUST dynamically include a nutrition information table on the label IF and ONLY IF the configured paper dimensions are >= 75mm (both height and width) AND the product has nutrition values enabled. The table MUST support 5-6 additional items and auto-scale/self-adjust to fit the available space.
- **FR-003**: System MUST reserve a precise 10mm x 10mm placeholder space on the label layout for manual application of a standard vegetarian symbol.
- **FR-004**: System MUST accurately retrieve and render the configured Farm Name, Registered Office Address, Customer Care Address, and Customer Care Contact on the label, with the contact number visually emphasized.
- **FR-005**: System MUST include FSSAI License Number prominently on the label layout.
- **FR-006**: System MUST clearly display and emphasize packaging date, expiry date ("Used By"), and specific storage instructions (e.g., "STORED AT 4°C - 8°C") with proper modern alignment.
- **FR-007**: System MUST provide database seed migrations/scripts to pre-populate necessary FSSAI, farm, and storage configuration data for testing (including 5-6 sample nutrition items).
- **FR-008**: System MUST generate the label as an HTML/CSS view optimized for direct printing via the browser print dialog.
- **FR-009**: System MUST omit/remove the QR code from the batch label for the time being.

### Key Entities *(include if feature involves data)*

- **LabelConfiguration/FarmSettings**: Stores FSSAI license, registered office, customer care details.
- **Product/InventoryUnit**: Provides the product name, weight, dates, and nutrition values (represents individual retail units, e.g., 200g packs).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Label generation for a batch of 100 inventory units completes in under 2 seconds.
- **SC-002**: Generated 75x75mm label layout perfectly accommodates the nutrition table without overflowing standard page margins in print preview.
- **SC-003**: 100% of FSSAI mandatory fields (License, Dates, Weight, Origin) are present on the generated template.
- **SC-004**: Seed data script executes without errors during application startup or manual execution.

## Assumptions

- We are assuming standard FSSAI layout rules apply as per the provided sample image.
- We assume the system already has a mechanism to define/select paper sizes during the printing process.
- We assume the Veg symbol will be physically pasted or pre-printed, hence the requirement for a "placeholder" rather than rendering the graphic directly.
