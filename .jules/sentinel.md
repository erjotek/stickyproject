## 2024-05-30 - Fix Log Forging / Log Injection Vulnerability
**Vulnerability:** Several logs (`StickyHeaderComponent.kt` tracking mimeTypes, `PinnedFoldersSettings.kt` and `PathValidator.kt` tracking file paths) logged external or user inputs directly via String template injection without sanitizing CRLF `\n` and `\r` characters.
**Learning:** Even internal settings, file paths, and MIME types coming from system clipboards can be tampered with by an attacker to forge log entries or obscure traces.
**Prevention:** Always use the `PathValidator.sanitizeForLog()` utility function when passing external, user-supplied, or potentially tampered strings to `LOG.warn`, `LOG.error`, or `LOG.info`.

## 2024-05-31 - Fix HTML Injection via Swing Cell Renderers
**Vulnerability:** User-provided inputs (like file paths or descriptions) displayed in Swing components (like `DefaultTableCellRenderer` for `JTable`) were not sanitizing HTML. Swing components like `JLabel` interpret strings starting with `<html>` as HTML by default, leading to potential HTML injection/XSS.
**Learning:** Default cell renderers and JLabels in Swing can execute embedded HTML if not explicitly disabled.
**Prevention:** Always disable HTML rendering by calling `(component as? javax.swing.JComponent)?.putClientProperty("html.disable", true)` when displaying external or unvalidated strings in custom Swing renderers.
