# Quickstart & Validation Guide: FSSAI Compliant Batch Labels

This guide outlines how to manually validate the new FSSAI batch label template changes.

## Prerequisites
- The application must be running locally.
- A product must exist with `nutritionEnabled = true`, an FSSAI license number, and a few `NutritionLineItem`s defined.
- A harvest batch containing some inventory units must exist for the product.

## Setup Commands

No external commands are needed. The database seeder will automatically insert the necessary global configuration values for the registered office address and customer care address upon startup.

## Validation Steps

1. **Verify Seed Data**
   - Navigate to the Settings/Admin page in the UI (or query the database `sk_app_settings` table).
   - Ensure `company.registered_office_address` and `company.customer_care_address` have sample data.

2. **Test Large Label (>= 75x75mm)**
   - Navigate to a Batch Details page.
   - Click "Print Labels" and select the **THERMAL_CUSTOM** format, specifying Width: 75 and Height: 75 (or larger, e.g. 100x150mm).
   - **Expected Outcome:** The print preview should display:
     - The FSSAI License Number clearly visible, and the QR code is explicitly hidden.
     - A 10x10mm square (or equivalent small box) serving as the Veg symbol placeholder.
     - The Farm Name, Registered Office Address, and Customer Care Address + Contact accurately rendered with the contact number emphasized.
     - The Nutrition Information table scaled automatically to fit 5-6 sample items smoothly.
     - Storage instructions, packing date, and expiry date prominently emphasized.

3. **Test Small Label (< 75mm)**
   - Navigate to the same Batch Details page.
   - Click "Print Labels" and select a format smaller than 75x75mm (e.g., **THERMAL_2x1** (50x25mm) or custom 50x50mm).
   - **Expected Outcome:** The print preview should display the FSSAI License, Addresses, dates, and veg symbol placeholder, but **must explicitly exclude** the Nutrition Information table to conserve space.
