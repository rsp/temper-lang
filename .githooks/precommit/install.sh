#!/bin/bash
# Installs precommit hooks under .git

set -e

export GIT_ROOT="$(git rev-parse --show-toplevel)"
export HOOK_FILE="$GIT_ROOT"/.git/hooks/pre-commit

(
    echo "#!/bin/bash"
    echo "set -e"
    for hook in "$GIT_ROOT"/.githooks/precommit/*.sh; do
        if [ "$(basename "$hook")" != "install.sh" ]; then
            echo echo "'" "$(basename "$hook")" "'"
            echo "$hook"
            echo echo
        fi
    done
) > "$HOOK_FILE"

chmod +x "$HOOK_FILE"
