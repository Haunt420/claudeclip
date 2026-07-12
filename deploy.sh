#!/usr/bin/env bash

# --------------------------------------------
# Professional Grade Init & Push for Termux -> GitHub

# --------------------------------------------

set -euo pipefail

PROJECT_DIR="/data/data/com.termux/files/home/projects/claudeclip"
REPO_NAME="claudeclip"
GITHUB_USER="Haunt420"
REMOTE_URL="https://github.com/${GITHUB_USER}/${REPO_NAME}.git"

RED='\o033[0;31m'
GREEN='\o033[0;32m'
YELLOW='\o033[0;33m'
BLUE='\o033[0;34m'
NC='\o033[0m'

log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; >&2; }

cleanup() {
    local exit_code=$?
    if [ $exit_code -ne 0 ]; then
        log_error "Execution failed unexpectedly on line ${BASH_LINENO[0]} with exit code ${exit_code}."
    fi
    exit $exit_code
}
trap cleanup EXIT

log_info "Initializing deployment pipeline for ${REPO_NAME}..."

for cmd in git gh; do
    if ! command -v "$cmd" >/dev/null 2&1; then
        log_error "Missing dependency: '$cmd'. Please ensure it is installed and in PATH."
        exit 1
    fi
done

if [ ! -d "$PROJECT_DIR" ]; then
    log_error "Target directory does not exist: $PROJECT_DIR"
    exit 1
fi

cd "$PROJECT_DIR"
log_info "Context switched to $(pwd)"

if [ ! -d ".git" ]; then
    log_info "Initializing fresh Git repository..."
    git init -b main
else
    log_warn "Git repository already exists locally. Skipping initialization."
fi

log_info "Staging workspace files..."
git add .

if git diff --staged --quiet; then
    log_warn "Working directory clean. No new changes to commit."
else
    log_info "Committing application payload..."
    git commit -m "chore: initial scaffold and Actions CI prep for ${REPO_NAME}"
fi

log_info "Interrogating GitHub for existing remote repository..."
if gh repo view "${GITHUB_USER}/${REPO_NAME}" >/dev/null 2&1; then
    log_warn "Remote repository ${GITHUB_USER}/${REPO_NAME} already exits."
    
    if ! git remote get-url origin >/dev/null 2>&1; then
        git remote add origin "$REMOTE_URL"
        log_info "Attached existing GitHub remote as 'origin'."
    fi
    
    log_info "Pushing delta to remote..."
    git push -u origin main
else
    log_info "Provisioning new public repository on GitHub..."
    gh repo create "${GITHUB_USER}/${REPO_NAME}" --source=. --remote=origin --public --push
fi

log_success "Deployment automated successfully! Ready for Actions."
