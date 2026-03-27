# Step 6: Validate unreleased commits before changelog/tagging
print_step "Step 6/10: Validating unreleased commits with git-cliff..."

CLIFF_LOG=$(mktemp)

npx git-cliff -vv --unreleased > /dev/null 2>"${CLIFF_LOG}"
CLIFF_STATUS=$?

if [ "${CLIFF_STATUS}" -ne 0 ] || grep -Eqi "skipped due to parse error|did not match conventional format|incorrect body syntax" "${CLIFF_LOG}"; then
    echo ""
    echo -e "${RED}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    print_error "Release blocked: invalid conventional commit message detected."
    echo -e "${YELLOW}What to do:${NC}"
    echo "  1. Look at the commit shown below"
    echo "  2. Reword it so the subject/body follow conventional commit format"
    echo "  3. Ensure there is a blank line between subject and body"
    echo "  4. Rerun this release script"
    echo ""
    echo -e "${RED}git-cliff details:${NC}"
    cat "${CLIFF_LOG}"
    echo -e "${RED}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    rm -f "${CLIFF_LOG}"
    exit 1
fi

rm -f "${CLIFF_LOG}"



Why am I getting the git_cliff error for this commit message and this build script?: 

'''fix(ui): update request icon urls

-Updated the request icon URLs to the "public" customer facing URLs
-The icon URLs currently used do not render for users without a license
'''

#!/bin/bash
# RAIL Portal Plugin Build and Deploy Script
# This script automates the complete build and deployment workflow:
# - Pull latest
# - Bump Maven version (patch suggestion)
# - Build frontend + backend
# - Upload artifact to GitLab Generic Package Registry
# - Generate CHANGELOG.md via git-cliff
# - Create ONE release commit (pom.xml + changelog, and any other staged changes)
# - Tag + push
# - Create GitLab Release with asset link (JSON-safe via jq)

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

print_step() { echo -e "${GREEN}==>${NC} $1"; }
print_error() { echo -e "${RED}ERROR:${NC} $1"; }
print_warning() { echo -e "${YELLOW}WARNING:${NC} $1"; }

# --- Step 0: Dependency Check ---
check_dependencies() {
    local dependencies=("npm" "atlas-mvn" "git-cliff" "curl" "git" "awk" "cut" "tr" "jq")
    for cmd in "${dependencies[@]}"; do
        if ! command -v "$cmd" &> /dev/null; then
            print_error "Dependency '$cmd' is not installed. Please install it to proceed."
            exit 1
        fi
    done
}

# --- Configuration ---
PROJECT_ROOT="/mnt/${USER}/git/rail-at-sas"
FRONTEND_DIR="${PROJECT_ROOT}/frontend"
BACKEND_DIR="${PROJECT_ROOT}/backend"
PROJECT_ID="7429"
TOKEN="${GL_DEPLOY_TOKEN}"
GITLAB_URL="https://gitlab.smartcloud.samsungds.net"

# Start Execution
check_dependencies

# Step 1: Git Pull
print_step "Step 1/9: Pulling latest changes from git..."
cd "${PROJECT_ROOT}"
git pull || {
    print_error "Git pull failed!"
    exit 1
}

# Step 2: Version Management
print_step "Step 2/9: Version Validation..."

# 1. Fetch version and strip any 'Executing...' noise or whitespace
RAW_VERSION=$(cd "${BACKEND_DIR}" && atlas-mvn help:evaluate -Dexpression=project.version -q -DforceStdout | grep -E '^[0-9]' | tail -n 1 | tr -d '[:space:]')

# 2. Safety check: If RAW_VERSION is empty, fall back to a safe default
CURRENT_VERSION=${RAW_VERSION:-"1.0.0"}

# 3. Increment the patch version (the robust way)
BASE_VERSION=$(echo "$CURRENT_VERSION" | cut -d. -f1-2)
PATCH_VERSION=$(echo "$CURRENT_VERSION" | cut -d. -f3)
SUGGESTED_VERSION="${BASE_VERSION}.$((PATCH_VERSION + 1))"

echo -e "${YELLOW}Current POM version: ${CURRENT_VERSION}${NC}"
read -p "Enter version for this release (Default suggestion: ${SUGGESTED_VERSION}): " NEW_VERSION

VERSION=${NEW_VERSION:-$SUGGESTED_VERSION}

# BLOCKER: Check GitLab Registry for version uniqueness
print_step "Verifying version uniqueness..."
PACKAGE_EXISTS=$(curl -s --header "PRIVATE-TOKEN: ${TOKEN}" \
    "${GITLAB_URL}/api/v4/projects/${PROJECT_ID}/packages?package_version=${VERSION}" | grep -c "version\":\"${VERSION}\"" || true)

if [ "$PACKAGE_EXISTS" -gt 0 ]; then
    print_error "Version ${VERSION} already exists in GitLab Registry! Aborting build."
    exit 1
fi

# Apply version to POM
cd "${BACKEND_DIR}"
atlas-mvn versions:set -DnewVersion="$VERSION" -DgenerateBackupPoms=false -q

# Step 3: Frontend Build
print_step "Step 3/9: Building frontend..."
cd "${FRONTEND_DIR}"
if [ ! -d "node_modules" ]; then
    print_warning "node_modules not found. Installing dependencies..."
    npm install
fi
npm run build:plugin || {
    print_error "Frontend build failed!"
    exit 1
}

