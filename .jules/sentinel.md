## 2025-05-24 - Semicolon Injection in Path Settings
**Vulnerability:** Input validation missing for paths in `StickyProjectConfigurable` and `AutoCollapseActions`, allowing paths with semicolons to corrupt `StickyProjectSettings` which uses semicolon as delimiter.
**Learning:** Storing list data as delimited strings is fragile; always sanitize delimiters from input if using this pattern.
**Prevention:** Use `List<String>` for storage or robustly escape/validate delimiters on input.

## 2025-05-24 - Partial Path Matching in String.startsWith
**Vulnerability:** Checking directory containment using  is vulnerable to partial path matching (e.g.,  matches the base path ).
**Learning:**  is not safe for evaluating directory paths because it doesn't respect directory boundaries (slashes).
**Prevention:** Always use proper path validation methods (like  or the project's ) that parse boundaries.

## 2025-05-24 - Partial Path Matching in String.startsWith
**Vulnerability:** Checking directory containment using `path.startsWith(basePath)` is vulnerable to partial path matching (e.g., `/home/user/project-secret` matches the base path `/home/user/project`).
**Learning:** `String.startsWith` is not safe for evaluating directory paths because it doesn't respect directory boundaries (slashes).
**Prevention:** Always use proper path validation methods (like `java.nio.file.Path.startsWith` or the project's `PathValidator.getValidatedRelativePath`) that parse boundaries.
