# Sticky Project Folder Plugin

<!-- Plugin description -->
Sticky scrolling, auto-collapse and pinned folders for JetBrains IDEs.

The plugin keeps important context visible while you navigate code — both in the **Project View** and in the **Editor** — and helps keep the project tree clean while putting frequently-used directories one click away.

### Features

The plugin bundles **four independent features** that can be enabled and tuned separately:

1. **Sticky Project View** — keeps parent directories pinned at the top of the Project View as you scroll, so you always know where you are in the tree.
   - Respects file color scopes (background colors)
   - Configurable maximum number of sticky headers (default: 10)
   - Drag-and-drop support on sticky headers

2. **Sticky Editor lines (extended)** — extends the IDE's built-in sticky-lines / breadcrumbs feature beyond classes and functions to also stick **control-flow blocks**:
   - `if` / `else if` / `else`
   - `for`, `foreach`, `while`, `do…while`
   - `switch` / `when`
   - `try` / `catch` / `finally`
   - PHP / JS array and object literals
   
   Supported languages: **Java, Kotlin, PHP, JavaScript / TypeScript, Python, C / C++**. Sticky control blocks and array-scope sticking can be toggled independently in Settings.

3. **Auto-collapse directories** — automatically collapses configured directories (e.g. `node_modules`, `vendor`, `build`, `dist`) in the Project View whenever you navigate to a file outside of them, keeping the tree tidy without manual clean-up.
   - Global list of directories to collapse
   - Per-project excluded directories

4. **Pinned Folders** — pin frequently-used directories to a footer at the bottom of the Project View and jump to them with one click. Order and per-folder descriptions are customizable. Stored per project.

### Configuration
Go to **Settings/Preferences | Tools | Sticky Project Folder** to configure:
- Maximum number of sticky headers in the Project View
- Sticky lines for control blocks (`if` / `for` / `foreach` / `switch` / `while` / `try`, …)
- Sticky lines for PHP / JS array and object literals
- Enable / disable the auto-collapse feature
- Add / remove directories for auto-collapse (e.g. `node_modules/`, `vendor/`) — global setting
- Auto-collapse excluded directories — per-project setting
- Manage pinned folders, their order and descriptions — per-project setting

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
