## 2024-05-30 - Fix Log Forging / Log Injection Vulnerability
**Vulnerability:** Several logs (`StickyHeaderComponent.kt` tracking mimeTypes, `PinnedFoldersSettings.kt` and `PathValidator.kt` tracking file paths) logged external or user inputs directly via String template injection without sanitizing CRLF `\n` and `\r` characters.
**Learning:** Even internal settings, file paths, and MIME types coming from system clipboards can be tampered with by an attacker to forge log entries or obscure traces.
**Prevention:** Always use the `PathValidator.sanitizeForLog()` utility function when passing external, user-supplied, or potentially tampered strings to `LOG.warn`, `LOG.error`, or `LOG.info`.

## 2024-05-31 - Fix HTML Injection / XSS in Pinned Folders Table
**Vulnerability:** The custom `DefaultTableCellRenderer` in `StickyProjectConfigurable.kt` rendered user-editable strings (pinned folder descriptions) without disabling HTML rendering, creating an HTML Injection / XSS risk within the IDE's Swing UI.
**Learning:** Swing components like `JLabel`, `DefaultTableCellRenderer`, and `DefaultListCellRenderer` automatically parse strings starting with `<html>` as HTML. This can be exploited if they render untrusted or user-modifiable data.
**Prevention:** Always set `.putClientProperty("html.disable", true)` during the initialization of any custom cell renderer or label that displays potentially attacker-controlled or user-editable content.
