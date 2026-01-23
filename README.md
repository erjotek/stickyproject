# Sticky Project Directories Plugin

<!-- Plugin description -->
Sticky Project Directories plugin for JetBrains IDEs.
Keeps parent directories visible at the top of the Project View when scrolling.

### Features
- Sticky headers for directories in Project View
- Respects file color scopes (Background colors)
- Configurable limit for sticky headers (default: 10)
- Auto-collapse directories when selecting files outside configured paths

### Configuration
Go to **Settings/Preferences | Tools | Sticky Project Directories** to configure:
- Maximum number of sticky headers
- Enable/disable auto-collapse feature
- Add/remove directories for auto-collapse (e.g., `node_modules/`, `vendor/`)
<!-- Plugin description end -->

## Installation

- Using the IDE built-in plugin system:

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "stickyproject"</kbd> >
  <kbd>Install</kbd>

- Using JetBrains Marketplace:

  Go to [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID) and install it by clicking the <kbd>Install to ...</kbd> button in case your IDE is running.

  You can also download the [latest release](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID/versions) from JetBrains Marketplace and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

- Manually:

  Download the [latest release](https://github.com/erjotek/stickyproject/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>


---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
[docs:plugin-description]: https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html#plugin-description-and-presentation
