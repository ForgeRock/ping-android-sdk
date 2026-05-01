---
name: commit-conventions
description: Use when creating commits in this repo — describes the required commit message format
---

Commit message format: SDKS-XXXX Description (#PR_NUMBER)

- Start with the JIRA ticket ID: SDKS-XXXX (with or without colon after)
- Capitalize first word after the ticket ID
- Use imperative mood when possible (Add, Update, Fix)
- End with PR number in parentheses: (#NNN)

Examples:
  SDKS-4694: Update PasswordCollector to handle nested Password policies. (#190)
  SDKS-4216 Package Sizes Report (#185)

Optional category prefixes (Docs:, Test:, Fix:, Feat:, CI:) are acceptable.
Co-authored-by and other trailers go in the commit body, not the subject line.
