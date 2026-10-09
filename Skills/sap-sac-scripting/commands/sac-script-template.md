---
name: sac-script-template
description: Generate SAC script templates for common Analytics Designer and Optimized Story Experience scenarios
allowed-tools:
  - Read
argument-hint: "<filter|planning|export|navigation|chart|table|dropdown|popup|timer|bookmark>"
arguments:
  - name: type
    description: "Template type: filter, planning, export, navigation, chart, table, dropdown, popup, timer, bookmark"
    required: true
---

## Shell Snippet Notes

- Shell snippets assume Bash on Linux/macOS, WSL2, or Git Bash.
- Install the command-specific tooling shown near each snippet before running it.
- Confirm before running commands that delete files, change ownership, deploy, or modify remote systems.

## Output Contract

Return a script template, required widget/model assumptions, API references, and validation notes. Default to generating text only unless the user explicitly asks to write files and confirms the target path.


Generate a SAC script template based on the requested type.

## Available Template Types

### filter
Cross-widget filtering pattern with selection handling and data source synchronization.

### planning
Planning operations template including version management, publish, and copy operations.

### export
PDF and PowerPoint export functionality with custom settings.

### navigation
Story-to-story navigation with URL parameters.

### chart
Chart manipulation including measure/dimension changes and selection handling.

### table
Table configuration with rows, columns, totals, and zero suppression.

### dropdown
Dropdown/Input Control handling with selection events.

### popup
Popup management with open/close and data passing.

### timer
Timer-based auto-refresh and scheduled operations.

### bookmark
Bookmark creation, application, and management.

---

## Template: filter

```javascript
/*
 * Cross-Widget Filter Pattern
 * Purpose: Apply selection from source widget to target widget
 * Trigger: onSelect event on source Chart/Table
 */

// Get selections from the source widget
var selections = Chart_Source.getSelections();

if (selections.length > 0) {
    // Extract the dimension value from selection
    var selectedValue = selections[0]["Location"]; // Adjust dimension name

    // Get target data source
    var targetDS = Table_Target.getDataSource();

    // Apply filter
    targetDS.setDimensionFilter("Location", selectedValue);

    // For multiple values
    // var values = selections.map(function(sel) { return sel["Location"]; });
    // targetDS.setDimensionFilter("Location", values);
}
```

---

## Template: planning

```javascript
/*
 * Planning Version Management Pattern
 * Purpose: Publish private version to public
 * Trigger: onClick event on Publish button
 */

var planning = Table_Planning.getPlanning();

Application.showBusyIndicator("Publishing data...");

try {
    // Check for private version
    var privateVersion = planning.getPrivateVersion();

    if (privateVersion !== null) {
        // Publish to public version
        planning.publish();

        Application.showMessage(
            ApplicationMessageType.Success,
            "Data published successfully"
        );

        // Refresh the view
        Table_Planning.getDataSource().refreshData();
    } else {
        Application.showMessage(
            ApplicationMessageType.Warning,
            "No private version to publish"
        );
    }
} catch (e) {
    Application.showMessage(
        ApplicationMessageType.Error,
        "Publish failed: " + e.message
    );
} finally {
    Application.hideBusyIndicator();
}
```

---

## Template: export

```javascript
/*
 * PDF Export Pattern
 * Purpose: Export current view to PDF
 * Trigger: onClick event on Export button
 */

// Configure export settings
var exportSettings = {
    fileName: "Report_" + new Date().toISOString().split('T')[0],
    paperSize: PaperSize.A4,
    orientation: Orientation.Landscape,
    includeFilters: true,
    fitToPage: true
};

// Execute PDF export
Application.exportToPDF(exportSettings);

// For PowerPoint export
// var pptSettings = {
//     fileName: "Presentation",
//     includeInputControls: false
// };
// Application.exportToPPT(pptSettings);
```

---

## Template: navigation

```javascript
/*
 * Story Navigation Pattern
 * Purpose: Navigate to another story with parameters
 * Trigger: onClick event on Navigation button
 */

// Create URL parameters
var urlParameters = ArrayUtils.create(Type.UrlParameter);
urlParameters.push(UrlParameter.create("page", "dashboard"));
urlParameters.push(UrlParameter.create("filter", Dropdown_1.getSelectedKey()));

// Navigate to target story
// Replace STORY_ID with actual story ID from URL
NavigationUtils.openStory(
    "STORY_ID_HERE",  // Target story ID
    "",               // Empty for same folder
    urlParameters,    // Parameters to pass
    false            // false = same tab, true = new tab
);
```

