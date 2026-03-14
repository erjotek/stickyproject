## 2024-05-30 - Fix Log Forging / Log Injection Vulnerability
**Vulnerability:** Several logs (`StickyHeaderComponent.kt` tracking mimeTypes, `PinnedFoldersSettings.kt` and `PathValidator.kt` tracking file paths) logged external or user inputs directly via String template injection without sanitizing CRLF `\n` and `\r` characters.
**Learning:** Even internal settings, file paths, and MIME types coming from system clipboards can be tampered with by an attacker to forge log entries or obscure traces.
**Prevention:** Always use the `PathValidator.sanitizeForLog()` utility function when passing external, user-supplied, or potentially tampered strings to `LOG.warn`, `LOG.error`, or `LOG.info`.
## 2024-05-24 - [Disable HTML rendering in Swing TableCellRenderer]
**Vulnerability:** Swing components like `JLabel` (used by `DefaultTableCellRenderer`) interpret strings starting with `<html>` as HTML, which can lead to HTML injection/XSS if a user provides a malicious path or description.
**Learning:** Swing components render HTML by default, which is a security risk when displaying unvalidated user input.
**Prevention:** Always explicitly disable HTML rendering by calling `.putClientProperty("html.disable", true)` on Swing components displaying user-provided strings.
