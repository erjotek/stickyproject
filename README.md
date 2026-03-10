# Sticky Project Folder Plugin

<!-- Plugin description -->
Sticky Project Folder and auto collapse plugin for JetBrains IDEs.

Keeps parent directories visible at the top of the Project View when scrolling.

Includes **Pinned Folders**: pin important directories to a footer at the bottom of the Project View and jump to them with one click. You can also customize their order and descriptions in Settings.

Automatically collapses configured directories (e.g., `node_modules`, `vendor`, `build`, `dist`) in the Project View whenever you navigate to a file outside of them, keeping the tree clean and uncluttered.

### Features
- Sticky headers for directories in Project View
- Respects file color scopes (Background colors)
- Configurable limit for sticky headers (default: 10)
- Auto-collapse directories when selecting files outside configured paths
- Drag and drop support on sticky headers
- **Pinned folders**: Pin frequently-used directories to a footer at the bottom of the Project View for quick one-click navigation (per-project setting). Order and descriptions are configurable.

### Configuration
Go to **Settings/Preferences | Tools | Sticky Project Folder** to configure:
- Maximum number of sticky headers
- Enable/disable auto-collapse feature
- Add/remove directories for auto-collapse (e.g., `node_modules/`, `vendor/`) - global setting
- Auto-collapse excluded directories - per-project setting

### Preview
![Sticky scroll preview](https://raw.githubusercontent.com/erjotek/stickyproject/main/.github/readme/sticky-scroll.gif)

![Sticky collapse preview](https://raw.githubusercontent.com/erjotek/stickyproject/main/.github/readme/auto-collapse.gif)

![Pinned folders preview](https://raw.githubusercontent.com/erjotek/stickyproject/main/.github/readme/pinned-folders.gif)

<!-- Plugin description end -->

## Installation

- Using the IDE built-in plugin system:

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "stickyproject"</kbd> >
  <kbd>Install</kbd>

- Using JetBrains Marketplace:

  Go to [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/29884) and install it by clicking the <kbd>Install to ...</kbd> button in case your IDE is running.

  You can also download the [latest release](https://plugins.jetbrains.com/plugin/29884/versions) from JetBrains Marketplace and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

- Manually:

  Download the [latest release](https://github.com/erjotek/stickyproject/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>


---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
[docs:plugin-description]: https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html#plugin-description-and-presentation
