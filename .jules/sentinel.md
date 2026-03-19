## 2024-05-30 - Fix Log Forging / Log Injection Vulnerability
**Vulnerability:** Several logs (`StickyHeaderComponent.kt` tracking mimeTypes, `PinnedFoldersSettings.kt` and `PathValidator.kt` tracking file paths) logged external or user inputs directly via String template injection without sanitizing CRLF `\n` and `\r` characters.
**Learning:** Even internal settings, file paths, and MIME types coming from system clipboards can be tampered with by an attacker to forge log entries or obscure traces.
**Prevention:** Always use the `PathValidator.sanitizeForLog()` utility function when passing external, user-supplied, or potentially tampered strings to `LOG.warn`, `LOG.error`, or `LOG.info`.

## 2025-02-23 - Prevent HTML Injection in Swing Table Renderers
**Vulnerability:** The `DefaultTableCellRenderer` for `pinnedTable` rendered raw strings (folder paths and descriptions) without disabling HTML interpretation, exposing a potential HTML injection/XSS vulnerability if a user enters a description like `<html>...`.
**Learning:** Swing components (like `JLabel` and table renderers based on it) interpret strings starting with `<html>` by default. Even local settings inputs can be used for UI redressing or HTML injection.
**Prevention:** Explicitly disable HTML rendering by calling `.putClientProperty("html.disable", true)` on the `JComponent` returned by a custom cell renderer when displaying unvalidated or user-provided strings.
