#!/usr/bin/bash
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
#
# Script to pull together an Apache Release
#

ME=$(basename $0)

usage() {
    cat <<-EOF
USAGE: ${ME} [options] SVNPATH SVNREV VERSION
Creates an Apache release tarball.

Mandatory arguments:
  SVNPATH  The path within the source code repository.
  SVNREV   The revision at which to create the release.
  VERSION  The release version.

Optional arguments:
  -h       This help screen
EOF
}

while getopts "h" opt; do
    case $opt in
        h)
            usage
            exit 0
            ;;

        \?)
            echo "Invalid option: -$OPTARG" >&2
            usage
            exit 1
            ;;

        :)
            echo "Option -$OPTARG requires an argument." >&2
            usage
            exit 1
            ;;
    esac
done

SVNPATH=${1-}
SVNREV=${2-}
VERSION=${3-}

if [[ -z "$SVNPATH" ]] || [[ -z "$SVNREV" ]] || [[ -z "$VERSION" ]]; then
    printf "Missing one or more required argument.\n\n" >&2
    usage
    exit 1
fi

URL=http://svn.apache.org/repos/asf/qpid/${SVNPATH}

WORKDIR=$(mktemp -d)
BASENAME=qpid-dispatch-${VERSION}
FILENAME=$PWD/${BASENAME}.tar.gz

if [ -f $FILENAME ]; then rm -f $FILENAME; fi

(
echo "Checking out to ${WORKDIR}..."
cd $WORKDIR
svn export -r ${SVNREV} ${URL}/extras/dispatch ${BASENAME} >/dev/null

echo "Building source tarball..."
cd $WORKDIR
tar --exclude release.sh -zcvf $FILENAME ${BASENAME} >/dev/null
)

echo "Done!"
