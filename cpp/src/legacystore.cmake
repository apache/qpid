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
# Legacy store library CMake fragment, to be included in CMakeLists.txt
# 

#
# Find BDB
#
include (finddb.cmake)

# Journal source files
set (legacy_jrnl_SOURCES
    qpid/legacystore/jrnl/aio.cpp
    qpid/legacystore/jrnl/cvar.cpp
    qpid/legacystore/jrnl/data_tok.cpp
    qpid/legacystore/jrnl/deq_rec.cpp
    qpid/legacystore/jrnl/enq_map.cpp
    qpid/legacystore/jrnl/enq_rec.cpp
    qpid/legacystore/jrnl/fcntl.cpp
    qpid/legacystore/jrnl/jcntl.cpp
    qpid/legacystore/jrnl/jdir.cpp
    qpid/legacystore/jrnl/jerrno.cpp
    qpid/legacystore/jrnl/jexception.cpp
    qpid/legacystore/jrnl/jinf.cpp
    qpid/legacystore/jrnl/jrec.cpp
    qpid/legacystore/jrnl/lp_map.cpp
    qpid/legacystore/jrnl/lpmgr.cpp
    qpid/legacystore/jrnl/pmgr.cpp
    qpid/legacystore/jrnl/rmgr.cpp
    qpid/legacystore/jrnl/rfc.cpp
    qpid/legacystore/jrnl/rrfc.cpp
    qpid/legacystore/jrnl/slock.cpp
    qpid/legacystore/jrnl/smutex.cpp
    qpid/legacystore/jrnl/time_ns.cpp
    qpid/legacystore/jrnl/txn_map.cpp
    qpid/legacystore/jrnl/txn_rec.cpp
    qpid/legacystore/jrnl/wmgr.cpp
    qpid/legacystore/jrnl/wrfc.cpp
)

# legacyStore source files
set (legacy_store_SOURCES
    qpid/legacystore/StorePlugin.cpp
    qpid/legacystore/BindingDbt.cpp
    qpid/legacystore/BufferValue.cpp
    qpid/legacystore/DataTokenImpl.cpp
    qpid/legacystore/IdDbt.cpp
    qpid/legacystore/IdSequence.cpp
    qpid/legacystore/JournalImpl.cpp
    qpid/legacystore/MessageStoreImpl.cpp
    qpid/legacystore/PreparedTransaction.cpp
    qpid/legacystore/TxnCtxt.cpp
)

# legacyStore include directories
get_property(dirs DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR} PROPERTY INCLUDE_DIRECTORIES)
set (legacy_include_DIRECTORIES
    ${dirs}
    ${CMAKE_CURRENT_SOURCE_DIR}/qpid/legacystore
)

if (DB_FOUND)
    if (UNIX)
        if(NOT EXISTS ${CMAKE_CURRENT_BINARY_DIR}/db-inc.h)
            message(STATUS "Including BDB from ${DB_INCLUDE_DIR}/db_cxx.h")
            file(WRITE 
                ${CMAKE_CURRENT_BINARY_DIR}/db-inc.h
                "#include <${DB_INCLUDE_DIR}/db_cxx.h>")
        endif()

        add_library (legacystore SHARED
            ${legacy_jrnl_SOURCES}
            ${legacy_store_SOURCES}
            ${legacy_qmf_SOURCES}
        )
    
        set_target_properties (legacystore PROPERTIES
            PREFIX ""
            COMPILE_DEFINITIONS _IN_QPID_BROKER
            OUTPUT_NAME legacystore
            SOVERSION ${legacystore_version}
            INCLUDE_DIRECTORIES "${legacy_include_DIRECTORIES}"
        )

        target_link_libraries (legacystore
            aio
            rt
            uuid
            qpidcommon qpidtypes qpidbroker
            ${DB_LIBRARY}
        )
    else (UNIX)
        message(STATUS "Legacystore produced only on Unix platforms")
    endif (UNIX)
else (DB_FOUND)
    message(STATUS "BerkeleyDB not found. Legacystore is not produced")
endif (DB_FOUND)
