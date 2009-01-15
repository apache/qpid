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

# Make file for building two plugins for asynchronously replicating
# queues.

dmodule_LTLIBRARIES += replicating_listener.la replication_exchange.la

# a queue event listener plugin that creates messages on a replication
# queue corresponding to enqueue and dequeue events:
replicating_listener_la_SOURCES =  \
	qpid/replication/constants.h \
	qpid/replication/ReplicatingEventListener.cpp \
	qpid/replication/ReplicatingEventListener.h 

replicating_listener_la_LIBADD = libqpidbroker.la

replicating_listener_la_LDFLAGS = $(PLUGINLDFLAGS)

# a custom exchange plugin that allows an exchange to be created that
# can process the messages from a replication queue (populated on the
# source system by the replicating listener plugin above) and take the
# corresponding action on the local queues
replication_exchange_la_SOURCES =  \
	qpid/replication/constants.h \
	qpid/replication/ReplicationExchange.cpp \
	qpid/replication/ReplicationExchange.h 

replication_exchange_la_LIBADD = libqpidbroker.la

replication_exchange_la_LDFLAGS = $(PLUGINLDFLAGS)