---

## Template: chart

```javascript
/*
 * Chart Manipulation Pattern
 * Purpose: Dynamically modify chart configuration
 * Trigger: onChange event on control or onClick on button
 */

// Add/Remove measures
Chart_1.addMeasure("Revenue", Feed.ValueAxis);
Chart_1.removeMeasure("Cost");

// Add/Remove dimensions
Chart_1.addDimension("Region", Feed.CategoryAxis);
Chart_1.removeDimension("Product");

// Get current selections
var selections = Chart_1.getSelections();
console.log("Selected:", JSON.stringify(selections));

// Handle selection in onSelect
if (selections.length > 0) {
    var selectedDimValue = selections[0]["Region"];
    // Do something with selection
}
```

---

## Template: table

```javascript
/*
 * Table Configuration Pattern
 * Purpose: Configure table display properties
 * Trigger: onInitialization or button click
 */

// Configure dimensions and measures
Table_1.addDimensionToRows("Location");
Table_1.addDimensionToRows("Product");
Table_1.addMeasure("Revenue");
Table_1.addMeasure("Quantity");

// Display settings
Table_1.setTotalsVisible(true);
Table_1.setZeroSuppressionEnabled(true);

// For planning tables, configure input
// Table_1.getPlanning().setInputEnabled(true);
```

---

## Template: dropdown

```javascript
/*
 * Dropdown/Input Control Pattern
 * Purpose: Handle dropdown selection changes
 * Trigger: onChange event on Dropdown
 */

// Get selected value
var selectedKey = Dropdown_1.getSelectedKey();
var selectedText = Dropdown_1.getSelectedText();

console.log("Selected:", selectedKey, selectedText);

// Apply as filter to other widgets
if (selectedKey !== "") {
    var ds = Chart_1.getDataSource();
    ds.setDimensionFilter("Category", selectedKey);
} else {
    // Clear filter if nothing selected
    Chart_1.getDataSource().removeDimensionFilter("Category");
}

// Programmatically set selection
// Dropdown_1.setSelectedKey("value");
```

---

## Template: popup

```javascript
/*
 * Popup Management Pattern
 * Purpose: Open/close popups with data context
 */

// --- In button onClick to OPEN popup ---
// Store context in script variable before opening
ScriptVariable_Context = JSON.stringify({
    selectedItem: Chart_1.getSelections()[0],
    timestamp: new Date().toISOString()
});

Application.openPopup(Popup_Details);

// --- In popup onOpen event ---
var context = JSON.parse(ScriptVariable_Context);
Text_PopupTitle.setText("Details for: " + context.selectedItem["Product"]);

// --- In popup Close button onClick ---
Application.closePopup(Popup_Details);
```

---

## Template: timer

```javascript
/*
 * Timer Auto-Refresh Pattern
 * Purpose: Periodically refresh data
 */

// --- In onInitialization (setup timer) ---
// Timer_Refresh should be added as Technical Object

// --- In Timer onTimeout event ---
// Refresh all data sources
Chart_1.getDataSource().refreshData();
Table_1.getDataSource().refreshData();

// Update timestamp display
Text_LastRefresh.setText("Last refresh: " + new Date().toLocaleTimeString());

// Timer automatically restarts based on interval setting
// To stop: Timer_Refresh.stop();
// To start: Timer_Refresh.start();
```

---

## Template: bookmark

```javascript
/*
 * Bookmark Management Pattern
 * Purpose: Save and apply user bookmarks
 */

// Save current state as bookmark
var bookmarkName = InputField_BookmarkName.getValue();
if (bookmarkName !== "") {
    Bookmarks.createBookmark(bookmarkName);
    Application.showMessage(
        ApplicationMessageType.Success,
        "Bookmark '" + bookmarkName + "' saved"
    );
}

// Get all bookmarks
var allBookmarks = Bookmarks.getAllBookmarks();
console.log("Available bookmarks:", allBookmarks.length);

// Apply a bookmark
// Bookmarks.applyBookmark(bookmarkId);

// Delete a bookmark
// Bookmarks.deleteBookmark(bookmarkId);
```

---

Provide the appropriate template based on the user's requested type. Customize variable names and dimension/measure names based on their specific use case.
