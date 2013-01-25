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
examplesdir=$(pkgdatadir)/examples/messaging

# Settings to build the examples in automake
AM_CXXFLAGS = $(WARNING_CFLAGS)
INCLUDES = -I$(top_srcdir)/include -I$(top_builddir)/include
CLIENT_LIB=$(top_builddir)/src/libqpidmessaging.la
TYPES_LIB=$(top_builddir)/src/libqpidtypes.la
CLIENTFLAGS=-lqpidmessaging

noinst_PROGRAMS=drain spout client server map_sender map_receiver hello_world hello_xml

hello_world_SOURCES=hello_world.cpp
hello_world_LDADD=$(CLIENT_LIB)

hello_xml_SOURCES=hello_xml.cpp
hello_xml_LDADD=$(CLIENT_LIB)

drain_SOURCES=drain.cpp OptionParser.h OptionParser.cpp
drain_LDADD=$(CLIENT_LIB) $(TYPES_LIB)

spout_SOURCES=spout.cpp OptionParser.h OptionParser.cpp
spout_LDADD=$(CLIENT_LIB) $(TYPES_LIB)

client_SOURCES=client.cpp
client_LDADD=$(CLIENT_LIB)

server_SOURCES=server.cpp
server_LDADD=$(CLIENT_LIB)

map_sender_SOURCES=map_sender.cpp
map_sender_LDADD=$(CLIENT_LIB) $(TYPES_LIB)

map_receiver_SOURCES=map_receiver.cpp
map_receiver_LDADD=$(CLIENT_LIB) $(TYPES_LIB)

examples_DATA=                 \
	hello_world.cpp	       \
	hello_xml.cpp	       \
	drain.cpp              \
	spout.cpp              \
	OptionParser.cpp       \
	OptionParser.h         \
	client.cpp             \
	server.cpp             \
	map_sender.cpp         \
	map_receiver.cpp       \
	extra_dist/CMakeLists.txt

EXTRA_DIST=                  \
	$(examples_DATA)     \
	CMakeLists.txt \
	messaging_client.vcproj \
	messaging_drain.vcproj \
	messaging_map_receiver.vcproj \
	messaging_map_sender.vcproj \
	messaging_server.vcproj \
	messaging_spout.vcproj
