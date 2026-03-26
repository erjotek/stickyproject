## 2024-05-30 - Fix Log Forging / Log Injection Vulnerability
**Vulnerability:** Several logs (`StickyHeaderComponent.kt` tracking mimeTypes, `PinnedFoldersSettings.kt` and `PathValidator.kt` tracking file paths) logged external or user inputs directly via String template injection without sanitizing CRLF `\n` and `\r` characters.
**Learning:** Even internal settings, file paths, and MIME types coming from system clipboards can be tampered with by an attacker to forge log entries or obscure traces.
**Prevention:** Always use the `PathValidator.sanitizeForLog()` utility function when passing external, user-supplied, or potentially tampered strings to `LOG.warn`, `LOG.error`, or `LOG.info`.
## 2026-03-26 - Fix Partial Path Matching Vulnerabilities
**Vulnerability:** Partial path matching vulnerabilities existed in AutoCollapseManager, PinnedFolderActions, and StickyProjectConfigurable where .startsWith() was used to check if a file path was within the project's base path. For example, /project-abc/malicious would incorrectly match /project base path.
**Learning:** Using .startsWith() for path validation is inherently insecure as it performs a string prefix match rather than a logical directory match.
**Prevention:** Always use secure path validation methods like PathValidator.getValidatedRelativePath() that leverage java.nio.file.Path normalization and logical path containment checks.
