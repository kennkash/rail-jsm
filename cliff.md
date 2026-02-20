-----------------------------------------------------------------------
ADD THIS SECTION TO YOUR MAIN README
-----------------------------------------------------------------------

## Changelog Generation (git-cliff)

This project uses git-cliff to automatically generate CHANGELOG.md
during each release.

Configuration file:
cliff.toml

The release script runs:

git-cliff --tag "vX.Y.Z" --output CHANGELOG.md

Important Rules:

- Conventional Commits are required.
- Only conventional commits are included in the changelog.
- Release commits (chore(release): prepare for vX.Y.Z) are skipped.
- Tags must follow format: vX.Y.Z.
- Commit prefixes determine changelog grouping.

Do NOT manually edit CHANGELOG.md.

If changelog formatting or grouping needs to change,
update cliff.toml instead.



-----------------------------------------------------------------------
DETAILED SECTION: HOW CHANGELOG GENERATION WORKS
-----------------------------------------------------------------------

## How Changelog Generation Works

Changelog entries are generated from commits made between Git tags.

The following settings are defined in cliff.toml:

- conventional_commits = true
- filter_unconventional = true
- tag_pattern = "v[0-9]*"

This means:

1. Only commits that follow Conventional Commit format are included.
2. Commits not matching defined patterns may be grouped under "Other".
3. Release commits are skipped:
   chore(release): prepare for vX.Y.Z
4. Tags must match the pattern vX.Y.Z.

Commit Type → Changelog Section Mapping

Based on commit_parsers:

- feat      → 🚀 Features
- fix       → 🐛 Bug Fixes
- doc       → 📚 Documentation
- perf      → ⚡ Performance
- refactor  → 🚜 Refactor
- style     → 🎨 Styling
- test      → 🧪 Testing
- chore     → ⚙️ Maintenance
- unmatched → ⭐ Other

Your commit title directly determines how it appears in the release notes.



-----------------------------------------------------------------------
COMMIT MESSAGE GUIDELINES
-----------------------------------------------------------------------

## Commit Message Guidelines

This repository uses the Conventional Commits standard.

Format:

type(scope): short description

Examples:

feat(nav): Redirect Refined Links
fix(ui): Correct sidebar hover state
refactor(backend): Extract request type service
docs(readme): Add release workflow documentation

Best Practices:

- Keep titles under 72 characters.
- Use lowercase type (feat, fix, chore, etc.).
- Use meaningful scopes (nav, ui, backend).
- Write the title as a user-facing summary.
- Use the commit body for implementation details.

Example with body:

feat(nav): Redirect Refined Links

Routes refined links through the new navigation handler.
Prevents direct navigation to legacy portal paths.

These messages directly power:

- CHANGELOG.md
- GitLab Release notes
- Version boundaries

If commit messages are vague or non-conventional,
the changelog will be incomplete or incorrectly grouped.



-----------------------------------------------------------------------
FINAL REMINDER TO DEVELOPERS
-----------------------------------------------------------------------

Always commit your feature and fix changes BEFORE running the release script.

The release script does NOT create feature commits.
It only:

- Bumps the version
- Generates the changelog
- Creates the release commit
- Tags the release
- Publishes the artifact

Development commits drive the changelog.
The release commit marks the version boundary.

Commit first.
Release second.
Always.
