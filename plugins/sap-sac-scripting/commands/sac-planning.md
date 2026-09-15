---
name: sac-planning
description: Generate SAC planning operation templates and workflows
allowed-tools:
  - Read
argument-hint: "<publish|copy|lock|workflow|version|dataaction>"
arguments:
  - name: operation
    description: "Planning operation: publish, copy, lock, workflow, version, dataaction"
    required: true
---

## Shell Snippet Notes

- Shell snippets assume Bash on Linux/macOS, WSL2, or Git Bash.
- Install the command-specific tooling shown near each snippet before running it.
- Confirm before running commands that delete files, change ownership, deploy, or modify remote systems.

## Output Contract

Return planning API guidance, version/data-locking caveats, safe script examples, and pending SAC tenant checks. Default to read-only guidance; do not publish planning data or write files unless the user explicitly confirms the target.


Generate SAC planning operation templates based on the requested operation type.

## Available Operations

### publish
Publish private version data to public version.

### copy
Copy data between versions (private to public, public to private).

### lock
Data locking patterns for planning input control.

### workflow
Complete planning workflow with status management.

### version
Version management (create, get, delete versions).

### dataaction
Data action integration with scripts.

---

## Operation: publish

```javascript
/*
 * Publish Private to Public Version
 * Purpose: Submit planning data for approval/consolidation
 * Prerequisites: User has private version with changes
 */

function publishPlanningData() {
    var planning = Table_Planning.getPlanning();

    Application.showBusyIndicator("Publishing planning data...");

    try {
        // Verify private version exists
        var privateVersion = planning.getPrivateVersion();

        if (privateVersion === null) {
            Application.showMessage(
                ApplicationMessageType.Warning,
                "No private version found. Nothing to publish."
            );
            return;
        }

        // Check for unsaved changes (if applicable)
        // planning.hasChanges() - in some versions

        // Publish to public version
        planning.publish();

        Application.showMessage(
            ApplicationMessageType.Success,
            "Planning data published successfully"
        );

        // Refresh view to show updated data
        Table_Planning.getDataSource().refreshData();

    } catch (error) {
        Application.showMessage(
            ApplicationMessageType.Error,
            "Publish failed: " + error.message
        );
        console.error("Publish error:", error);
    } finally {
        Application.hideBusyIndicator();
    }
}

// Call on button click
publishPlanningData();
```

---

## Operation: copy

```javascript
/*
 * Copy Data Between Versions
 * Purpose: Initialize private version from public, or reverse
 */

// Copy PUBLIC to PRIVATE (initialize working copy)
function copyPublicToPrivate() {
    var planning = Table_Planning.getPlanning();

    Application.showBusyIndicator("Creating working copy...");

    try {
        // Create private version if doesn't exist
        var privateVersion = planning.getPrivateVersion();
        if (privateVersion === null) {
            planning.createPrivateVersion();
        }

        // Copy data from public to private
        planning.copyFromPublicVersion();

        Application.showMessage(
            ApplicationMessageType.Success,
            "Working copy created from public data"
        );

        Table_Planning.getDataSource().refreshData();

    } catch (error) {
        Application.showMessage(
            ApplicationMessageType.Error,
            "Copy failed: " + error.message
        );
    } finally {
        Application.hideBusyIndicator();
    }
}

// Copy PRIVATE to PUBLIC (alternative to publish)
function copyPrivateToPublic() {
    var planning = Table_Planning.getPlanning();

    Application.showBusyIndicator("Copying to public version...");

    try {
        planning.copyToPublicVersion();

        Application.showMessage(
            ApplicationMessageType.Success,
            "Data copied to public version"
        );

        Table_Planning.getDataSource().refreshData();

    } catch (error) {
        Application.showMessage(
            ApplicationMessageType.Error,
            "Copy failed: " + error.message
        );
    } finally {
        Application.hideBusyIndicator();
    }
}
```

---

## Operation: lock

```javascript
/*
 * Data Locking Pattern
 * Purpose: Control concurrent access to planning data
 */

// Check lock status
function checkLockStatus() {
    var planning = Table_Planning.getPlanning();
    var lockInfo = planning.getLockInfo();

    if (lockInfo.isLocked) {
        if (lockInfo.lockedBy === Application.getUserInfo().id) {
            console.log("Data is locked by current user");
            return "owned";
        } else {
            console.log("Data is locked by: " + lockInfo.lockedBy);
            Application.showMessage(
                ApplicationMessageType.Warning,
                "Data is locked by: " + lockInfo.lockedByName
            );
            return "other";
        }
    }
    return "unlocked";
}

// Acquire lock before editing
function acquireLock() {
    var planning = Table_Planning.getPlanning();

    var status = checkLockStatus();
    if (status === "other") {
        return false; // Cannot acquire, locked by another user
    }

    if (status === "unlocked") {
        try {
            planning.setLock(true);
            Application.showMessage(
                ApplicationMessageType.Info,
                "Data locked for editing"
            );
        } catch (error) {
            Application.showMessage(
                ApplicationMessageType.Error,
                "Failed to acquire lock: " + error.message
            );
            return false;
        }
    }

    return true;
}

// Release lock when done
function releaseLock() {
    var planning = Table_Planning.getPlanning();

    try {
        planning.setLock(false);
        Application.showMessage(
            ApplicationMessageType.Info,
            "Data lock released"
        );
    } catch (error) {
        console.error("Failed to release lock:", error);
    }
}

// Usage pattern
if (acquireLock()) {
    // Perform editing operations
    // ...

    // Release when done (or on page exit)
    releaseLock();
}
```

---

## Operation: workflow

