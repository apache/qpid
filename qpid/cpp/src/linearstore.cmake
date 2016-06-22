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
# Linear store library CMake fragment, to be included in CMakeLists.txt
# 

if (DEFINED linearstore_force)
    set (linearstore_default ${linearstore_force})
else (DEFINED linearstore_force)
    set (linearstore_default OFF)
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
                # allow linearstore to be built
                #
                message(STATUS "BerkeleyDB for C++ and libaio found, Linearstore support enabled.")
                set (linearstore_default ON)
            else (HAVE_AIO AND HAVE_AIO_H)
                if (NOT HAVE_AIO)
                    message(STATUS "Linearstore requires libaio which is absent.")
                endif (NOT HAVE_AIO)
                if (NOT HAVE_AIO_H)
                    message(STATUS "Linearstore requires libaio.h which is absent.")
                endif (NOT HAVE_AIO_H)
            endif (HAVE_AIO AND HAVE_AIO_H)
        else (DB_FOUND)
            message(STATUS "Linearstore requires BerkeleyDB for C++ which is absent.")
        endif (DB_FOUND)
    endif (UNIX)
endif (DEFINED linearstore_force)

option(BUILD_LINEARSTORE "Build linearstore persistent store" ${linearstore_default})

if (BUILD_LINEARSTORE)
    if (NOT UNIX)
        message(FATAL_ERROR "Linearstore produced only on Unix platforms")
    endif (NOT UNIX)
    if (NOT DB_FOUND)
        message(FATAL_ERROR "Linearstore requires BerkeleyDB for C++ which is absent.")
    endif (NOT DB_FOUND)
    if (NOT HAVE_AIO)
        message(FATAL_ERROR "Linearstore requires libaio which is absent.")
    endif (NOT HAVE_AIO)
    if (NOT HAVE_AIO_H)
        message(FATAL_ERROR "Linearstore requires libaio.h which is absent.")
    endif (NOT HAVE_AIO_H)

    # Journal source files
    set (linear_jrnl_SOURCES
        qpid/linearstore/journal/Checksum.cpp
        qpid/linearstore/journal/data_tok.cpp
        qpid/linearstore/journal/deq_rec.cpp
        qpid/linearstore/journal/EmptyFilePool.cpp
        qpid/linearstore/journal/EmptyFilePoolManager.cpp
        qpid/linearstore/journal/EmptyFilePoolPartition.cpp
        qpid/linearstore/journal/enq_map.cpp
        qpid/linearstore/journal/enq_rec.cpp
        qpid/linearstore/journal/jcntl.cpp
        qpid/linearstore/journal/jdir.cpp
        qpid/linearstore/journal/jerrno.cpp
        qpid/linearstore/journal/jexception.cpp
        qpid/linearstore/journal/JournalFile.cpp
        qpid/linearstore/journal/JournalLog.cpp
        qpid/linearstore/journal/LinearFileController.cpp
        qpid/linearstore/journal/pmgr.cpp
        qpid/linearstore/journal/RecoveryManager.cpp
        qpid/linearstore/journal/time_ns.cpp
        qpid/linearstore/journal/txn_map.cpp
        qpid/linearstore/journal/txn_rec.cpp
        qpid/linearstore/journal/wmgr.cpp
    )

    # linearstore source files
    set (linear_store_SOURCES
        qpid/linearstore/StorePlugin.cpp
        qpid/linearstore/BindingDbt.cpp
        qpid/linearstore/BufferValue.cpp
        qpid/linearstore/DataTokenImpl.cpp
        qpid/linearstore/IdDbt.cpp
        qpid/linearstore/IdSequence.cpp
        qpid/linearstore/JournalImpl.cpp
        qpid/linearstore/MessageStoreImpl.cpp
        qpid/linearstore/PreparedTransaction.cpp
        qpid/linearstore/JournalLogImpl.cpp
        qpid/linearstore/TxnCtxt.cpp
    )

    set (util_SOURCES
        qpid/linearstore/journal/utils/deq_hdr.c
        qpid/linearstore/journal/utils/enq_hdr.c
        qpid/linearstore/journal/utils/file_hdr.c
        qpid/linearstore/journal/utils/rec_hdr.c
        qpid/linearstore/journal/utils/rec_tail.c
        qpid/linearstore/journal/utils/txn_hdr.c
    )

    # linearstore include directories
    get_property(dirs DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR} PROPERTY INCLUDE_DIRECTORIES)
    set (linear_include_DIRECTORIES
        ${dirs}
        ${CMAKE_CURRENT_SOURCE_DIR}/qpid/linearstore
    )

    if(NOT EXISTS ${CMAKE_CURRENT_BINARY_DIR}/db-inc.h)
      message(STATUS "Including BDB from ${DB_CXX_INCLUDE_DIR}/db_cxx.h")
        file(WRITE 
             ${CMAKE_CURRENT_BINARY_DIR}/db-inc.h
             "#include <${DB_CXX_INCLUDE_DIR}/db_cxx.h>\n")
    endif()

    add_library (linearstoreutils SHARED
        ${util_SOURCES}
    )

    target_link_libraries (linearstoreutils
        rt
    )

    add_library (linearstore MODULE
        ${linear_jrnl_SOURCES}
        ${linear_store_SOURCES}
        ${linear_qmf_SOURCES}
    )

    set_target_properties (linearstore PROPERTIES
        PREFIX ""
        OUTPUT_NAME linearstore
        INCLUDE_DIRECTORIES "${linear_include_DIRECTORIES}"
    )

    target_link_libraries (linearstore
        aio
        uuid
        qpidcommon qpidtypes qpidbroker linearstoreutils
        ${DB_LIBRARY}
    )

  install(TARGETS linearstore
    DESTINATION ${QPIDD_MODULE_DIR}
    COMPONENT ${QPID_COMPONENT_BROKER})

  install (TARGETS linearstoreutils
    RUNTIME DESTINATION ${QPID_INSTALL_BINDIR}
    LIBRARY DESTINATION ${QPID_INSTALL_LIBDIR}
    ARCHIVE DESTINATION ${QPID_INSTALL_LIBDIR}
    COMPONENT ${QPID_COMPONENT_BROKER})

else (BUILD_LINEARSTORE)
    message(STATUS "Linearstore is excluded from build.")
endif (BUILD_LINEARSTORE)
