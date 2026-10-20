# Feature Specification: Remote Server-Side Label Printing

**Feature Branch**: `[006-server-side-printing]`

**Created**: 2026-10-20

**Status**: Draft

**Input**: User description: "so apprently we have client side prining option now. i would also like to bring in an extension for server side printing that is i will expose this application through a cloudflare tunnel so i can remoteley print the labels on a network exposed printed on my lan network. at the same time in lan printer connected system you can go for the direct print as of now. so i have configures printer sharing and stuff on the workstation where the label printer is currently connected... so i want you to implement an endpoint then a button to the print page and this button should only be enabled if the server side printer connectivity is available. or whatever also add the printer server confi to the application and use that as well"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Remote Server-Side Label Printing (Priority: P1)

As a remote operator accessing the inventory application from outside the local network (e.g., via a remote tunnel on a phone or external computer), I want to trigger label printing directly from the application server to the local network label printer workstation while retaining the existing client-side browser print button, so that physical batch labels can be printed at the packing station regardless of where I am accessing the system from.

**Why this priority**: Core capability requested by the user to enable remote label printing when accessing the web application externally, while keeping direct client-side printing intact for local workstations.

**Independent Test**: Can be fully tested by opening a batch label print preview page when the print server is reachable, clicking the server-side print button, and verifying that the print job is transmitted to the configured print server endpoint and confirmed to the user.

**Acceptance Scenarios**:

1. **Given** a configured and reachable LAN print server and a batch print page, **When** the user views the batch print page, **Then** both the existing client-side "Print" button and the new "Server Print" (or remote print) button are visible and enabled.
2. **Given** an enabled "Server Print" button on the batch print page, **When** the user clicks the button, **Then** the system transmits the label print payload for the selected batch units to the configured print server, prevents duplicate accidental submissions while in progress, and displays a clear success or failure notification.
3. **Given** a user on a local workstation with a directly connected printer, **When** the user clicks the standard client-side "Print" button, **Then** the browser's native print dialog opens exactly as before without interference from the server-side print extension.

---

### User Story 2 - Dynamic Printer Connectivity Verification & Button State (Priority: P1)

As a user on the batch label print page, I want the server-side print button to automatically reflect whether the network print server is currently reachable, so that I do not attempt server-side print jobs when the printer workstation is offline or unreachable.

**Why this priority**: Explicitly required by the user ("this button should only be enabled if the server side printer connectivity is available") to prevent failed print attempts and provide immediate visual feedback on printer server availability.

**Independent Test**: Can be tested by loading the batch print page both when the configured print server address is reachable and when it is unreachable (or unconfigured), and verifying the server-side print button enables or disables accordingly.

**Acceptance Scenarios**:

1. **Given** the print server configuration is valid and the print server is online and reachable from the application host, **When** the user opens the batch print page, **Then** the server-side print button is enabled and indicates ready status.
2. **Given** the print server is offline, unreachable within a short timeout, or not configured, **When** the user opens the batch print page, **Then** the server-side print button is disabled (with an informative status or tooltip explaining that the print server is unavailable), while the client-side "Print" button remains enabled.

---

### User Story 3 - Print Server Configuration Management (Priority: P2)

As a system administrator, I want to configure and update the LAN print server connection details (such as the print server URL/host and port) in the application settings (with sensible defaults from environment/application configuration), so that the system knows where to check connectivity and dispatch server-side print jobs without requiring code changes if the workstation IP or port changes.

**Why this priority**: Essential for maintaining the connection to the LAN print bridge (e.g., `http://192.168.1.53:8080`) and supporting both initial configuration defaults and runtime administrative updates.

**Independent Test**: Can be tested by navigating to the System Settings page, updating the print server configuration, saving, and verifying that subsequent connectivity checks and print requests use the updated address.

**Acceptance Scenarios**:

1. **Given** an administrator on the System Settings page, **When** they view the Label Print Settings section, **Then** they can see and edit the print server configuration (e.g., Print Server URL / Host & Port, defaulting to the configured LAN bridge address such as `http://192.168.1.53:8080`).
2. **Given** updated print server settings are saved by an administrator, **When** any user opens the batch print page, **Then** the system uses the newly saved print server configuration to verify connectivity and send print jobs.

