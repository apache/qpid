##
## Licensed to the Apache Software Foundation (ASF) under one
## or more contributor license agreements.  See the NOTICE file
## distributed with this work for additional information
## regarding copyright ownership.  The ASF licenses this file
## to you under the Apache License, Version 2.0 (the
## "License"); you may not use this file except in compliance
## with the License.  You may obtain a copy of the License at
##
##   http://www.apache.org/licenses/LICENSE-2.0
##
## Unless required by applicable law or agreed to in writing,
## software distributed under the License is distributed on an
## "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
## KIND, either express or implied.  See the License for the
## specific language governing permissions and limitations
## under the License.
##

project(qpid-dispatch C)

cmake_minimum_required(VERSION 2.6)
include(CheckLibraryExists)
include(CheckSymbolExists)
include(CheckFunctionExists)
include(CheckIncludeFiles)
include(FindPythonInterp)
include(FindPythonLibs)

enable_testing()
include (CTest)

if (NOT PYTHONLIBS_FOUND)
     message(FATAL_ERROR "Python Development Libraries are needed.")
endif (NOT PYTHONLIBS_FOUND)

set (SO_VERSION_MAJOR 0)
set (SO_VERSION_MINOR 1)
set (SO_VERSION "${SO_VERSION_MAJOR}.${SO_VERSION_MINOR}")

if (NOT DEFINED LIB_SUFFIX)
    get_property(LIB64 GLOBAL PROPERTY FIND_LIBRARY_USE_LIB64_PATHS)
    if ("${LIB64}" STREQUAL "TRUE" AND ${CMAKE_SIZEOF_VOID_P} STREQUAL "8")
        set(LIB_SUFFIX 64)
    else()
        set(LIB_SUFFIX "")
    endif()
endif()

set(INCLUDE_INSTALL_DIR include CACHE PATH "Include file directory")
set(LIB_INSTALL_DIR "lib${LIB_SUFFIX}" CACHE PATH "Library object file directory")
set(SYSCONF_INSTALL_DIR etc CACHE PATH "System read only configuration directory")
set(SHARE_INSTALL_DIR share CACHE PATH "Shared read only data directory")
set(MAN_INSTALL_DIR share/man CACHE PATH "Manpage directory")

# determine the location for installing the python packages
if (PYTHONLIBS_FOUND)
    execute_process(COMMAND ${PYTHON_EXECUTABLE}
                    -c "from distutils.sysconfig import get_python_lib; print get_python_lib(False)"
                    OUTPUT_VARIABLE PYTHON_SITELIB_PACKAGES
                    OUTPUT_STRIP_TRAILING_WHITESPACE)
endif (PYTHONLIBS_FOUND)

##
## Find dependencies
##
find_library(proton_lib qpid-proton)
find_library(pthread_lib pthread)
find_library(rt_lib rt)
find_path(proton_include proton/driver.h)

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}/src
    ${proton_include}
    ${PYTHON_INCLUDE_PATH}
    )

set(CMAKE_C_FLAGS "-pthread -Wall -Werror -std=gnu99")
set(CATCH_UNDEFINED "-Wl,--no-undefined")

##
## Build the Multi-Threaded Server Library
##
set(server_SOURCES
    src/agent.c
    src/alloc.c
    src/amqp.c
    src/bitmask.c
    src/buffer.c
    src/compose.c
    src/config.c
    src/container.c
    src/dispatch.c
    src/hash.c
    src/iovec.c
    src/iterator.c
    src/log.c
    src/message.c
    src/parse.c
    src/posix/threading.c
    src/python_embedded.c
    src/router_node.c
    src/server.c
    src/timer.c
    src/work_queue.c
    )

add_library(qpid-dispatch SHARED ${server_SOURCES})
target_link_libraries(qpid-dispatch ${proton_lib} ${pthread_lib} ${rt_lib} ${PYTHON_LIBRARIES})
set_target_properties(qpid-dispatch PROPERTIES
                      VERSION "${SO_VERSION}"
                      SOVERSION "${SO_VERSION_MAJOR}"
                      LINK_FLAGS "${CATCH_UNDEFINED}"
                      )
install(TARGETS qpid-dispatch
        LIBRARY DESTINATION ${LIB_INSTALL_DIR})
file(GLOB headers "include/qpid/dispatch/*.h")
install(FILES ${headers} DESTINATION ${INCLUDE_INSTALL_DIR}/qpid/dispatch)
install(FILES include/qpid/dispatch.h DESTINATION ${INCLUDE_INSTALL_DIR}/qpid)
install(FILES etc/qpid-dispatch.conf DESTINATION ${SYSCONF_INSTALL_DIR})

##
## Python modules installation
##
set(PYTHON_STUBS_SOURCES
    python/qpid/dispatch/stubs/__init__.py
    python/qpid/dispatch/stubs/ioadapter.py
    python/qpid/dispatch/stubs/logadapter.py
)

set(PYTHON_ROUTER_SOURCES
    python/qpid/dispatch/router/link.py
    python/qpid/dispatch/router/router_engine.py
    python/qpid/dispatch/router/__init__.py
    python/qpid/dispatch/router/adapter.py
    python/qpid/dispatch/router/mobile.py
    python/qpid/dispatch/router/node.py
    python/qpid/dispatch/router/routing.py
    python/qpid/dispatch/router/data.py
    python/qpid/dispatch/router/configuration.py
    python/qpid/dispatch/router/neighbor.py
    python/qpid/dispatch/router/path.py
    python/qpid/dispatch/router/binding.py
)

set(PYTHON_CONFIG_SOURCES
    python/qpid/dispatch/config/parser.py
    python/qpid/dispatch/config/__init__.py
    python/qpid/dispatch/config/schema.py
    python/qpid/dispatch/__init__.py
)

set(DOC_FILES
    ChangeLog
    LICENSE
    README.md
    TODO
)

install(FILES ${PYTHON_STUBS_SOURCES}
        DESTINATION ${PYTHON_SITELIB_PACKAGES}/qpid/dispatch/stubs)

install(FILES ${PYTHON_ROUTER_SOURCES}
        DESTINATION ${PYTHON_SITELIB_PACKAGES}/qpid/dispatch/router)

install(FILES ${PYTHON_CONFIG_SOURCES}
        DESTINATION ${PYTHON_SITELIB_PACKAGES}/qpid/dispatch/config)

install(FILES python/qpid/__init__.py
        DESTINATION ${PYTHON_SITELIB_PACKAGES}/qpid)

install(FILES python/qpid/dispatch/__init__.py
        DESTINATION ${PYTHON_SITELIB_PACKAGES}/qpid/dispatch)

install(FILES ${DOC_FILES}
        DESTINATION ${SHARE_INSTALL_DIR}/qpid-dispatch)
##
## Build Tests
##
add_subdirectory(router)
add_subdirectory(tests)
