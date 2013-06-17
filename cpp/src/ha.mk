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
# HA plugin makefile fragment, to be included in Makefile.am
#

dmoduleexec_LTLIBRARIES += ha.la

ha_la_SOURCES =					\
  qpid/ha/AlternateExchangeSetter.h		\
  qpid/ha/Backup.cpp				\
  qpid/ha/Backup.h				\
  qpid/ha/BackupConnectionExcluder.h		\
  qpid/ha/BrokerInfo.cpp			\
  qpid/ha/BrokerInfo.h				\
  qpid/ha/BrokerReplicator.cpp			\
  qpid/ha/BrokerReplicator.h			\
  qpid/ha/ConnectionObserver.cpp		\
  qpid/ha/ConnectionObserver.h			\
  qpid/ha/FailoverExchange.cpp			\
  qpid/ha/FailoverExchange.h			\
  qpid/ha/HaBroker.cpp				\
  qpid/ha/HaBroker.h				\
  qpid/ha/HaPlugin.cpp				\
  qpid/ha/hash.h				\
  qpid/ha/IdSetter.h				\
  qpid/ha/QueueSnapshot.h			\
  qpid/ha/makeMessage.cpp			\
  qpid/ha/makeMessage.h				\
  qpid/ha/Membership.cpp			\
  qpid/ha/Membership.h				\
  qpid/ha/Primary.cpp				\
  qpid/ha/Primary.h				\
  qpid/ha/QueueGuard.cpp			\
  qpid/ha/QueueGuard.h				\
  qpid/ha/QueueReplicator.cpp			\
  qpid/ha/QueueReplicator.h			\
  qpid/ha/QueueSnapshot.h			\
  qpid/ha/QueueSnapshots.h			\
  qpid/ha/RemoteBackup.cpp			\
  qpid/ha/RemoteBackup.h			\
  qpid/ha/ReplicatingSubscription.cpp		\
  qpid/ha/ReplicatingSubscription.h		\
  qpid/ha/ReplicationTest.cpp			\
  qpid/ha/ReplicationTest.h			\
  qpid/ha/Role.h				\
  qpid/ha/Settings.h				\
  qpid/ha/StandAlone.h				\
  qpid/ha/StatusCheck.cpp			\
  qpid/ha/StatusCheck.h				\
  qpid/ha/types.cpp				\
  qpid/ha/types.h

ha_la_LIBADD = libqpidbroker.la libqpidmessaging.la
ha_la_LDFLAGS = $(PLUGINLDFLAGS)
ha_la_CXXFLAGS = $(AM_CXXFLAGS) -D_IN_QPID_BROKER
