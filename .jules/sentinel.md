## 2024-05-30 - Fix Log Forging / Log Injection Vulnerability
**Vulnerability:** Several logs (`StickyHeaderComponent.kt` tracking mimeTypes, `PinnedFoldersSettings.kt` and `PathValidator.kt` tracking file paths) logged external or user inputs directly via String template injection without sanitizing CRLF `\n` and `\r` characters.
**Learning:** Even internal settings, file paths, and MIME types coming from system clipboards can be tampered with by an attacker to forge log entries or obscure traces.
**Prevention:** Always use the `PathValidator.sanitizeForLog()` utility function when passing external, user-supplied, or potentially tampered strings to `LOG.warn`, `LOG.error`, or `LOG.info`.

## 2024-05-31 - Fix HTML Injection Vulnerability
**Vulnerability:** Swing components like `DefaultTableCellRenderer` (which extends `JLabel`) interpret strings starting with `<html>` as HTML by default. In `StickyProjectConfigurable.kt`, the renderer for the Pinned Folders table displayed user-editable descriptions and file paths without disabling this behavior.
**Learning:** Even internal settings like file paths or user-provided descriptions can be abused for UI redressing, spoofing, or hanging the UI if an attacker manages to inject `<html>` tags into these fields.
**Prevention:** Always explicitly disable HTML rendering on Swing components displaying user-provided or unvalidated strings by calling `putClientProperty("html.disable", true)`.
