#!/bin/sh
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


COREROOT=../../core
AMQROOT=../../../clients_java

CP=../lib/jython/jython.jar
CP=$CP:../dist/amqp-stac.jar
CP=$CP:$COREROOT/dist/amqp-management-common.jar
CP=$CP:$COREROOT/lib/log4j/log4j-1.2.9.jar
CP=$CP:$COREROOT/lib/xmlbeans/jsr173_api.jar
CP=$CP:$COREROOT/lib/xmlbeans/resolver.jar
CP=$CP:$COREROOT/lib/xmlbeans/xbean.jar
CP=$CP:$COREROOT/lib/xmlbeans/xbean_xpath.jar
CP=$CP:$COREROOT/lib/xmlbeans/xmlpublic.jar
CP=$CP:$COREROOT/lib/xmlbeans/saxon8.jar
CP=$CP:$AMQROOT/dist/amqp-common.jar
CP=$CP:$AMQROOT/dist/amqp-jms.jar
CP=$CP:$AMQROOT/lib/mina/mina-0.7.3.jar
CP=$CP:$AMQROOT/lib/jms/jms.jar
CP=$CP:$AMQROOT/lib/util-concurrent/backport-util-concurrent.jar
CP=$CP:$AMQROOT/lib/jakarta-commons/commons-collections-3.1.jar

$JAVA_HOME/bin/java -Damqj.logging.level="ERROR" -cp $CP org.apache.qpid.stac.Stac
