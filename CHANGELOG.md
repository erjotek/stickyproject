<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Sticky Project Folder Changelog

## [Unreleased]

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
