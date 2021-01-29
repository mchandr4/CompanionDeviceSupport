#!/bin/bash

# This script pulls the latest translations from the translation dump, copies
# them into the Companion Device res directory, and then generates a change
# list. This should be run before cutting a release and whenever the translation
# dump is updated.

set -e

# First things first: you need gcert
if ! /usr/bin/loas_check >/dev/null 2>&1; then
  echo "You need to run gcert first." >&2
  exit 2
fi

source gbash.sh || exit 1
GOOGLE3=${PWD%/google3*}/google3
APP_PATH=third_party/java_src/android_app/companiondevice
TRANSLATION_RESOURCES=java/com/google/android/companiondevicesupport
CURRENT_YEAR=$(date +"%Y")
COPYRIGHT="\<!--\n \
\ ~ Copyright (C) ${CURRENT_YEAR} The Android Open Source Project\n \
\ ~\n \
\ ~ Licensed under the Apache License, Version 2.0 (the \"License\");\n \
\ ~ you may not use this file except in compliance with the License.\n \
\ ~ You may obtain a copy of the License at\n \
\ ~\n \
\ ~      http://www.apache.org/licenses/LICENSE-2.0\n \
\ ~\n \
\ ~ Unless required by applicable law or agreed to in writing, software\n \
\ ~ distributed under the License is distributed on an \"AS IS\" BASIS,\n \
\ ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. \n \
\ ~ See the License for the specific language governing permissions and\n \
\ ~ limitations under the License.\n \
\ -->\n"

# Run blaze clean to make sure there's no stale blaze-genfiles output.
blaze clean

# Generate a fresh set of translations.
blaze build ${APP_PATH}/${TRANSLATION_RESOURCES}:translated_messages

# Copy the translated XML files to the app's res directory.
cp -rf ${GOOGLE3}/blaze-genfiles/${APP_PATH}/${TRANSLATION_RESOURCES}/res/values-* \
   ${GOOGLE3}/${APP_PATH}/${TRANSLATION_RESOURCES}/res/

# Add copyrights to translated strings files.
sed -i "1 a ${COPYRIGHT}" ${GOOGLE3}/${APP_PATH}/${TRANSLATION_RESOURCES}/res/values-*/strings.xml

# Remove any trailing white spaces so presubmit will pass. This does not affect the translation.
sed -i 's/[ \t]*$//' ${GOOGLE3}/${APP_PATH}/${TRANSLATION_RESOURCES}/res/values-*/strings.xml

# Generate changelist for latest translations.
g4 change --desc "Generated changelist to update app translations"
