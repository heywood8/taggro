# Claude Instructions

## General
- Never use command substitution (`$(...)`) in any shell commands.
- Never use compound commands or pipes: no `&&`, `||`, `;`, or `|`. Run each command separately.

## Git Commits
- Pass commit messages directly with `-m "..."`. Never use heredoc or command substitution.
