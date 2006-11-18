#!/bin/sh
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

# -----------------------------------------------------------------------------
# Start/Stop Script for the JMS compliance test suite
#
# Required Environment Variables
#
#   JAVA_HOME       Points to the Java Development Kit installation.
#
# Optional Environment Variables
# 
#   JMSCTS_HOME     Points to the JMS CTS installation directory.
#
#   JAVA_OPTS       Java runtime options used when the command is executed.
#
#
# $Id: jmscts.sh,v 1.6 2003/09/27 09:50:49 tanderson Exp $
# ---------------------------------------------------------------------------

# OS specific support.  $var _must_ be set to either true or false.
cygwin=false
case "`uname`" in
CYGWIN*) cygwin=true;;
esac

# For Cygwin, ensure paths are in UNIX format before anything is touched
if $cygwin; then
  [ -n "$JAVA_HOME" ] && JAVA_HOME=`cygpath --unix "$JAVA_HOME"`
fi

if [ -z "$JAVA_HOME" ]; then
  echo "The JAVA_HOME environment variable is not set."
  echo "This is required to run jmscts"
  exit 1
fi
if [ ! -r "$JAVA_HOME"/bin/java ]; then
  echo "The JAVA_HOME environment variable is not set correctly."
  echo "This is required to run jmscts"
  exit 1
fi
_RUNJAVA="$JAVA_HOME"/bin/java


# Guess JMSCTS_HOME if it is not set
if [ -z "$JMSCTS_HOME" ]; then
# resolve links - $0 may be a softlink
  PRG="$0"
  while [ -h "$PRG" ]; do
    ls=`ls -ld "$PRG"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '.*/.*' > /dev/null; then
      PRG="$link"
    else
      PRG=`dirname "$PRG"`/"$link"
    fi
  done

  PRGDIR=`dirname "$PRG"`
  JMSCTS_HOME=`cd "$PRGDIR/.." ; pwd`
elif [ ! -r "$JMSCTS_HOME"/bin/jmscts.sh ]; then
  echo "The JMSCTS_HOME environment variable is not set correctly."
  echo "This is required to run jmscts"
  exit 1
fi

# Set CLASSPATH to empty by default. User jars can be added via the setenv.sh
# script
CLASSPATH=

if [ -r "$JMSCTS_HOME"/bin/setenv.sh ]; then
  . "$JMSCTS_HOME"/bin/setenv.sh
fi

CLASSPATH="$CLASSPATH":"$JMSCTS_HOME"/lib/jmscts-0.5-b2.jar

# For Cygwin, switch paths to Windows format before running java
if $cygwin; then
  JAVA_HOME=`cygpath --path --windows "$JAVA_HOME"`
  JMSCTS_HOME=`cygpath --path --windows "$JMSCTS_HOME"`
  CLASSPATH=`cygpath --path --windows "$CLASSPATH"`
fi

POLICY_FILE="$JMSCTS_HOME"/config/jmscts.policy

# Configure TrAX
JAVAX_OPTS=-Djavax.xml.transform.TransformerFactory=org.apache.xalan.processor.TransformerFactoryImpl


# Execute the requested command

echo "Using JMSCTS_HOME: $JMSCTS_HOME"
echo "Using JAVA_HOME:   $JAVA_HOME"
echo "Using CLASSPATH:   $CLASSPATH"

if [ "$1" = "run" ]; then

  shift
  exec "$_RUNJAVA" $JAVA_OPTS $JAVAX_OPTS -Djmscts.home="$JMSCTS_HOME" \
      -classpath "$CLASSPATH" \
      -Djava.security.manager -Djava.security.policy="$POLICY_FILE" \
      org.exolab.jmscts.test.ComplianceTestSuite "$@"

elif [ "$1" = "stress" ]; then

  shift
  exec "$_RUNJAVA" $JAVA_OPTS $JAVAX_OPTS -Djmscts.home="$JMSCTS_HOME" \
      -classpath "$CLASSPATH" \
      -Djava.security.manager -Djava.security.policy="$POLICY_FILE" \
      org.exolab.jmscts.stress.StressTestSuite "$@"

elif [ "$1" = "stop" ] ; then

  shift
  "$_RUNJAVA" $JAVA_OPTS $JAVAX_OPTS -Djmscts.home="$JMSCTS_HOME" \
      -classpath "$CLASSPATH" \
      -Djava.security.manager -Djava.security.policy="$POLICY_FILE" \
      org.exolab.jmscts.core.Admin -stop

elif [ "$1" = "abort" ] ; then

  shift
  exec "$_RUNJAVA" $JAVA_OPTS $JAVAX_OPTS -Djmscts.home="$JMSCTS_HOME" \
      -classpath "$CLASSPATH" \
      -Djava.security.manager -Djava.security.policy="$POLICY_FILE" \
      org.exolab.jmscts.core.Admin -abort

elif [ "$1" = "snapshot" ] ; then

  shift
  exec "$_RUNJAVA" $JAVA_OPTS $JAVAX_OPTS -Djmscts.home="$JMSCTS_HOME" \
      -classpath "$CLASSPATH" \
      -Djava.security.manager -Djava.security.policy="$POLICY_FILE" \
      org.exolab.jmscts.core.Admin -snapshot "$@"

else
  echo "usage: jmscts.sh (commands)"
  echo "commands:"
  echo "  run          Run compliance tests"
  echo "  stress       Run stress tests"
  echo "  stop         Stop the JMS CTS"
  echo "  abort        Abort the JMS CTS"
  echo "  snapshot     Take a snapshot"
  exit 1
fi
