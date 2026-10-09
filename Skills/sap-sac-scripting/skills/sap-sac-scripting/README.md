# SAP SAC Scripting Skill

Portable SAP Analytics Cloud (SAC) scripting skill for AI coding assistants,
including Analytics Designer and Optimized Story Experience.

## Overview

This skill provides guidance, API references, and ready-to-use templates for developing interactive analytics applications and enhanced stories in SAP Analytics Cloud.

**Version**: 2.3.0
**SAC Version**: Q2 2026 (2026.8)
**OSE API Version**: 2025.20
**Last Updated**: 2026-06-16

## Capability Index

| Capability | Status |
|------------|--------|
| Commands | 4: `/sac-debug`, `/sac-optimize`, `/sac-planning`, `/sac-script-template` |
| Agents | 4: `sac-api-helper`, `sac-performance-optimizer`, `sac-planning-assistant`, `sac-script-debugger` |
| Hooks | Yes: `hooks/hooks.json` |
| MCP | Yes: `.mcp.json` |
| LSP | No |
| Source Freshness | `last_verified`: 2026-06-11; OSE API references are aligned to 2025.20 and oversized OSE references require search routing from SKILL.md. |
| Verification | `npm run validate`; live SAC story/runtime checks pending. |

## Installation

Install through the supported skills/plugin mechanism for your AI coding
assistant. In clients without native command, agent, hook, or MCP support, treat
those files as Markdown guidance, manual validators, and connection recipes.

For a direct local copy:

```bash
# Clone or download to your assistant's skills folder
cp -r sap-sac-scripting <skills-directory>/
```

## When to Use

This skill triggers when you're working with:

### Technologies & Platforms
- SAP Analytics Cloud (SAC)
- Analytics Designer
- Optimized Story Experience (OSE)
- Advanced Mode scripting
- SAC Planning
- SAC Analytics applications
- Analytic applications

### Development Tasks
- Creating interactive dashboards
- Building planning applications
- Writing event handler scripts
- Implementing widget interactions
- Managing data sources programmatically
- Applying dynamic filters
- Creating popups and dialogs

### Specific APIs & Objects
- Application object
- DataSource API
- Chart widget scripting
- Table widget scripting
- Planning API
- Input controls (Dropdown, ListBox, Slider)
- Global variables and script objects
- Data actions and multi-actions

### Common Operations
- onInitialization event
- onSelect event
- onResultChanged event
- getDataSource()
- setDimensionFilter()
- getMembers()
- getSelections()
- getPublicVersion()
- publish()
- showBusyIndicator()

## Skill Contents

### Main Files
- `SKILL.md` - Core skill instructions and quick reference
- `README.md` - This file (keywords and overview)

### Reference Files (65 files)

**Starter API routes**:
- `references/api-datasource.md` - Complete DataSource API (36+ methods)
- `references/api-widgets.md` - Chart, Table, Input Controls APIs
- `references/api-planning.md` - Planning API, version management
- `references/api-application.md` - Application object, utilities, events

**Advanced APIs**:
- `references/api-calendar-bookmarks.md` - Calendar integration, Bookmarks, Linked Analysis, Timer
- `references/api-advanced-widgets.md` - Containers, Layout API, R Viz, Custom Widgets, Navigation
- `references/api-data-operations.md` - Range/exclude filters, hierarchies, members, DataSource info

**Scripting Fundamentals**:
- `references/scripting-language-fundamentals.md` - Type system, variables, loops, arrays, built-in objects
- `references/debugging-browser-tools.md` - Console logging, browser debugging, breakpoints, R visualization debugging

**Best Practices**:
- `references/best-practices-developer.md` - Naming conventions, layout organization, script annotation
- `references/best-practices-planning-stories.md` - Multi-story architecture, entry point design, navigation scripting

### Templates (56 patterns)
- `templates/common-patterns.js` - 40 scripting patterns (filtering, loops, arrays, R viz, type conversion)
- `templates/planning-operations.js` - 16 planning-specific patterns

## Keywords

### Primary Keywords
- SAP Analytics Cloud
- SAC scripting
- Analytics Designer
- Optimized Story Experience
- SAC JavaScript
- analytic application
- SAC planning application
- SAC dashboard

### API Keywords
- DataSource API
- getDataSource
- setDimensionFilter
- removeDimensionFilter
- getDimensionFilters
- copyDimensionFilterFrom
- getMembers
- getMeasures
- getDimensions
- getResultSet
- getData
- refreshData
- setRefreshPaused

### Widget Keywords
- Chart scripting
- Table scripting
- addDimension
- addMeasure
- removeMember
- getSelections
- addDimensionToRows
- addDimensionToColumns
- Input control
- Dropdown
- ListBox
- Slider
- Button onClick
- Popup dialog

### Planning Keywords
- SAC planning
- getPlanning
- getPublicVersion
- getPrivateVersion
- publish
- isDirty
- copy version
- revert changes
- data locking
- DataLockingState
- PlanningCopyOption
- PlanningCategory
- submitData

