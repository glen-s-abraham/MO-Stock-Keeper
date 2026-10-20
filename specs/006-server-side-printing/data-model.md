# Data Model: Remote Server-Side Label Printing

**Feature**: `006-server-side-printing`
**Date**: 2026-10-20

## 1. Persistent Configuration Entities (`AppSetting`)

No schema migration is required for `AppSetting` (`app_settings` table) as it stores key-value pairs dynamically. A new standardized setting key is added to `SettingsService`:

| Setting Key (`settingKey`) | Type | Default Fallback (`application.properties`) | Validation Rules | Description |
| :--- | :--- | :--- | :--- | :--- |
| `print_server_url` | `String` | `http://192.168.1.53:8080` (`${app.print-server.url}`) | Must be a valid `http://` or `https://` URI with host and optional port/path; empty/blank falls back to property default or evaluates as unavailable if explicitly cleared | Base URL of the LAN print bridge (`H30PrintServer`) or full print endpoint URL |

### Application Properties (`application.properties` / `application-dev.properties` / `application-prod.properties`)

| Property Key | Default Value | Description |
| :--- | :--- | :--- |
| `app.print-server.url` | `http://192.168.1.53:8080` | Default base URL for the LAN label print server workstation |
| `app.print-server.connect-timeout-ms` | `1500` | Maximum timeout in milliseconds when probing print server reachability |
| `app.print-server.read-timeout-ms` | `5000` | Maximum timeout in milliseconds when dispatching a PDF print job |

---

## 2. Domain / Data Transfer Objects (DTOs)

### `PrintServerStatusResponse` (or Map payload)
Represents the current connectivity status of the configured LAN print server:
- `available` (`boolean`): `true` if the print server is configured and reachable within the timeout window, `false` otherwise.
- `printServerUrl` (`String`): The resolved print server URL (e.g., `http://192.168.1.53:8080`).
- `message` (`String`): Human-readable status detail (e.g., `"Connected to 192.168.1.53:8080"` or `"Print server unreachable at 192.168.1.53:8080"`).

### `ServerPrintResponse`
Represents the outcome of a server-side print job request:
- `success` (`boolean`): Whether the PDF label payload was generated and accepted by the print server.
- `batchId` (`Long`): ID of the `HarvestBatch` printed.
- `unitsPrinted` (`int`): Number of `InventoryUnit` labels included in the generated multi-page PDF.
- `bytesSent` (`int`): Size of the generated PDF payload in bytes.
- `message` (`String`): Confirmation message or actionable error description.

---

## 3. Existing Entities Referenced (Read-Only)

- **`HarvestBatch`**: Provides `id`, `batchCode`, `batchDate`, `expiryDate`, and associated `Product`.
- **`Product`**: Provides `name`, `fssaiLicenseNumber`, `netWeight`, `netWeightUom`, `mrp`, `storageInstructions`, `hasNutritionValues`, `nutritionBaseUnitValue`, `nutritionBaseUnitType`, and `nutritionLineItems`.
- **`InventoryUnit`**: Represents individual packaged units in the batch filtered by optional `unitId` or `seqStart`–`seqEnd` range.
