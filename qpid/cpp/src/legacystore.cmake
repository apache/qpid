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

if (DEFINED legacystore_force)
    set (legacystore_default ${legacystore_force})
else (DEFINED legacystore_force)
    set (legacystore_default OFF)
    if (UNIX)
        #
        # Find required BerkeleyDB
        #
        include (finddb.cmake)
        if (DB_FOUND)
            #
            # find libaio
            #
            CHECK_LIBRARY_EXISTS (aio io_queue_init "" HAVE_AIO)
            CHECK_INCLUDE_FILES (libaio.h HAVE_AIO_H)
            if (HAVE_AIO AND HAVE_AIO_H)
                #
                # allow legacystore to be built
                #
                message(STATUS "BerkeleyDB for C++ and libaio found, Legacystore support disabled by default (deprecated, use linearstore instead).")
                set (legacystore_default OFF) # Disabled, deprecated. Use linearstore instead.
            else (HAVE_AIO AND HAVE_AIO_H)
                if (NOT HAVE_AIO)
                    message(STATUS "Legacystore requires libaio which is absent.")
                endif (NOT HAVE_AIO)
                if (NOT HAVE_AIO_H)
                    message(STATUS "Legacystore requires libaio.h which is absent.")
                endif (NOT HAVE_AIO_H)
            endif (HAVE_AIO AND HAVE_AIO_H)
        else (DB_FOUND)
            message(STATUS "Legacystore requires BerkeleyDB for C++ which is absent.")
        endif (DB_FOUND)
    endif (UNIX)
endif (DEFINED legacystore_force)

option(BUILD_LEGACYSTORE "Build legacystore persistent store" ${legacystore_default})

if (BUILD_LEGACYSTORE)
    if (NOT UNIX)
        message(FATAL_ERROR "Legacystore produced only on Unix platforms")
    endif (NOT UNIX)
    if (NOT DB_FOUND)
        message(FATAL_ERROR "Legacystore requires BerkeleyDB for C++ which is absent.")
    endif (NOT DB_FOUND)
    if (NOT HAVE_AIO)
        message(FATAL_ERROR "Legacystore requires libaio which is absent.")
    endif (NOT HAVE_AIO)
    if (NOT HAVE_AIO_H)
        message(FATAL_ERROR "Legacystore requires libaio.h which is absent.")
    endif (NOT HAVE_AIO_H)

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

    set (legacystore_defines RHM_CLEAN)

    if(NOT EXISTS ${CMAKE_CURRENT_BINARY_DIR}/db-inc.h)
      message(STATUS "Including BDB from ${DB_CXX_INCLUDE_DIR}/db_cxx.h")
        file(WRITE 
             ${CMAKE_CURRENT_BINARY_DIR}/db-inc.h
             "#include <${DB_CXX_INCLUDE_DIR}/db_cxx.h>\n")
    endif()

    add_library (legacystore MODULE
        ${legacy_jrnl_SOURCES}
        ${legacy_store_SOURCES}
        ${legacy_qmf_SOURCES}
    )

    set_target_properties (legacystore PROPERTIES
        PREFIX ""
        COMPILE_DEFINITIONS "${legacystore_defines}"
        OUTPUT_NAME legacystore
    )

    target_link_libraries (legacystore
        ${clock_gettime_LIB}
        aio
        uuid
        qpidcommon qpidtypes qpidbroker
        ${DB_LIBRARY}
    )

    # For use in the store tests only
    add_library (legacystore_shared SHARED
        ${legacy_jrnl_SOURCES}
        ${legacy_store_SOURCES}
        ${legacy_qmf_SOURCES}
    )

    set_target_properties (legacystore_shared PROPERTIES
        COMPILE_DEFINITIONS "${legacystore_defines}"
    )

    target_link_libraries (legacystore_shared
        ${clock_gettime_LIB}
        aio
        uuid
        qpidcommon qpidtypes qpidbroker
        ${DB_LIBRARY}
    )

install(TARGETS legacystore
        DESTINATION ${QPIDD_MODULE_DIR}
        COMPONENT ${QPID_COMPONENT_BROKER})

else (BUILD_LEGACYSTORE)
    message(STATUS "Legacystore is excluded from build.")
endif (BUILD_LEGACYSTORE)
