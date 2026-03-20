## 2024-05-30 - Fix Log Forging / Log Injection Vulnerability
**Vulnerability:** Several logs (`StickyHeaderComponent.kt` tracking mimeTypes, `PinnedFoldersSettings.kt` and `PathValidator.kt` tracking file paths) logged external or user inputs directly via String template injection without sanitizing CRLF `\n` and `\r` characters.
**Learning:** Even internal settings, file paths, and MIME types coming from system clipboards can be tampered with by an attacker to forge log entries or obscure traces.
**Prevention:** Always use the `PathValidator.sanitizeForLog()` utility function when passing external, user-supplied, or potentially tampered strings to `LOG.warn`, `LOG.error`, or `LOG.info`.

## 2024-05-31 - Fix Swing HTML Injection Vulnerability
**Vulnerability:** Swing components like `JLabel` and subclasses like `DefaultTableCellRenderer` parse strings starting with `<html>` as HTML, allowing arbitrary HTML execution (Swing XSS / HTML Injection).
**Learning:** Even when displaying non-web data in internal IDE settings like Table Renderers (e.g. for user-provided paths/descriptions in `StickyProjectConfigurable`), untrusted inputs can trigger HTML injection if not explicitly disabled.
**Prevention:** Always disable HTML rendering on display components handling user-controlled strings by calling `putClientProperty("html.disable", true)`.
