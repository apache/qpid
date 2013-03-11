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

usage()
{
    echo "Usage: release.sh <svn-path> <svn-revision> <version> [options]"
    echo
    echo "Options: Default : --prepare --svn --all --sign"
    echo "--help  |-h : Show this help"
    echo "--prepare   : Export specified tree from source control"
    echo "--svn       : Export from svn"
    echo "--git       : Export from git repository with svn metadata"
    echo "--clean-all : Remove build artefacts and downloaded svn tree"
    echo "--clean     : Remove built artefacts"
    echo "--all   |-a : Generate all artefacts"
    echo "--source|-e : Generate the source artefact"
    echo "--cpp   |-c : Generate the CPP artefacts"
    echo "--java  |-j : Generate the java artefacts"
    echo "--perl  |-r : Generate the Perl artefacts"
    echo "--python|-p : Generate the python artefacts"
    echo "--wcf   |-w : Generate the WCF artefacts"
    echo "--tools |-t : Generate the tools artefacts"
    echo "--qmf   |-q : Generate the QMF artefacts"
    echo "--source|-e : Generate the source artefact"
    echo "--sign  |-s : Sign generated artefacts"
    echo "--upload|-u : Upload the artifacts directory to people.apache.org as qpid-\$VER"
    echo
}

all_artefacts()
{
   echo ALL_ARTEFACTS

   CPP="CPP"
   JAVA="JAVA"
   PERL="PERL"
   PYTHON="PYTHON"
   WCF="WCF"
   TOOLS="TOOLS"
   QMF="QMF"
   SOURCE="SOURCE"
}

REPO="SVN"
for arg in $* ; do 
 case $arg in
 --help|-h)
   HELP="HELP"
 ;;
 --prepare)
   PREPARE="PREPARE"
 ;;
 --svn)
   REPO="SVN"
 ;;
 --git)
   REPO="GIT"
 ;;
 --clean-all)
   CLEAN="CLEAN"
   CLEAN_ARTIFACTS="CLEAN_ARTIFACTS"
 ;;
 --clean)
   CLEAN_ARTIFACTS="CLEAN_ARTIFACTS"
 ;;
 --sign|-s)
   SIGN="SIGN"
 ;;
 --all|-a)
   all_artefacts
 ;;
 --cpp|-c)
   CPP="CPP"
 ;;
 --java|-j)
   JAVA="JAVA"
 ;;
 --perl|-r)
   PERL="PERL"
 ;;
 --python|-p)
   PYTHON="PYTHON"
 ;;
 --wcf|-w)
   WCF="WCF"
 ;;
 --tools|-t)
   TOOLS="TOOLS"
 ;;
 --qmf|-q)
   QMF="QMF"
 ;;
 --source|-e)
   SOURCE="SOURCE"
 ;;
 --upload|-u)
   UPLOAD="UPLOAD"
 ;;
 *)
  if [ -z "$SVN" ] ; then
   SVN=$arg
   continue
  fi
 
  if [ -z "$REV" ] ; then
   REV=$arg
   continue
  fi
  
  if [ -z "$VER" ] ; then
   VER=$arg
   continue
  fi 
 ;;
 esac
done

if [ -n "${HELP}" ] ; then
 usage
 exit 0
fi

if [ -z "$SVN" -o -z "$REV" -o -z "$VER" ]; then
    echo "Usage: release.sh <svn-path> <svn-revision> <version>"
    exit 1
fi

echo SVN:$SVN
echo REV:$REV
echo VER:$VER

# If nothing is specified then do it all
if [ -z "${CLEAN}${PREPARE}${CPP}${JAVA}${PERL}${PYTHON}${QMF}${TOOLS}${WCF}${SOURCE}${SIGN}${UPLOAD}" ] ; then
   PREPARE="PREPARE"
   all_artefacts
   SIGN="SIGN"
fi

set -xe

if [ "CLEAN" == "$CLEAN" ] ; then
  rm -rf qpid-${VER}
fi

if [ "CLEAN_ARTIFACTS" == "$CLEAN_ARTIFACTS" ] ; then
  rm -rf artifacts
fi

if [ "PREPARE" == "$PREPARE" ] ; then
  mkdir artifacts
  case ${REPO} in
  SVN)  
    URL=https://svn.apache.org/repos/asf/qpid/${SVN}
    svn export -r ${REV} ${URL} qpid-${VER}
    echo ${URL} ${REV} > artifacts/qpid-${VER}.svnversion
  ;;
  GIT)
    URL=${SVN}
    GITREV=$(GIT_DIR=${URL} git svn find-rev r${REV})
    git archive --remote=${URL}  ${GITREV} | tar xvf -
    mv qpid qpid-${VER}
    echo ${REV} > artifacts/qpid-${VER}.svnversion
  ;;
  esac
fi

if [ "SOURCE" == "$SOURCE" ] ; then
  tar -czf artifacts/qpid-${VER}.tar.gz qpid-${VER}
fi

