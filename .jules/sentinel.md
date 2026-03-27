## 2024-05-30 - Fix Log Forging / Log Injection Vulnerability
**Vulnerability:** Several logs (`StickyHeaderComponent.kt` tracking mimeTypes, `PinnedFoldersSettings.kt` and `PathValidator.kt` tracking file paths) logged external or user inputs directly via String template injection without sanitizing CRLF `\n` and `\r` characters.
**Learning:** Even internal settings, file paths, and MIME types coming from system clipboards can be tampered with by an attacker to forge log entries or obscure traces.
**Prevention:** Always use the `PathValidator.sanitizeForLog()` utility function when passing external, user-supplied, or potentially tampered strings to `LOG.warn`, `LOG.error`, or `LOG.info`.
## 2024-05-30 - Fix UI XSS Vulnerability in JTable Renderer
**Vulnerability:** The `DefaultTableCellRenderer` used for the `pinnedTable` in `StickyProjectConfigurable.kt` did not disable HTML rendering, making it vulnerable to UI-based Cross-Site Scripting (XSS) if user input or external data starting with `<html>` was rendered.
**Learning:** Swing components like `JLabel` (and subclasses like `DefaultTableCellRenderer`) interpret strings starting with `<html>` as HTML by default. This can lead to potential HTML injection/XSS or arbitrary local file reads.
**Prevention:** When displaying user-provided or unvalidated strings (e.g., file/directory paths) in Swing components, explicitly disable HTML rendering by calling `.putClientProperty("html.disable", true)`. Use a safe cast `(comp as? javax.swing.JComponent)?.putClientProperty("html.disable", true)` to prevent `ClassCastException`.
