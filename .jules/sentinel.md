## 2024-05-30 - Fix Log Forging / Log Injection Vulnerability
**Vulnerability:** Several logs (`StickyHeaderComponent.kt` tracking mimeTypes, `PinnedFoldersSettings.kt` and `PathValidator.kt` tracking file paths) logged external or user inputs directly via String template injection without sanitizing CRLF `\n` and `\r` characters.
**Learning:** Even internal settings, file paths, and MIME types coming from system clipboards can be tampered with by an attacker to forge log entries or obscure traces.
**Prevention:** Always use the `PathValidator.sanitizeForLog()` utility function when passing external, user-supplied, or potentially tampered strings to `LOG.warn`, `LOG.error`, or `LOG.info`.

## 2024-06-03 - [Fix HTML Injection in Pinned Folders Settings]
**Vulnerability:** A standard `DefaultTableCellRenderer` in Swing interprets strings starting with `<html>` as HTML, allowing for HTML injection / UI-based XSS when displaying user-controlled text like file descriptions.
**Learning:** We had already protected the list of auto-collapse paths with `putClientProperty("html.disable", true)`, but missed the Pinned Folders table because it uses a different renderer mechanism (`DefaultTableCellRenderer` vs `ListCellRenderer`). Any Swing component rendering user input needs this property.
**Prevention:** Always apply `.putClientProperty("html.disable", true)` to any `JLabel` or `JComponent` acting as a cell renderer in Swing applications when rendering potentially untrusted user data or file paths.
