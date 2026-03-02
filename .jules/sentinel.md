## 2025-05-24 - Semicolon Injection in Path Settings
**Vulnerability:** Input validation missing for paths in `StickyProjectConfigurable` and `AutoCollapseActions`, allowing paths with semicolons to corrupt `StickyProjectSettings` which uses semicolon as delimiter.
**Learning:** Storing list data as delimited strings is fragile; always sanitize delimiters from input if using this pattern.
**Prevention:** Use `List<String>` for storage or robustly escape/validate delimiters on input.

## 2025-05-25 - Swing HTML Injection in JLabel
**Vulnerability:** A `JLabel` displaying unvalidated user input (like path/directory names) could render malicious HTML and execute BasicHTML logic.
**Learning:** Swing components like `JLabel` have HTML rendering enabled by default. If it displays input from untrusted sources (or settings files that can be tampered with), it leads to XSS/HTML injection.
**Prevention:** Always use `.putClientProperty("html.disable", true)` on JLabels when displaying raw strings or untrusted text.
