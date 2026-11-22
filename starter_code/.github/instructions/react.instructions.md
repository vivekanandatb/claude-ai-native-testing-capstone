---
name: "React Component Standards"
description: "Coding conventions for React component files"
applyTo: "**.jsx, **/*.tsx"
---

- Use function components (no class components) and React hooks for state and lifecycle.
- Prefer function expressions with `const` and arrow functions: `const X = (props) => {}`.
- Prefer named exports for components; use `export default` only for small, single-file utilities.
- Keep components small and focused: aim for < 150 lines and a single responsibility.
- Follow file/folder naming: component files use PascalCase (e.g., `CourseCard.jsx`) and are colocated with their styles and tests in the same folder.
- CSS/scoped styles: prefer CSS modules or component-scoped styles; avoid global stylesheet mutations where possible.
- Accessibility: always include semantic elements and ARIA attributes when required. Ensure interactive elements have keyboard support and labels.
