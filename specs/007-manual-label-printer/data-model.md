# Data Model: Manual Label Printer

## 1. Persistent Entities

### `AppSetting` (Existing Entity — `app_settings` table)

Used to persist the last allocated manual label sequence number across application restarts.

| Setting Key | Type (Stored as String) | Default | Validation / Constraints | Description |
|-------------|-------------------------|---------|--------------------------|-------------|
| `manual_label_last_sequence` | Non-negative Integer (`0`, `1`, `2`, ...) | `"0"` | `>= 0` | Stores the last sequence number allocated for manual stickers. Next new sticker set starts at `manual_label_last_sequence + 1`. |

## 2. Value Objects / DTOs

### `ManualLabelRange` (`com.mushroom.stockkeeper.dto.ManualLabelRange`)

Represents a contiguous range of manual sticker sequence numbers allocated or selected for printing.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `startSeq` | `int` | `>= 1` | First sequence number in the set |
| `endSeq` | `int` | `>= startSeq`, `endSeq - startSeq + 1 <= 500` | Last sequence number in the set |
| `count` | `int` | `1..500` | Total number of stickers in the range (`endSeq - startSeq + 1`) |

**Formatting Rule**:
- Any sequence integer `seq` is formatted as `String.format("M-%04d", seq)`:
  - `1` → `"M-0001"`
  - `42` → `"M-0042"`
  - `9999` → `"M-9999"`
  - `10000` → `"M-10000"`

## 3. Transient Print View Construction (No Inventory DB Persistence)

When rendering `/batches/manual-labels/print` or generating a PDF via `/batches/manual-labels/server-print`:

1. **Transient `HarvestBatch`**:
   - `id`: `0L`
   - `product`: Selected `Product` entity loaded from `ProductRepository`
   - `batchCode`: Summary range string (e.g., `"M-0001 to M-0010"` or `"M-0001"` if single sticker) for the non-printed top toolbar header
   - `batchDate`: `null` (signals write-in placeholder `"____________"` for `Pkd:`)
   - `expiryDate`: `null` (signals write-in placeholder `"____________"` for `Exp:`)
   - `totalUnits`: `endSeq - startSeq + 1`

2. **Transient `List<InventoryUnit>`**:
   - One transient `InventoryUnit` per sequence number `seq` in `[startSeq..endSeq]`:
     - `id`: `(long) seq`
     - `batch`: The transient `HarvestBatch`
     - `uuid`: `String.format("M-%04d", seq)` (used as the individual sticker's `Batch No:` value)
     - `qrCodeContent`: `"U:" + uuid`