### Application Keywords
- Application object
- showBusyIndicator
- hideBusyIndicator
- showMessage
- ApplicationMessageType
- getUserInfo
- getInfo
- getMode
- NavigationUtils
- Export PDF
- Export Excel

### Calendar Keywords
- Calendar integration
- CalendarCompositeTask
- getCalendarEventById
- getCurrentEvent
- createProcess
- createTask
- approve
- reject
- submit
- addReminder
- planning workflow
- calendar task

### Bookmark Keywords
- Bookmarks
- BookmarkSet
- saveBookmark
- apply bookmark
- getAppliedBookmark
- deleteBookmark
- global bookmark
- personal bookmark
- URL bookmarkId

### Container/Layout Keywords
- Panel widget
- TabStrip
- PageBook
- Flow Layout Panel
- container widgets
- Layout API
- setWidth
- setHeight
- setLeft
- setRight
- responsive layout
- getSelectedTab
- setSelectedPage

### Advanced Keywords
- Timer
- Timer API
- onTimeout
- Linked Analysis
- setFilters
- R Visualization
- Custom Widget
- Script Object
- Technical Object

### Event Keywords
- onInitialization
- onSelect
- onResultChanged
- onClick
- onResize
- onOrientationChange
- onOpen
- onClose
- event handler
- script object
- global variable

### Development Keywords
- console.log
- debugger statement
- debug mode
- performance optimization
- pause refresh
- batch filter
- URL parameters
- p_ prefix

### Error/Issue Keywords
- SAC script error
- scripting not working
- filter not applying
- selection handling
- data not refreshing
- version publish failed
- data locked

### Language Fundamentals Keywords
- SAC type system
- strong typing
- static typing
- toString()
- explicit type conversion
- no automatic type casting
- JavaScript subset
- strict mode
- var declaration
- for loop iterator
- while loop
- for-in loop
- switch statement
- break statement
- triple equals
- strict equality
- method chaining
- ArrayUtils
- 2D array
- two-dimensional array

### Filter Keywords
- exclude filter
- range filter
- setDimensionFilter
- getDimensionFilters
- FilterValueType
- SingleFilterValue
- MultipleFilterValue
- RangeFilterValue
- from to filter
- less than filter
- greater than filter

### Hierarchy Keywords
- setHierarchy
- setHierarchyLevel
- getHierarchyLevel
- expandNode
- collapseNode
- hierarchy manipulation

### Debugging Keywords
- browser debugging
- Chrome DevTools
- F12
- Sources tab
- sandbox.worker.main
- debug=true
- debugger statement
- breakpoints
- script names
- transformed scripts
- R environment
- getEnvironmentValues
- getInputParameters

### Pattern-Based Function Keywords
- pattern-based function
- training examples
- string transformation
- ML pattern matching

### Developer Best Practices Keywords
- SAC naming conventions
- widget naming prefixes
- SAC story organization
- maintainable SAC stories
- SAC code documentation
- SAC script comments
- panel organization
- responsive SAC layout
- SAC design guidelines
- story development best practices

### Planning Story Architecture Keywords
- planning story architecture
- multi-story planning
- planning entry point
- story navigation scripting
- NavigationUtils.openStory
- planning sidebar
- Guide Me popup
- planning user assistance
- step-by-step instructions
- button color coding
- planning folder structure

## Documentation Sources

- [Analytics Designer API Reference](https://help.sap.com/doc/958d4c11261f42e992e8d01a4c0dde25/release/en-US/index.html)
- [Optimized Story Experience API Reference 2025.20](https://help.sap.com/doc/1639cb9ccaa54b2592224df577abe822/release/en-US/index.html)
- [SAP Analytics Cloud Scripting Documentation](https://help.sap.com/docs/SAP_ANALYTICS_CLOUD/00f68c2e08b941f081002fd3691d86a7/6a4db9a9c8634bcb86cecbf1f1dbbf8e.html)
- [SAP Community - SAC](https://community.sap.com/topics/cloud-analytics)
- [Denis Reis Code Snippets](https://www.denisreis.com/sap-analytics-cloud-javascript-api-code-snippets/)
- [Building Stories That Other Developers Actually Want to Inherit](https://community.sap.com/t5/technology-blog-posts-by-members/building-stories-that-other-developers-actually-want-to-inherit/ba-p/14168133) (Best Practices)
- [Best Practices for Planning Stories in SAP Analytics Cloud](https://blog.sap-press.com/best-practices-for-planning-stories-in-sap-analytics-cloud) (SAP PRESS - Planning Architecture)

## Related Skills

- SAP SAPUI5 Development
- SAP BTP Development
- SAP Fiori Development

## License

GPL-3.0 License

## Maintainer

Eduard Jiglau | [hello@sap-ai-skills.com](mailto:hello@sap-ai-skills.com) | [sap-ai-skills.com](https://sap-ai-skills.com) | [https://github.com/secondsky/sap-skills](https://github.com/secondsky/sap-skills)

---

**Repository**: [https://github.com/secondsky/sap-skills](https://github.com/secondsky/sap-skills)
