## 2025-05-24 - Semicolon Injection in Path Settings
**Vulnerability:** Input validation missing for paths in `StickyProjectConfigurable` and `AutoCollapseActions`, allowing paths with semicolons to corrupt `StickyProjectSettings` which uses semicolon as delimiter.
**Learning:** Storing list data as delimited strings is fragile; always sanitize delimiters from input if using this pattern.
**Prevention:** Use `List<String>` for storage or robustly escape/validate delimiters on input.

## 2025-05-24 - Partial Path Match Traversal
**Vulnerability:** `StickyProjectConfigurable` used `String.startsWith()` to check if a selected path is inside the project base path, which incorrectly allows sibling directories with matching prefixes (e.g., `/app/secret` matches prefix `/app`).
**Learning:** String-based path checks are notoriously brittle and often miss edge cases like partial matches or separators.
**Prevention:** Always use `java.nio.file.Path` API (specifically `startsWith` on `Path` objects, after normalization) to validate directory containment.
