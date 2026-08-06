<!--
Sync Impact Report:
- Version change: Initial template -> 1.0.0
- Modified principles:
  - [PRINCIPLE_1_NAME] -> I. Financial Integrity & Explicit Auditability (NON-NEGOTIABLE)
  - [PRINCIPLE_2_NAME] -> II. Complete Inventory Traceability & QR Lifecycle
  - [PRINCIPLE_3_NAME] -> III. Strict Security & Data Ownership
  - [PRINCIPLE_4_NAME] -> IV. Defensive Concurrency & Data Consistency
  - [PRINCIPLE_5_NAME] -> V. Database-Driven Aggregation & Performance
- Added sections:
  - Architecture & Technology Stack
  - Development & Quality Workflow
- Removed sections: None
- Follow-up TODOs: None
-->

# MO-Stock-Keeper Constitution

## Core Principles

### I. Financial Integrity & Explicit Auditability (NON-NEGOTIABLE)
All financial transactions, payment allocations, credit note usages, and invoice reversals MUST maintain explicit, immutable database records.
- Payments MUST be explicitly linked to the exact target invoices via dedicated allocation entities; implicit date-based debt shifting is strictly prohibited.
- Credit note redemptions MUST record exact transaction links (`CreditNoteUsage`) detailing invoice ID, timestamp, and amount applied.
- Financial records and customer entities MUST NEVER be hard-deleted. System MUST use soft deletion (`isHidden = true`) to preserve immutable audit trails.
- Tax amounts MUST be recorded per line-item unit at the time of sale to eliminate rounding anomalies during partial returns.

### II. Complete Inventory Traceability & QR Lifecycle
Every inventory unit MUST maintain continuous traceability from farm production to retail fulfillment using ZXing-generated QR codes.
- Every `InventoryUnit` MUST adhere to explicit, valid lifecycle states (e.g., `AVAILABLE`, `ALLOCATED`, `RETURNED`, `RESTOCKED`, `SPOILED`).
- Transitions between inventory states MUST be fully triaged and auditable. State transitions MUST NOT result in dead-end inventory states.
- Scanned physical items MUST map 1-to-1 with system states to prevent ghost inventory or unaccounted stock variance.

### III. Strict Security & Data Ownership
Security and access controls MUST be enforced at both controller and service layers to protect multi-role data boundary integrity.
- Administrative operations MUST require explicit `@PreAuthorize("hasRole('ADMIN')")` annotations.
- Endpoint parameters (such as `customerId`, `orderId`, or `invoiceId`) MUST enforce strict ownership verification to prevent Insecure Direct Object Reference (IDOR) vulnerabilities across user roles.
- Cross-tenant or role-based data isolation MUST be systematically verified in integration tests.

### IV. Defensive Concurrency & Data Consistency
System operations handling shared inventory and financial document sequence generation MUST prevent race conditions under high concurrency.
- Domain entities susceptible to concurrent updates (including `InventoryUnit`, `SalesOrder`, and `Invoice`) MUST implement JPA optimistic locking (`@Version`).
- Invoice and document sequence numbers MUST utilize atomic database sequences or synchronized generators to avoid duplicate constraint violations.
- Double-allocation of physical QR inventory items across concurrent scanning users MUST be structurally impossible.

### V. Database-Driven Aggregation & Performance
Data processing and financial aggregations MUST be executed natively within the database engine to guarantee scalability.
- Financial reporting, inventory balances, and summary metrics MUST rely on SQL `GROUP BY` aggregations rather than in-memory entity iteration.
- Web endpoints displaying list views or transactional tables MUST implement server-side pagination and sorting.

## Architecture & Technology Stack

The MO-Stock-Keeper system relies on a standardized, modern Java backend stack:
- **Language & Runtime**: Java 21 LTS with Maven build configuration.
- **Framework**: Spring Boot 3.4.x (Spring Web, Spring Security, Spring Data JPA, Spring Validation).
- **Template Engine**: Thymeleaf with Thymeleaf Layout Dialect and Spring Security 6 extras.
- **Database Layer**: H2 (In-Memory for local development and integration tests), PostgreSQL for production database deployments.
- **Utilities**: Lombok for boilerplate reduction, ZXing 3.5.x for QR code encoding/decoding.

## Development & Quality Workflow

All contributions to MO-Stock-Keeper MUST adhere to strict verification and quality standards:
1. **Automated Verification**: All new features and bug fixes MUST include unit and integration tests covering positive execution, edge cases, and role authorization boundaries.
2. **Financial & Concurrency Tests**: Concurrent QR scans and payment reversal flows MUST be validated via concurrent test execution suites.
3. **Database Migration & Safety**: Schema changes altering financial or inventory tables MUST maintain backward compatibility and preserve historical data.
4. **Code Quality**: Code MUST pass compiler warnings, Lombok annotation processors, and lint checks cleanly before pull request merge.

## Governance

This Constitution represents the supreme technical governance policy for the MO-Stock-Keeper codebase.
- **Supremacy**: All architectural decisions, design documents, pull requests, and implementations MUST strictly comply with this document.
- **Amendment Policy**: Amendments require formal proposal, documented rationale, approval from project leads, and an explicit version bump.
- **Versioning Policy**:
  - **MAJOR**: Backward-incompatible principle redefinitions or structural governance changes.
  - **MINOR**: Addition of new core principles, workflow sections, or material guidance expansion.
  - **PATCH**: Non-semantic clarifications, formatting, or typographical corrections.
- **Compliance Audits**: Every pull request code review MUST evaluate compliance against Core Principles I through V.

**Version**: 1.0.0 | **Ratified**: 2026-07-31 | **Last Amended**: 2026-07-31
