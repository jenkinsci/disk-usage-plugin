#!/usr/bin/env bash

# TO CLEAN
# find src/test/resources -name \*old -delete
# git restore src/test/resources/

for ZIP_FILE in $(find src -name \*zip |sort ); do
  ZIP_FILE=$(realpath $ZIP_FILE)
  echo "$ZIP_FILE"
  
  TEMP_DIR=$(mktemp -d)
  unzip -q $ZIP_FILE -d $TEMP_DIR

  OLD_ZIP_FILE="$(dirname $ZIP_FILE)/$(basename $ZIP_FILE).old"
  mv $ZIP_FILE $OLD_ZIP_FILE

  find "$TEMP_DIR" -name "*~" -delete

  for PROJECT_DIR in $(find "$TEMP_DIR" -name config.xml -not -path "*/axis/*" |sort| xargs dirname); do
    echo $PROJECT_DIR
    DISK_USAGE_FILE="$PROJECT_DIR/disk-usage.xml"

    BUILD_NUMBER=1
    for BUILD_DIR in $(find "$PROJECT_DIR/builds" -mindepth 1 -maxdepth 1 -type d|sort); do
      OLD_BUILD_NUMBER=$(basename "$BUILD_DIR")
      xmlstarlet ed --inplace \
        -u '/build/number' \
        -v $BUILD_NUMBER \
        "$BUILD_DIR/build.xml"
      mv -v "$BUILD_DIR" "$PROJECT_DIR/builds/$BUILD_NUMBER"
      if test -f "$DISK_USAGE_FILE"; then
        xmlstarlet ed --inplace \
          -u "//hudson.plugins.disk__usage.DiskUsageBuildInformation[id='$OLD_BUILD_NUMBER']/number" \
          -v $BUILD_NUMBER \
          "$DISK_USAGE_FILE"
        xmlstarlet ed --inplace \
          -u "//hudson.plugins.disk__usage.DiskUsageBuildInformation[id='$OLD_BUILD_NUMBER']/id" \
          -v $BUILD_NUMBER \
          "$DISK_USAGE_FILE"
      fi
      ((BUILD_NUMBER=BUILD_NUMBER+1))
    done
    if test -f "$PROJECT_DIR/nextBuildNumber"; then
      echo $BUILD_NUMBER > "$PROJECT_DIR/nextBuildNumber"
    fi
  done

  pushd "$TEMP_DIR"
  zip -q -r $ZIP_FILE .
  popd
  rm -r "$TEMP_DIR"
done