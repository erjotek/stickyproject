## 2025-05-24 - Semicolon Injection in Path Settings
**Vulnerability:** Input validation missing for paths in `StickyProjectConfigurable` and `AutoCollapseActions`, allowing paths with semicolons to corrupt `StickyProjectSettings` which uses semicolon as delimiter.
**Learning:** Storing list data as delimited strings is fragile; always sanitize delimiters from input if using this pattern.
**Prevention:** Use `List<String>` for storage or robustly escape/validate delimiters on input.