---

### Edge Cases

- **Print Server Goes Offline Mid-Session**: What happens if the print server is online when the print page loads (so the button is enabled), but goes offline right before the user clicks "Server Print"? The system MUST handle the connection timeout/error gracefully during the print request and display an actionable error message to the user without crashing.
- **Unconfigured or Malformed Print Server Address**: How does the system handle an empty or invalid URL/IP in the print server settings? The connectivity check MUST safely evaluate to unavailable (disabling the server print button) rather than throwing unhandled page rendering errors.
- **Large Batch or Filtered Unit Range Printing**: What happens when a user filters the print page to a single unit (`unitId`) or a sequence range (`seqStart` to `seqEnd`) vs. printing the entire batch? The server-side print job MUST respect the exact subset of units currently selected/displayed on the print page.
- **Slow Network / Firewall Drop on LAN**: If the workstation firewall blocks the port or the host IP is unreachable on the LAN, the connectivity check MUST enforce a strict, short timeout (e.g., 1.5–2 seconds) so that loading the print page or checking status does not hang the user interface.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST maintain existing client-side browser printing on the batch label print page alongside the new server-side printing option.
- **FR-002**: System MUST store and manage Print Server configuration (including the print server endpoint URL/host, with a default fallback of `http://192.168.1.53:8080` configurable via application properties and editable in System Settings).
- **FR-003**: System MUST check connectivity to the configured print server with a strict timeout (maximum 2 seconds) to determine whether server-side printing is currently available.
- **FR-004**: System MUST display a dedicated "Server Print" button on the batch label print page that is enabled ONLY when server-side printer connectivity is verified as available, and disabled with a clear visual indicator when unavailable.
- **FR-005**: System MUST provide an application endpoint to trigger server-side label printing for a batch (supporting full batch, single unit, or sequence range matching the print view).
- **FR-006**: System MUST generate and transmit the batch labels as a multi-page PDF document (or rendered page sequence) that replicates the exact visual layout, page dimensions, and auto-scaled bounding-box formatting of the current print setup (1 label per page for thermal roll formats, or multi-label grid pages for A4 formats) to the configured LAN print server endpoint.
- **FR-007**: System MUST provide immediate visual feedback on the print page when a server-side print job is initiated (in-progress state), succeeded, or failed (with error details).
- **FR-008**: System MUST restrict print server configuration modifications in System Settings to authorized administrative users, consistent with existing system settings security.

### Key Entities *(include if feature involves data)*

- **PrintServerConfig (AppSetting / Application Properties)**: Represents the connection settings for the LAN label print server, including endpoint URL/host, port/protocol, and timeout thresholds.
- **PrintJobRequest**: Represents a user-initiated request to print one or more batch inventory unit labels via the server-side print bridge, including batch identifier, selected unit filter/range, and label payload/dimensions.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: When the print server is unreachable or offline, the batch label print page still loads and updates the server-side print button state to disabled within 2 seconds without blocking client-side printing.
- **SC-002**: Remote users accessing the application over an external tunnel can initiate and complete a server-side print request for a batch label with feedback displayed in under 5 seconds (assuming an active LAN print server).
- **SC-003**: 100% of labels printed via the server-side print feature contain the required FSSAI batch, product, company, and conditional nutrition information matching the configured label dimensions.
- **SC-004**: Administrators can update the print server network address through the settings interface in under 1 minute without restarting the application server.

## Assumptions

- The Linux application server and the Windows workstation running `H30PrintServer` (`192.168.1.53`) are connected to the same local area network (LAN), allowing direct HTTP/TCP communication from the Linux backend to the Windows print server even when the end-user accesses the web app remotely via a Cloudflare Tunnel.
- Client-side printing via the browser print dialog remains the primary fallback and direct-print mechanism when a user is locally connected to a printer.
- Server-side printing is specifically targeted at batch label printing (`/batches/{id}/print`), where the thermal label printer (`H30C-lite`) is used.
