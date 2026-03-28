## 2024-05-30 - Fix Log Forging / Log Injection Vulnerability
**Vulnerability:** Several logs (`StickyHeaderComponent.kt` tracking mimeTypes, `PinnedFoldersSettings.kt` and `PathValidator.kt` tracking file paths) logged external or user inputs directly via String template injection without sanitizing CRLF `\n` and `\r` characters.
**Learning:** Even internal settings, file paths, and MIME types coming from system clipboards can be tampered with by an attacker to forge log entries or obscure traces.
**Prevention:** Always use the `PathValidator.sanitizeForLog()` utility function when passing external, user-supplied, or potentially tampered strings to `LOG.warn`, `LOG.error`, or `LOG.info`.

## 2024-05-30 - Fix HTML Injection / XSS in Swing Tables
**Vulnerability:** In `StickyProjectConfigurable.kt`, user-editable strings (descriptions for Pinned Folders) were rendered using `DefaultTableCellRenderer`. By default, Swing components interpret strings starting with `<html>` as HTML, potentially allowing HTML injection or Cross-Site Scripting (XSS) within the IDE settings UI.
**Learning:** Default Swing renderers do not sanitize inputs. Even in a desktop application context, allowing arbitrary HTML execution from settings could lead to UI spoofing or executing unintended actions if the HTML payload is complex enough.
**Prevention:** Always explicitly disable HTML rendering by calling `.putClientProperty("html.disable", true)` on Swing components used to display user-provided or unvalidated strings, especially in custom `ListCellRenderer` or `TableCellRenderer` implementations.