# Step 4: Backend Build
print_step "Step 4/9: Building backend with atlas-mvn..."
cd "${BACKEND_DIR}"
atlas-mvn clean package -DskipTests || {
    print_error "Backend build failed!"
    exit 1
}

# Step 5: Upload artifact to GitLab Package Registry
print_step "Step 5/9: Uploading to GitLab Registry..."
cd "${BACKEND_DIR}/target"
FINAL_JAR="rail-portal-plugin-${VERSION}.jar"

if [ ! -f "${FINAL_JAR}" ]; then
    print_error "JAR file not found: ${FINAL_JAR}"
    exit 1
fi

PACKAGE_URL="${GITLAB_URL}/api/v4/projects/${PROJECT_ID}/packages/generic/rail-portal/${VERSION}/${FINAL_JAR}"

curl --fail --header "PRIVATE-TOKEN: ${TOKEN}" \
    --upload-file "${FINAL_JAR}" \
    "${PACKAGE_URL}"

echo ""

# Step 6: Single release commit (version bump + changelog)
print_step "Step 6/9: Committing Release Metadata & Generating Changelog..."
cd "${PROJECT_ROOT}"

# Generate changelog for this release tag (based on conventional commits)
npx git-cliff --tag "v${VERSION}" --output CHANGELOG.md

# Stage release metadata changes:
# - pom.xml (already modified by versions:set)
# - CHANGELOG.md (just generated)
# - any other changes present (optional; keeps things consistent if you also adjust a frontend file)
if [[ -n $(git status -s) ]]; then
    git add .
    git commit -m "chore(release): prepare for v${VERSION}"
else
    # Rare, but allows tagging/releasing even if nothing changed
    git commit --allow-empty -m "chore(release): prepare for v${VERSION}"
fi

# Step 7: Tagging
print_step "Step 7/9: Finalizing Git Tag..."
git tag -a "v${VERSION}" -m "Release v${VERSION}"

# Step 8: Final Push
print_step "Step 8/9: Pushing code and tags..."
git push origin main --tags -f

# Step 9: Create GitLab Release Entry with Assets
print_step "Step 9/9: Creating Official GitLab Release..."

# Release notes: latest section generated by git-cliff (markdown)
NOTES="$(npx git-cliff --latest --strip all)"

payload="$(jq -n \
    --arg name "Release ${VERSION}" \
    --arg tag  "v${VERSION}" \
     --arg desc "${NOTES}" \
    --arg asset_name "${FINAL_JAR}" \
    --arg asset_url  "${PACKAGE_URL}" \
    '{
        name: $name,
        tag_name: $tag,
        description: $desc,
        assets: { links: [ { name: $asset_name, url: $asset_url, link_type: "package" } ] }
    }'
)"

curl --fail-with-body -sS -o /dev/null \
    --header "Content-Type: application/json" \
    --header "PRIVATE-TOKEN: ${TOKEN}" \
    --data "${payload}" \
    --request POST "${GITLAB_URL}/api/v4/projects/${PROJECT_ID}/releases"

# Final Success Message
echo -e "\n${GREEN}─────────────────────────────────────────────────────────────────${NC}"
echo -e "${GREEN}✓ Release ${VERSION} is complete!${NC}"
echo -e "${YELLOW}Artifact URL:${NC} ${GITLAB_URL}/digitalsolutions/knowledge/rail-at-sas/-/packages"
echo -e "${YELLOW}Release Page:${NC} ${GITLAB_URL}/digitalsolutions/knowledge/rail-at-sas/-/releases"
echo -e "${YELLOW}Tag:${NC} v${VERSION}"
echo -e "${GREEN}─────────────────────────────────────────────────────────────────${NC}\n"


[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  18.481 s
[INFO] Finished at: 2026-03-27T14:38:51-05:00
[INFO] ------------------------------------------------------------------------
==> Step 5/9: Uploading to GitLab Registry...
{"message":"201 Created"}
==> Step 6/9: Committing Release Metadata & Generating Changelog...
 WARN  git_cliff_core::changelog > 35 commit(s) were skipped due to parse error(s) (run with `-vv` for details)
[main 9bf4aad] chore(release): prepare for v2.0.19
 3 files changed, 236 insertions(+), 234 deletions(-)
==> Step 7/9: Finalizing Git Tag...
==> Step 8/9: Pushing code and tags...
Enumerating objects: 30, done.
Counting objects: 100% (30/30), done.
Delta compression using up to 96 threads
Compressing objects: 100% (16/16), done.
Writing objects: 100% (17/17), 68.88 KiB | 230.00 KiB/s, done.
Total 17 (delta 11), reused 0 (delta 0), pack-reused 0
To gitlab.smartcloud.samsungds.net:digitalsolutions/knowledge/rail-at-sas.git
   5513bbe..9bf4aad  main -> main
 * [new tag]         v2.0.19 -> v2.0.19
==> Step 9/9: Creating Official GitLab Release...
 WARN  git_cliff_core::changelog > 1 commit(s) were skipped due to parse error(s) (run with `-vv` for details)

─────────────────────────────────────────────────────────────────
✓ Release 2.0.19 is complete!
Artifact URL: https://gitlab.smartcloud.samsungds.net/digitalsolutions/knowledge/rail-at-sas/-/packages
Release Page: https://gitlab.smartcloud.samsungds.net/digitalsolutions/knowledge/rail-at-sas/-/releases
Tag: v2.0.19
─────────────────────────────────────────────────────────────────


