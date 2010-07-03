#!/bin/bash
#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

TEMPLATE_DIR=template
TEMPLATE=template.html
CONTENT_DIR=content
BUILD_DIR=build
WRAP="tools/wrap"

# Gather the *.html filenames to wrap
pushd $CONTENT_DIR >/dev/null
HTML_FILES=`ls *.html`
popd >/dev/null

# Clear the existing artefacts, or create the build dir
if [ -d $BUILD_DIR ];
then
  rm -rf $BUILD_DIR/*;
else
  mkdir $BUILD_DIR
fi

# Wrap the html files
for FILE in $HTML_FILES;
do
  ./$WRAP "$TEMPLATE_DIR/$TEMPLATE" "$CONTENT_DIR/$FILE" "$BUILD_DIR/$FILE"
done

# Copy the .htaccess, style.css, images, and any specifically required non-html content
cp $CONTENT_DIR/.htaccess $BUILD_DIR/
cp $TEMPLATE_DIR/style.css $BUILD_DIR/
cp -R $TEMPLATE_DIR/images $BUILD_DIR/
cp -R $CONTENT_DIR/images $BUILD_DIR/
cp $CONTENT_DIR/download.cgi $BUILD_DIR/


