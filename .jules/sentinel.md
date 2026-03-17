## 2024-05-30 - Fix Log Forging / Log Injection Vulnerability
**Vulnerability:** Several logs (`StickyHeaderComponent.kt` tracking mimeTypes, `PinnedFoldersSettings.kt` and `PathValidator.kt` tracking file paths) logged external or user inputs directly via String template injection without sanitizing CRLF `\n` and `\r` characters.
**Learning:** Even internal settings, file paths, and MIME types coming from system clipboards can be tampered with by an attacker to forge log entries or obscure traces.
**Prevention:** Always use the `PathValidator.sanitizeForLog()` utility function when passing external, user-supplied, or potentially tampered strings to `LOG.warn`, `LOG.error`, or `LOG.info`.

## $(date +%Y-%m-%d) - Prevent XSS in DefaultTableCellRenderer
**Vulnerability:** HTML Injection / XSS in Swing `DefaultTableCellRenderer` used in Settings UI for paths.
**Learning:** Default implementations of Swing renderers like `DefaultTableCellRenderer` inherit from `JLabel` and parse strings starting with `<html>` as HTML, allowing for UI manipulation or malicious execution if untrusted input is displayed.
**Prevention:** Explicitly set the client property `(comp as? javax.swing.JComponent)?.putClientProperty("html.disable", true)` on the returned component within `getTableCellRendererComponent` when displaying user input or file paths.
