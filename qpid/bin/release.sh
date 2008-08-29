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

svn delete https://svn.eu.apache.org/repos/asf/incubator/qpid/tags/M3
svn copy https://svn.eu.apache.org/repos/asf/incubator/qpid/trunk  https://svn.eu.apache.org/repos/asf/incubator/qpid/tags/M3
svn co https://svn.eu.apache.org/repos/asf/incubator/qpid/tags/M3 qpid-M3
cd qpid-M3
ln -s qpid/ qpid-incubating-M3
tar -zhcf qpid-incubating-M3.tar.gz --exclude=.svn qpid-incubating-M3/
rm qpid-incubating-M3
tar -zxf qpid-incubating-M3.tar.gz
tar -hzcf qpid-incubating-M3-ruby.tar.gz qpid-incubating-M3/ruby/ qpid-incubating-M3/specs/
tar -zcf qpid-incubating-M3-python.tar.gz qpid-incubating-M3/python/ qpid-incubating-M3/specs/
cd qpid-incubating-M3/cpp
./bootstrap
./configure
make dist -j4
cd ../java
ant build release
cd ../dotnet
sh build-framing
./release mono-2.0
cd ../../
mkdir ../artifacts
cp qpid-incubating-M3/java/release/*.tar.gz  ../artifacts
cp *.tar.gz ../artifacts
cp qpid-incubating-M3/cpp/*tar.gz ../artifacts/qpid-incubating-M3-cpp.tar.gz
cp qpid-incubating-M3/dotnet/bin/mono-2.0/release/*.zip ../artifacts/qpid-incubating-M3-dotnet.zip
cd ../artifacts
sha1sum *.zip *.gz > SHA1SUM
for i in `find . | egrep 'jar$|pom$|gz$|zip$|SHA1SUM'`; do gpg --sign --armor --detach $i; done;
