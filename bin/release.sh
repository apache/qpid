#!/bin/sh
#
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

REV=$1
VER=$2

if [ -z "$REV" -o -z "$VER" ]; then
    echo "Usage: release.sh <revision> <version>"
    exit 1
fi

set -xe

svn export -r ${REV} https://svn.apache.org/repos/asf/incubator/qpid/trunk/qpid qpid-${VER}

mkdir artifacts

echo ${REV} > artifacts/qpid-${VER}.svnversion

tar -czf artifacts/qpid-${VER}.tar.gz qpid-${VER}
tar -czf artifacts/qpid-ruby-${VER}.tar.gz qpid-${VER}/ruby qpid-${VER}/specs
tar -czf artifacts/qpid-python-${VER}.tar.gz qpid-${VER}/python qpid-${VER}/specs

cd qpid-${VER}/cpp
./bootstrap
./configure
make dist -j2

cd ../java
ant build release

cd ../dotnet
cd Qpid.Common
ant
cd ..
./build-nant-release mono-2.0

cd client-010/gentool
ant
cd ..
nant -t:mono-2.0 release-pkg

cd ../../../
cp qpid-${VER}/java/release/*.tar.gz  artifacts/qpid-java-${VER}.tar.gz
cp qpid-${VER}/cpp/*.tar.gz artifacts/qpid-cpp-${VER}.tar.gz
cp qpid-${VER}/dotnet/bin/mono-2.0/release/*.zip artifacts/qpid-dotnet-0-8-${VER}.zip
cp qpid-${VER}/dotnet/client-010/bin/mono-2.0/debug/*.zip artifacts/qpid-dotnet-0-10-${VER}.zip

cd artifacts
sha1sum *.zip *.gz *.svnversion > SHA1SUM
for i in `find . | egrep 'jar$|pom$|gz$|zip$|svnversion$|SHA1SUM'`; do gpg --sign --armor --detach $i; done;
