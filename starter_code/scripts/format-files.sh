#!/bin/bash

INPUT=$(cat)

TOOL_NAME=$(echo "$INPUT" | jq -r '.tool_name')

if [ "$TOOL_NAME" = "editFiles" ] || [ "$TOOL_NAME" = "createFile" ]; then

    echo "$INPUT" |
        jq -r '.tool_input.files[]? // .tool_input.path // empty' |
        while IFS= read -r FILE; do

            if [ ! -f "$FILE" ]; then
                continue
            fi

            case "$FILE" in
                *.java)
                    mvn spotless:apply \
                        -DspotlessFiles="$(realpath "$FILE")" \
                        -q
                    ;;

                *.js|*.jsx|*.ts|*.tsx|*.json|*.css|*.scss|*.md|*.yaml|*.yml)
                    npx prettier --write "$FILE" 2>/dev/null
                    ;;
            esac

        done
fi

echo '{"continue":true}'