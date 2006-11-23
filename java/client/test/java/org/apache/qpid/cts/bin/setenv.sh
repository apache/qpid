#
# Copyright (c) 2006 The Apache Software Foundation
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# ---------------------------------------------------------------------------
# Sample environment script for JMS CTS
#
# This is invoked by jmscts.sh to configure:
# . the CLASSPATH, for JMS provider jars
# . JVM options
#
# The following configures the JMS CTS for OpenJMS 0.7.6
# ---------------------------------------------------------------------------

# Configure the CLASSPATH
#
DISTDIR="$IBASE/amqp/dist"
LIBDIR="$IBASE/amqp/lib"

CLASSPATH="$LIBDIR/jakarta-commons/commons-collections-3.1.jar:$LIBDIR/util-concurrent/backport-util-concurrent.jar:$LIBDIR/mina/mina-0.7.3.jar:$LIBDIR/jms/jms.jar:$LIBDIR/logging-log4j/log4j-1.2.9.jar:$DISTDIR/amqp-common.jar:$DISTDIR/amqp-jms.jar"

# Configure JVM options
#
JAVA_OPTS=-Xmx512m -Xms512m
JAVA_OPTS="$JAVA_OPTS \
           -Damqj.logging.level=WARN"
