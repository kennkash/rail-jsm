# RAIL Portal Plugin — Build & Release Script

This script automates the full release workflow for the RAIL Portal Plugin.

It performs:

- Git pull
- Maven version bump (with patch suggestion)
- Frontend build
- Backend build
- Upload of the JAR to GitLab Generic Package Registry
- Automatic CHANGELOG generation via git-cliff
- Creation of a single release commit
- Git tag creation
- Push to remote
- Creation of a GitLab Release with linked artifact

---

## 🚨 IMPORTANT WORKFLOW RULE

Before running this script, you must commit all feature, fix, or refactor changes manually.

This script is NOT intended to commit your development work.

It only creates a release commit that includes:
- pom.xml version bump
- CHANGELOG.md
- any release metadata files

### ✅ Correct Workflow

1. Make your code changes
2. Commit them with meaningful Conventional Commit messages
3. Repeat as needed
4. When ready to release → run this script

### ❌ Incorrect Workflow

- Making code changes
- Running this script
- Letting the script commit those changes

Your feature commits are what drive the changelog.

---

## Why You Must Commit Before Running

The changelog is generated using git-cliff and Conventional Commits.

Example commits made before release:

feat(nav): Redirect Refined Links  
fix(ui): Correct sidebar hover state  

When the script runs, it generates a changelog section for the new version based on commits since the previous tag.

If you do not commit first:
- Your changes may not appear in the changelog
- Your release history becomes unclear
- Version boundaries become messy

---

## What the Script Actually Commits

When the script runs, it creates exactly ONE release commit:

chore(release): prepare for vX.Y.Z

This commit includes:
- pom.xml version bump
- CHANGELOG.md update

That is intentional and correct.

---

## Requirements

You must have installed:

- git
- npm
- atlas-mvn
- git-cliff
- jq
- curl

The script checks for these before running.

---

## Authentication

You must export a GitLab token with API access:

export GL_DEPLOY_TOKEN="your_token_here"

The token must:
- Have API scope
- Have permission to create releases
- Have permission to upload packages

---

## Configuration

Inside the script:

PROJECT_ROOT  
FRONTEND_DIR  
BACKEND_DIR  
PROJECT_ID  
GITLAB_URL  

Adjust these if your environment differs.

---

## How to Run

1. Commit all your changes.
2. Push your branch to main (if required by your workflow).
3. Export your GitLab token.
4. Run:

bash ./build-and-deploy

The script will:
- Suggest a patch version bump
- Ask for confirmation
- Build frontend + backend
- Upload artifact
- Generate changelog
- Create release commit
- Tag the release
- Push changes
- Create GitLab Release entry

---

## What the Result Looks Like

### Git History

feat(nav): Redirect Refined Links  
fix(ui): Correct sidebar hover state  
chore(release): prepare for v1.4.0  

Tag: v1.4.0

---

### GitLab Release Page

Release v1.4.0

Features
- Redirect Refined Links

Bug Fixes
- Correct sidebar hover state

Artifact
- rail-portal-plugin-1.4.0.jar

---

## Best Practices

- Keep commit titles short and meaningful
- Use Conventional Commit format
- Do not squash feature commits into the release commit
- Do not manually edit CHANGELOG.md — let git-cliff generate it
- Only run the script when the branch is clean

You can check:

git status

It should show:
nothing to commit, working tree clean

before running the script.

---

## Safety Notes

The script:
- Pushes tags
- Pushes to main
- Uploads artifacts

Make sure:
- You are on the correct branch
- Your working directory is clean
- You are releasing the correct version

---

## Summary

Development commits → drive changelog  
Release commit → marks version boundary  
Tag → defines release  
GitLab Release → publishes artifact  

Commit first.  
Release second.  
Always.