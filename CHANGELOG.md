<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Sticky Project Folder Changelog

## [Unreleased]

### Fixed

- Sticky Editor lines: PHP `switch` and `match` blocks are now recognized correctly (previously not detected).
- Sticky Editor lines: PHP namespaces now show the full namespace path (e.g. `App\Classes\Fxiers\Actions`) instead of only the last segment Actions.

## [1.3.1] - 2026-07-14

### Changed

- **Performance** — large trees and frequent redraws do less repeated work.
- **Preparing for 2026.2** 

### Fixed

- Fixed a slow-operation-on-EDT warning during sticky rendering.
- Replaced the internal API call.

### Removed

- Removed duplicated Project View tree-resolution logic.

## [1.2.6] - 2026-05-11

### Added

- **Sticky Editor lines (experimental)** — extends the IDE's built-in sticky-lines / breadcrumbs feature beyond classes and functions to also stick **control-flow blocks**: `if` / `else if` / `else`, `for`, `foreach`, `while`, `do…while`, `switch` / `when`, `try` / `catch` / `finally`, and PHP / JS array and object literals. Supported languages: **Java, Kotlin, PHP, JavaScript / TypeScript, Python, C / C++**.
- New settings to toggle sticky lines for control blocks and for PHP/JS array & object literals independently.

### Changed

- Performance improvements across sticky rendering and PSI classification: cached element-kind lookups and reflection method handles, reducing work on large files and on every breadcrumbs/sticky-lines refresh.

### Removed

- Removed per-folder divider lines in the Project View sticky area for a cleaner, more compact look.
- Removed remaining deprecated IntelliJ Platform API usages and replaced them with the current recommended APIs.

## [1.0.3] - 2026-03-10

### Added

- Added **Pinned Folders**: a footer at the bottom of the Project View with one-click navigation to pinned directories.
- Added context menu actions for directories: **"Set as Pinned"** and **"Remove from Pinned"**.
- Pinned folders are stored per project.
- Pinned folder descriptions and order can be customized in Settings.

### Changed

- Reworked the configuration window for better clarity and workflow.
- Updated dependencies for compatibility with newer IntelliJ Platform builds.

### Fixed

- Improved path validation to better handle invalid and unsafe relative paths.
- Replaced deprecated API usage according to current IntelliJ Platform recommendations.

## [0.9.17] - 2026-02-25

### Added

- Added a new setting "Adjust sticky width for transparent scrollbar" (disabled by default) to prevent the scrollbar from partially covering sticky directories when "Always show scrollbar" is off in the OS.
- Sticky directories will now reliably disappear when scrolling to the absolute top of the tree, allowing access to the root folder's expand/collapse icon and context menu.

### Fixed

- Performance: optimized auto-collapse path handling (including cached excluded paths and faster tree-path search) to reduce work on large project trees.
- Path validation: hardened relative-path checks in settings and auto-collapse to block traversal outside the project root.
- Remove deprecated APIs and update the plugin to apply the latest Intellij Platform recommendations

## [0.9.12] - 2026-02-20

### Added

- Right-clicking a directory in the Project View now shows **"Add to Auto-Collapse"** or **"Remove from Auto-Collapse"** in the context menu, allowing quick management of the auto-collapse list without opening Settings.
- Redesigned settings page.

### Fixed

- Selected file position in the Project panel no longer shifts when auto-collapse occurs above it.

## [0.9.6] - 2026-01-28

### Added

- Works also in the Project Files tab and other project view tabs
- JetBrains IDE 2026.1 EAP support
- Added missing auto-scroll when dragging near the top edge of sticky area.

### Fixed

- Fixed exceptions on EDT during painting sticky rows 
- Improved mouse wheel scrolling speed over sticky area.

## [0.9.1] - 2026-01-23

### Added

- Added option to auto-collapse excluded folders with a read-only list of excluded paths.
- Colors in path selector:
    - gray color - path does not exist in this project,
    - orange color - other path includes this path

### Fixed

- Added mouse wheel scrolling support over sticky area.
- Fixed dependencies to ensure greater compatibility with various Jebtrains IDEs.

## [0.8.2] - 2026-01-20

### Fixed

- Fixed scrolling behavior when clicking sticky headers.

## [0.7.34] - 2026-01-12

### Added

- Added Drag & Drop support to move files into sticky directories.
- Added automatic directory collapsing (e.g., `node_modules`, `vendor`). Directories automatically collapse when switching to a file outside their tree (configurable in settings).

### Fixed

- Fixed flickering/disappearing sticky issue in large expanded trees.
- Fixed tree scrolling to ensure the selected file is visible if covered by a sticky element.
- Fixed `SlowOperation` error during background coloring.

## [0.6.7] - 2026-01-03

### Added

- Created sticky folder functionality.
- Added ability to set limits.
- Added clickable sticky elements.

[Unreleased]: https://github.com/erjotek/stickyproject/compare/v1.3.1...HEAD
[1.3.1]: https://github.com/erjotek/stickyproject/compare/v1.2.6...v1.3.1
[1.2.6]: https://github.com/erjotek/stickyproject/compare/v1.0.3...v1.2.6
[1.0.3]: https://github.com/erjotek/stickyproject/compare/v0.9.17...v1.0.3
[0.9.17]: https://github.com/erjotek/stickyproject/compare/v0.9.12...v0.9.17
[0.9.12]: https://github.com/erjotek/stickyproject/compare/v0.9.6...v0.9.12
[0.9.6]: https://github.com/erjotek/stickyproject/compare/v0.9.1...v0.9.6
[0.9.1]: https://github.com/erjotek/stickyproject/compare/v0.8.2...v0.9.1
[0.8.2]: https://github.com/erjotek/stickyproject/compare/v0.7.34...v0.8.2
[0.7.34]: https://github.com/erjotek/stickyproject/compare/v0.6.7...v0.7.34
[0.6.7]: https://github.com/erjotek/stickyproject/commits/v0.6.7
