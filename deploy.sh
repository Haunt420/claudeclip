#!/usr/bin/env bash
set -euo pipefail
​PROJECT_DIR="/data/data/com.termux/files/home/projects/claudeclip"
REPO_NAME="claudeclip"
REMOTE_URL="https://github.com/Haunt420/claudeclip.git"
​RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m'
​log_info() { echo -e "{BLUE}[INFO]{NC} 1"; }
log_warn() { echo -e "{YELLOW}[WARN]{NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]{NC} 1"; }
log_error() { echo -e "{RED}[ERROR]${NC} $1"; >&2; }
​cleanup() {
local exit_code=$?
if [ $exit_code -ne 0 ]; then
log_error "Execution failed unexpectedly with exit code ${exit_code}."
fi
exit $exit_code
}
trap cleanup EXIT
​log_info "Deploying ${REPO_NAME} pipeline..."
​for cmd in git gh; do
if ! command -v "$cmd" &>/dev/null; then
log_error "Missing dependency: $cmd"
exit 1
fi
done
​cd "$PROJECT_DIR"
​if [ ! -d ".git" ]; then
log_info "Initializing Git repository..."
git init -b main
fi
​log_info "Staging workspace files..."
git add .
​if git diff --staged --quiet; then
log_warn "Working directory clean. No new changes to commit."
else
log_info "Committing application payload..."
git commit -m "chore: initial scaffold and Actions CI prep"
fi
​log_info "Interrogating GitHub..."
if gh repo view "Haunt420/${REPO_NAME}" &>/dev/null; then
log_warn "Remote repository already exists."
if ! git remote get-url origin &>/dev/null; then
git remote add origin "REMOTE_URL"
fi
log_info "Pushing delta to remote..."
git push -u origin main
else
log_info "Provisioning public repository on GitHub..."
gh repo create "{REPO_NAME}" --public --source=. --remote=origin --push
fi
​log_success "Deployment automated successfully!"
