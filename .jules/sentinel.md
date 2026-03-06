## 2025-05-24 - Semicolon Injection in Path Settings
**Vulnerability:** Input validation missing for paths in `StickyProjectConfigurable` and `AutoCollapseActions`, allowing paths with semicolons to corrupt `StickyProjectSettings` which uses semicolon as delimiter.
**Learning:** Storing list data as delimited strings is fragile; always sanitize delimiters from input if using this pattern.
**Prevention:** Use `List<String>` for storage or robustly escape/validate delimiters on input.
## 2025-05-24 - Duplicate/Vulnerable PathValidator
**Vulnerability:** A duplicate vulnerable class `PathValidator.kt` existed in the `settings` package, using simple string concatenation and `Path.startsWith` logic that wasn't as robust against Path Traversal as the centralized utility.
**Learning:** Having multiple utility classes doing the same thing leads to inconsistencies and potential vulnerabilities where outdated or incorrect logic is used.
**Prevention:** Centralize security utilities like `PathValidator` in a single location (`util` package) and ensure all call sites use the secure version. Delete deprecated or redundant vulnerable classes.
