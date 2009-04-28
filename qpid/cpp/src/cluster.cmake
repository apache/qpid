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
# Cluster library CMake fragment, to be included in CMakeLists.txt
# 

# Optional cluster support. Requires CPG; if building it, can optionally
# include CMAN support as well.

include(CheckIncludeFiles)
include(CheckLibraryExists)

if (CMAKE_SYSTEM_NAME STREQUAL Windows)
  set (cluster_default OFF)
else (CMAKE_SYSTEM_NAME STREQUAL Windows)
  set (cluster_default ON)
endif (CMAKE_SYSTEM_NAME STREQUAL Windows)

option(BUILD_CPG "Build with CPG support for clustering" ${cluster_default})
if (BUILD_CPG)
  find_library(LIBCPG cpg /usr/lib/openais /usr/lib64/openais /usr/lib/corosync /usr/lib64/corosync)
  CHECK_LIBRARY_EXISTS (${LIBCPG} cpg_local_get "" HAVE_LIBCPG)
  CHECK_INCLUDE_FILES (openais/cpg.h HAVE_OPENAIS_CPG_H)
  CHECK_INCLUDE_FILES (corosync/cpg.h HAVE_COROSYNC_CPG_H)
  if (NOT HAVE_LIBCPG)
    message(FATAL_ERROR "libcpg not found, install openais-devel or corosync-devel")
  endif (NOT HAVE_LIBCPG)
  if (NOT HAVE_OPENAIS_CPG_H AND NOT HAVE_COROSYNC_CPG_H)
    message(FATAL_ERROR "cpg.h not found, install openais-devel or corosync-devel")
  endif (NOT HAVE_OPENAIS_CPG_H AND NOT HAVE_COROSYNC_CPG_H)

  option(CPG_INCLUDE_CMAN "Include libcman quorum service integration" ON)
  if (CPG_INCLUDE_CMAN)
    CHECK_LIBRARY_EXISTS (cman cman_is_quorate "" HAVE_LIBCMAN)
    CHECK_INCLUDE_FILES (libcman.h HAVE_LIBCMAN_H)
    if (NOT HAVE_LIBCMAN)
      message(FATAL_ERROR "libcman not found, install cman-devel or cmanlib-devel")
    endif (NOT HAVE_LIBCMAN)
    if (NOT HAVE_LIBCMAN_H)
      message(FATAL_ERROR "libcman.h not found, install cman-devel or cmanlib-devel")
    endif (NOT HAVE_LIBCMAN_H)

    set (CMAN_SOURCES qpid/cluster/Quorum_cman.h qpid/cluster/Quorum_cman.cpp)
    set (CMAN_LIB cman)
  else (CPG_INCLUDE_CMAN)
    set (CMAN_SOURCES qpid/cluster/Quorum_null.h)
  endif (CPG_INCLUDE_CMAN)

  set (cluster_SOURCES
       ${CMAN_SOURCES}
       qpid/cluster/Cluster.cpp
       qpid/cluster/Cluster.h
       qpid/cluster/Decoder.cpp
       qpid/cluster/Decoder.h
       qpid/cluster/PollableQueue.h
       qpid/cluster/ClusterMap.cpp
       qpid/cluster/ClusterMap.h
       qpid/cluster/ClusterPlugin.cpp
       qpid/cluster/ClusterSettings.h
       qpid/cluster/Connection.cpp
       qpid/cluster/Connection.h
       qpid/cluster/ConnectionCodec.cpp
       qpid/cluster/ConnectionCodec.h
       qpid/cluster/Cpg.cpp
       qpid/cluster/Cpg.h
       qpid/cluster/Dispatchable.h
       qpid/cluster/UpdateClient.cpp
       qpid/cluster/UpdateClient.h
       qpid/cluster/Event.cpp
       qpid/cluster/Event.h
       qpid/cluster/EventFrame.h
       qpid/cluster/EventFrame.cpp
       qpid/cluster/ExpiryPolicy.h
       qpid/cluster/ExpiryPolicy.cpp
       qpid/cluster/FailoverExchange.cpp
       qpid/cluster/FailoverExchange.h
       qpid/cluster/UpdateExchange.h
       qpid/cluster/LockedConnectionMap.h
       qpid/cluster/Multicaster.cpp
       qpid/cluster/Multicaster.h
       qpid/cluster/McastFrameHandler.h
       qpid/cluster/NoOpConnectionOutputHandler.h
       qpid/cluster/OutputInterceptor.cpp
       qpid/cluster/OutputInterceptor.h
       qpid/cluster/PollerDispatch.cpp
       qpid/cluster/PollerDispatch.h
       qpid/cluster/ProxyInputHandler.h
       qpid/cluster/Quorum.h
       qpid/cluster/WriteEstimate.cpp
       qpid/cluster/WriteEstimate.h
       qpid/cluster/types.h
      )

  add_library (cluster MODULE ${cluster_SOURCES})
  target_link_libraries (cluster ${LIBCPG} ${CMAN_LIB} qpidbroker qpidclient)
#cluster_la_LDFLAGS = $(PLUGINLDFLAGS)
  set_target_properties (cluster PROPERTIES
                         PREFIX "")

endif (BUILD_CPG)

# Distribute all sources.
#EXTRA_DIST += qpid/cluster/Quorum_cman.h qpid/cluster/Quorum_cman.cpp qpid/cluster/Quorum_null.h
