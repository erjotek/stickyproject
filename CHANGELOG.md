<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Sticky Project Folder Changelog

## [Unreleased]

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
