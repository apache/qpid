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

INCLUDE = -I$(top_srcdir)/include

AM_CPPFLAGS = $(INCLUDE)

noinst_PROGRAMS=agent event_driven_list_agents list_agents print_events

agent_SOURCES=agent.cpp
agent_LDADD=$(top_builddir)/src/libqmf2.la -lqpidtypes -lqpidmessaging

list_agents_SOURCES=list_agents.cpp
list_agents_LDADD=$(top_builddir)/src/libqmf2.la -lqpidmessaging

event_driven_list_agents_SOURCES=event_driven_list_agents.cpp
event_driven_list_agents_LDADD=$(top_builddir)/src/libqmf2.la -lqpidmessaging

print_events_SOURCES=print_events.cpp
print_events_LDADD=$(top_builddir)/src/libqmf2.la -lqpidtypes -lqpidmessaging