if [ "PERL" == "$PERL" ]; then
  mkdir qpid-${VER}/perl-qpid-${VER}
  cp qpid-${VER}/cpp/bindings/qpid/perl/perl.i \
     qpid-${VER}/cpp/bindings/qpid/perl/*pm \
     qpid-${VER}/cpp/bindings/qpid/perl/LICENSE \
     qpid-${VER}/cpp/bindings/qpid/perl/Makefile.PL \
     qpid-${VER}/cpp/bindings/qpid/perl/t/*.t \
     qpid-${VER}/perl-qpid-${VER}
  cp -r qpid-${VER}/cpp/bindings/qpid/perl/lib \
     qpid-${VER}/perl-qpid-${VER}
  mkdir qpid-${VER}/perl-qpid-${VER}/examples
  cp qpid-${VER}/cpp/bindings/qpid/examples/perl/* \
     qpid-${VER}/perl-qpid-${VER}/examples
  pushd qpid-${VER}
  tar -czf ../artifacts/perl-qpid-${VER}.tar.gz perl-qpid-${VER}
  popd
fi

if [ "PYTHON" == "$PYTHON" ] ; then
  tar -czf artifacts/qpid-python-${VER}.tar.gz qpid-${VER}/python qpid-${VER}/specs
fi

if [ "WCF" == "$WCF" ] ; then
  zip -rq artifacts/qpid-wcf-${VER}.zip qpid-${VER}/wcf
fi

if [ "CPP" == "$CPP" ] ; then
  pushd qpid-${VER}/cpp
  ./bootstrap
  ./configure
  make dist -j2
  popd

  cp qpid-${VER}/cpp/*.tar.gz artifacts/qpid-cpp-${VER}.tar.gz
fi

if [ "JAVA" == "$JAVA" ] ; then
  # generate the java 'release' archive seperately to ensure it doesnt have any optional feature dependencies in it
  pushd qpid-${VER}/java
  ant clean build release -Dsvnversion.output=${REV}
  popd

  cp qpid-${VER}/java/release/*.tar.gz  artifacts/qpid-java-${VER}.tar.gz

  # now generate the binary packages, with the glue for optional features
  pushd qpid-${VER}/java
  ant build release-bin -Dsvnversion.output=${REV} -Dmodules.opt="bdbstore,bdbstore/jmx" -Ddownload-bdb=true
  ant release-mvn -Dsvnversion.output=${REV} -Dmodules.opt="bdbstore,bdbstore/jmx" -Dmaven.snapshot=false
  popd

  cp qpid-${VER}/java/broker/release/*.tar.gz artifacts/qpid-java-broker-${VER}.tar.gz
  cp qpid-${VER}/java/client/release/*.tar.gz artifacts/qpid-java-client-${VER}.tar.gz
  cp qpid-${VER}/java/amqp-1-0-client-jms/release/*.tar.gz artifacts/qpid-java-amqp-1-0-client-jms-${VER}.tar.gz

  # copy the Maven artifacts
  cp -a qpid-${VER}/java/amqp-1-0-client/release/maven artifacts/
  cp -a qpid-${VER}/java/amqp-1-0-client-jms/release/maven artifacts/
  cp -a qpid-${VER}/java/amqp-1-0-common/release/maven artifacts/
  cp -a qpid-${VER}/java/client/release/maven artifacts/
  cp -a qpid-${VER}/java/common/release/maven artifacts/
  cp -a qpid-${VER}/java/broker/release/maven artifacts/
  cp -a qpid-${VER}/java/bdbstore/release/maven artifacts/
  cp -a qpid-${VER}/java/management/common/release/maven artifacts/
  cp -a qpid-${VER}/java/amqp-1-0-common/release/maven artifacts/
  cp -a qpid-${VER}/java/broker-plugins/access-control/release/maven artifacts/
  cp -a qpid-${VER}/java/broker-plugins/management-jmx/release/maven artifacts/
  cp -a qpid-${VER}/java/broker-plugins/management-http/release/maven artifacts/
  cp -a qpid-${VER}/java/bdbstore/jmx/release/maven artifacts/
fi

if [ "TOOLS" = "$TOOLS" ] ; then
    pushd qpid-${VER}/tools

    python setup.py sdist
    
    popd

    cp qpid-${VER}/tools/dist/*.tar.gz artifacts/qpid-tools-${VER}.tar.gz
fi

if [ "QMF" = "$QMF" ]; then
    pushd qpid-${VER}/extras/qmf

    python setup.py sdist

    popd

    cp qpid-${VER}/extras/qmf/dist/*.tar.gz artifacts/qpid-qmf-${VER}.tar.gz
fi

if [ "SIGN" == "$SIGN" ] ; then
  pushd artifacts
  sha1sum *.zip *.gz *.svnversion > SHA1SUM
  if [ ! -z $SIGNING_KEY ] ; then
    KEYOPTION="--default-key $SIGNING_KEY"
  fi
  for i in `find . | egrep 'jar$|pom$|gz$|zip$|svnversion$|SHA1SUM'`; do gpg --sign --armor --detach $KEYOPTION $i; done;
  popd
fi

if [ "UPLOAD" == "$UPLOAD" ] ; then
  scp -r artifacts people.apache.org:qpid-${VER}
fi
