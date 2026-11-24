#!/bin/sh
set -eu

cd "$SRCROOT/../.."
if [ "${OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED:-}" != YES ]; then
    ./gradlew -p companion/core -PmiqatApple=true embedAndSignAppleFrameworkForXcode
fi
node companion/ios/stage-resources.mjs "$TARGET_BUILD_DIR/$UNLOCALIZED_RESOURCES_FOLDER_PATH"
