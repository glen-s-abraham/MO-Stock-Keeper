# Data Model Updates: FSSAI Compliant Batch Labels

No new entities are being created, but the existing `AppSetting` entity and `SettingsService` will be extended to support the following keys:

## Settings Keys (AppSetting)
- `company.name` (Existing)
- `company.contact_number` (Existing)
- `company.registered_office_address` (New): String, stores the registered office address for FSSAI compliance.
- `company.customer_care_address` (New): String, stores the customer care physical address.

## Product Entity (Existing)
- `fssaiLicenseNumber`: (Existing/Expected) String, stores the FSSAI license number.
- `nutritionEnabled`: (Existing) Boolean, toggles nutrition table visibility.
- `servingSize`: (Existing) String, serving size for the nutrition table.
- `nutritionItems`: (Existing) One-to-Many mapping to `NutritionLineItem`.
