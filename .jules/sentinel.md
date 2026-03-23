## 2024-05-30 - Fix Log Forging / Log Injection Vulnerability
**Vulnerability:** Several logs (`StickyHeaderComponent.kt` tracking mimeTypes, `PinnedFoldersSettings.kt` and `PathValidator.kt` tracking file paths) logged external or user inputs directly via String template injection without sanitizing CRLF `\n` and `\r` characters.
**Learning:** Even internal settings, file paths, and MIME types coming from system clipboards can be tampered with by an attacker to forge log entries or obscure traces.
**Prevention:** Always use the `PathValidator.sanitizeForLog()` utility function when passing external, user-supplied, or potentially tampered strings to `LOG.warn`, `LOG.error`, or `LOG.info`.

## 2024-05-24 - HTML Injection in Swing Table Renderers
**Vulnerability:** A `DefaultTableCellRenderer` in `StickyProjectConfigurable` processed user-provided paths and descriptions without disabling HTML interpretation, leading to potential Cross-Site Scripting (XSS) / HTML Injection if malicious input like `<html>...` was used.
**Learning:** Swing components (like `JLabel` and subclasses like `DefaultTableCellRenderer`) default to parsing strings starting with `<html>` as HTML. This can be exploited to spoof UI elements or perform injection attacks when displaying unvalidated user input or external paths.
**Prevention:** Explicitly disable HTML rendering on Swing components displaying untrusted data by calling `(component as? JComponent)?.putClientProperty("html.disable", true)`.
