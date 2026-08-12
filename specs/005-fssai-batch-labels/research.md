# Research: FSSAI Compliant Batch Labels

## Clarifications
No technical clarifications were needed during the planning phase. The technical context is clear and relies entirely on modifying existing HTML/CSS layouts using Thymeleaf and fetching simple properties via existing configuration services.

## Architectural Decisions

- **Decision:** Utilize existing `AppSetting` entity for FSSAI compliance settings.
- **Rationale:** The application already has a mechanism for global settings (`SettingsService`). Reusing this for `registeredOfficeAddress`, `customerCareAddress`, and `fssaiLicenseNumber` prevents unnecessary table creation and simplifies administration.
- **Alternatives considered:** Creating a dedicated `FarmSettings` entity, but this adds unnecessary complexity since these are singleton configuration values.

- **Decision:** Pure CSS/HTML for label size detection and conditional rendering.
- **Rationale:** Thymeleaf templates can perform logic on the provided variables (e.g. `labelSheetSize`, `customLabelWidth`, `customLabelHeight`). We will use Thymeleaf `th:if` statements to conditionally render the nutrition table if the label dimensions are >= 75mm x 75mm. 
- **Alternatives considered:** Backend calculation of a boolean flag `showNutritionTable`. This is also viable and slightly cleaner for the view, so we will implement the logic in the controller/service to pass a boolean to the template.

- **Decision:** Remove QR code and rely on visual emphasis for critical fields.
- **Rationale:** Space constraints on smaller labels and user preference dictates removing the QR code temporarily (FR-009). Critical fields (Expiry, Pack Date, Storage, Contact) will use bold/larger fonts for emphasis.
- **Alternatives considered:** Keeping QR code but scaling it down, which would risk scannability.

- **Decision:** Dynamic flexbox/table layout for the 5-6 item nutrition table.
- **Rationale:** The table needs to auto-adjust to fit the remaining space without overflowing. We will utilize CSS Flexbox and percentage-based table layouts so it scales correctly on 75x75 and larger sheets.