```javascript
/*
 * Complete Planning Workflow
 * Purpose: Full planning cycle with status management
 */

// Workflow states
var WorkflowStatus = {
    NOT_STARTED: "not_started",
    IN_PROGRESS: "in_progress",
    SUBMITTED: "submitted",
    APPROVED: "approved",
    REJECTED: "rejected"
};

// Initialize planning session
function initializePlanningSession() {
    var planning = Table_Planning.getPlanning();

    // Create private version for editing
    var privateVersion = planning.getPrivateVersion();
    if (privateVersion === null) {
        planning.createPrivateVersion();
        planning.copyFromPublicVersion();
    }

    // Acquire lock
    planning.setLock(true);

    // Update status display
    updateStatusIndicator(WorkflowStatus.IN_PROGRESS);
}

// Submit for approval
function submitForApproval() {
    var planning = Table_Planning.getPlanning();

    // Validate data before submission
    if (!validatePlanningData()) {
        Application.showMessage(
            ApplicationMessageType.Error,
            "Please fix validation errors before submitting"
        );
        return;
    }

    Application.showBusyIndicator("Submitting...");

    try {
        // Publish data
        planning.publish();

        // Release lock
        planning.setLock(false);

        // Update status
        updateStatusIndicator(WorkflowStatus.SUBMITTED);

        Application.showMessage(
            ApplicationMessageType.Success,
            "Planning data submitted for approval"
        );

    } catch (error) {
        Application.showMessage(
            ApplicationMessageType.Error,
            "Submission failed: " + error.message
        );
    } finally {
        Application.hideBusyIndicator();
    }
}

// Validation function (customize as needed)
function validatePlanningData() {
    // Add your validation logic
    // Example: Check for zero values, required fields, etc.
    return true;
}

// Status indicator update
function updateStatusIndicator(status) {
    var statusText = {
        "not_started": "Not Started",
        "in_progress": "In Progress",
        "submitted": "Submitted for Approval",
        "approved": "Approved",
        "rejected": "Rejected - Revisions Needed"
    };

    Text_Status.setText(statusText[status] || status);

    // Update visual indicator color if needed
    // Panel_Status.setBackgroundColor(...);
}
```

---

## Operation: version

```javascript
/*
 * Version Management Operations
 * Purpose: Create, retrieve, and manage planning versions
 */

var planning = Table_Planning.getPlanning();

// Get current versions
function getVersionInfo() {
    var publicVersion = planning.getPublicVersion();
    var privateVersion = planning.getPrivateVersion();

    console.log("Public version:", publicVersion);
    console.log("Private version:", privateVersion);

    return {
        hasPublic: publicVersion !== null,
        hasPrivate: privateVersion !== null,
        publicVersion: publicVersion,
        privateVersion: privateVersion
    };
}

// Create private version
function createWorkingVersion() {
    var privateVersion = planning.getPrivateVersion();

    if (privateVersion !== null) {
        Application.showMessage(
            ApplicationMessageType.Info,
            "Private version already exists"
        );
        return;
    }

    planning.createPrivateVersion();
    Application.showMessage(
        ApplicationMessageType.Success,
        "Private version created"
    );
}

// Delete private version (discard changes)
function discardChanges() {
    var privateVersion = planning.getPrivateVersion();

    if (privateVersion === null) {
        Application.showMessage(
            ApplicationMessageType.Info,
            "No private version to discard"
        );
        return;
    }

    // Confirm with user
    // (In real implementation, use popup confirmation)

    planning.deletePrivateVersion();
    Application.showMessage(
        ApplicationMessageType.Info,
        "Changes discarded, private version deleted"
    );

    Table_Planning.getDataSource().refreshData();
}

// Switch version view
function switchToVersion(versionType) {
    var ds = Table_Planning.getDataSource();

    if (versionType === "private") {
        ds.setDimensionFilter("Version", planning.getPrivateVersion());
    } else {
        ds.setDimensionFilter("Version", planning.getPublicVersion());
    }
}
```

---

## Operation: dataaction

```javascript
/*
 * Data Action Integration
 * Purpose: Execute data actions from scripts
 */

// Basic data action execution
function executeDataAction() {
    var dataAction = DataAction_Allocate;

    Application.showBusyIndicator("Running data action...");

    // Set parameters (if data action has parameters)
    dataAction.setParameterValue("Period", "2025.Q1");
    dataAction.setParameterValue("Version", "Budget");
    dataAction.setParameterValue("Category", Dropdown_Category.getSelectedKey());

    // Execute data action
    dataAction.execute().then(function(result) {
        Application.hideBusyIndicator();

        if (result.success) {
            Application.showMessage(
                ApplicationMessageType.Success,
                "Data action completed successfully"
            );

            // Refresh affected widgets
            Table_Planning.getDataSource().refreshData();
            Chart_Summary.getDataSource().refreshData();
        } else {
            Application.showMessage(
                ApplicationMessageType.Error,
                "Data action failed: " + result.message
            );
        }
    }).catch(function(error) {
        Application.hideBusyIndicator();
        Application.showMessage(
            ApplicationMessageType.Error,
            "Data action error: " + error.message
        );
    });
}

// Data action with dynamic parameters from selections
function executeWithSelections() {
    var selections = Chart_1.getSelections();

    if (selections.length === 0) {
        Application.showMessage(
            ApplicationMessageType.Warning,
            "Please select data points first"
        );
        return;
    }

    var dataAction = DataAction_Process;

    // Set parameters from selection
    var selectedRegion = selections[0]["Region"];
    dataAction.setParameterValue("Region", selectedRegion);

    dataAction.execute().then(function(result) {
        if (result.success) {
            Table_1.getDataSource().refreshData();
        }
    });
}
```

---

Provide the appropriate planning template based on the requested operation. Customize widget names, dimension names, and business logic based on the specific use case.
