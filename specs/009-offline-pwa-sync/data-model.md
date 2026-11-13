# Phase 1 Data Model: Offline-First PWA & Outbox Sync (`009-offline-pwa-sync`)

**Feature**: `009-offline-pwa-sync`
**Date**: 2026-11-13

## 1. Server-Side JPA Entity: `ProcessedOfflineMutation` (`sk_processed_offline_mutations`)

Tracks every offline mutation ID already applied by the server to guarantee idempotent replay.

| Field | Java Type | Column / Constraints | Description |
|-------|-----------|----------------------|-------------|
| `id` | `Long` | PK, Auto-Increment | Internal primary key |
| `clientMutationId` | `String` | `unique = true, nullable = false`, length 100 | Client-generated UUID for the offline mutation |
| `mutationType` | `String` | `nullable = false`, length 60 | e.g., `CREATE_BATCH`, `OVERRIDE_PROCUREMENT_DATE` |
| `processedAt` | `LocalDateTime` | `nullable = false` | Timestamp when the server applied the mutation |
| `summary` | `String` | length 255 | Human-readable audit description |

## 2. DTOs for Offline Snapshot & Sync

### `OfflineSnapshotDto`
Returned by `GET /api/offline/snapshot`:
- `timestamp` (`String` ISO-8601)
- `products` (`List<ProductSnapshotItem>`): `id`, `sku`, `name`, `defaultExpiryDays`, `fssaiLicenseNumber`, `netWeight`, `netWeightUom`, `mrp`, `storageInstructions`, `hasNutritionValues`, `nutritionBaseUnitValue`, `nutritionBaseUnitType`, `nutritionLineItems` (`componentName`, `amount`, `measurementUnit`)
- `settings` (`Map<String, String>`): `company_name`, `company_phone`, `company_registered_office`, `company_customer_care`, `label_sheet_size`, `custom_label_width`, `custom_label_height`, `procurement_blackout_dates`, etc.
- `manualLabelLastSequence` (`int`)
- `recentBatches` (`List<BatchSnapshotItem>`): `id`, `batchCode`, `productId`, `productName`, `batchDate`, `expiryDate`, `totalUnits`
- `procurementSchedules` (`List<ProcurementScheduleSnapshotItem>`): `id`, `scheduleName`, `supplyLastingDays`, `vendorLeadTime`, `actionBuffer`, `preferredDeliveryDay`, `nodes` (`cycleIndex`, `theoreticalNeedDate`, `actualDeliveryDate`, `orderCutoffDate`, `actionDate`, `manualOverride`, `gapDays`, `stockoutRisk`, `holidayAdjusted`, `holidayAdjustedDays`)

### `OfflineSyncRequest` & `OfflineMutationDto`
Accepted by `POST /api/offline/sync`:
- `mutations` (`List<OfflineMutationDto>`):
  - `clientMutationId` (`String`, required)
  - `mutationType` (`String`, required):
    - `CREATE_BATCH`: `{ productId, quantity, batchDate, batchCode }`
    - `ALLOCATE_MANUAL_LABELS`: `{ productId, quantity, seqStart, seqEnd }`
    - `SET_MANUAL_LABEL_SEQUENCE`: `{ lastSequence }`
    - `CREATE_PROCUREMENT_SCHEDULE`: `{ scheduleName, supplyLastingDays, vendorLeadTime, actionBuffer, preferredDeliveryDay, initialDeliveryDate }`
    - `OVERRIDE_PROCUREMENT_DATE`: `{ scheduleId, scheduleName, cycleIndex, newDeliveryDate }`
    - `UPDATE_BLACKOUT_DATES`: `{ blackoutDates }`
    - `UPDATE_SETTINGS`: `{ settings: Map<String, String> }`
