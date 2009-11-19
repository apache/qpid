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

abs_path()
{
  D=`dirname "$1"`
  B=`basename "$1"`
  echo "`cd \"$D\" 2>/dev/null && pwd || echo \"$D\"`/$B"
}

# Environment for python tests
test -d ../../../python || { echo "WARNING: skipping test, no python directory."; exit 0; }
PYTHON_DIR=../../../python
PYTHONPATH=$PYTHON_DIR:$PYTHON_DIR/qpid

if [ "$QPIDD_EXEC" = "" ] ; then
   test -x ../../../cpp/src/qpidd || { echo "WARNING: skipping test, QPIDD_EXEC not set and qpidd not found."; exit 0; }
   QPIDD_EXEC=`abs_path "../../../cpp/src/qpidd"`
   #QPIDD_EXEC=/opt/workspace/qpid/trunk/qpid/cpp/src/.libs/lt-qpidd
fi

if [ "$CLUSTER_LIB" = "" ] ; then
   test -x ../../../cpp/src/.libs/cluster.so || { echo "WARNING: skipping test, CLUSTER_LIB not set and cluster.so not found."; exit 0; }
   CLUSTER_LIB=`abs_path "../../../cpp/src/.libs/cluster.so"`
   #CLUSTER_LIB=/opt/workspace/qpid/trunk/qpid/cpp/src/.libs/cluster.so
fi

if [ "$QP_CP" = "" ] ; then
   QP_CP=`find ../../build/lib/ -name '*.jar' | tr '\n' ':'`
fi

if [ "$OUTDIR" = "" ] ; then
   OUTDIR=`abs_path "../output"`
fi


export PYTHONPATH PYTHON_DIR QPIDD_EXEC CLUSTER_LIB QP_CP OUTDIR
