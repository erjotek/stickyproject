## 2024-05-30 - Fix Log Forging / Log Injection Vulnerability
**Vulnerability:** Several logs (`StickyHeaderComponent.kt` tracking mimeTypes, `PinnedFoldersSettings.kt` and `PathValidator.kt` tracking file paths) logged external or user inputs directly via String template injection without sanitizing CRLF `\n` and `\r` characters.
**Learning:** Even internal settings, file paths, and MIME types coming from system clipboards can be tampered with by an attacker to forge log entries or obscure traces.
**Prevention:** Always use the `PathValidator.sanitizeForLog()` utility function when passing external, user-supplied, or potentially tampered strings to `LOG.warn`, `LOG.error`, or `LOG.info`.
## 2024-05-24 - Fix HTML injection in Pinned Folders settings table
**Vulnerability:** A custom `DefaultTableCellRenderer` in `StickyProjectConfigurable` was rendering user-provided paths and descriptions without disabling HTML rendering.
**Learning:** Swing components like `JLabel` interpret strings starting with `<html>` as HTML by default. When displaying user-provided strings in tables or lists, this can lead to HTML injection/XSS.
**Prevention:** Explicitly disable HTML rendering on the component returned by the renderer using `(comp as? javax.swing.JComponent)?.putClientProperty("html.disable", true)`.
