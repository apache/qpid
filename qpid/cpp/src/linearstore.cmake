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

if (DEFINED linearstore_force)
    set (linearstore_default ${linearstore_force})
else (DEFINED linearstore_force)
    set (linearstore_default OFF)
    if (UNIX)
        #
        # Find required BerkelyDB
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
		# find libuuid
		#
  	        CHECK_LIBRARY_EXISTS (uuid uuid_compare "" HAVE_UUID)
		CHECK_INCLUDE_FILES(uuid/uuid.h HAVE_UUID_H)
		IF (HAVE_UUID AND HAVE_UUID_H)
		    #
		    # allow linearstore to be built
		    #
		    set (linearstore_default ON)
		ENDIF (HAVE_UUID AND HAVE_UUID_H)
	    endif (HAVE_AIO AND HAVE_AIO_H)
        endif (DB_FOUND)
    endif (UNIX)
endif (DEFINED linearstore_force)

option(BUILD_LINEARSTORE "Build linearstore persistent store" ${linearstore_default})

if (BUILD_LINEARSTORE)
    if (NOT UNIX)
        message(FATAL_ERROR "Linearstore produced only on Unix platforms")
    endif (NOT UNIX)
    if (NOT DB_FOUND)
        message(FATAL_ERROR "Linearstore requires BerkeleyDB which is absent.")
    endif (NOT DB_FOUND)
    if (NOT HAVE_AIO)
        message(FATAL_ERROR "Linearstore requires libaio which is absent.")
    endif (NOT HAVE_AIO)
    if (NOT HAVE_AIO_H)
        message(FATAL_ERROR "Linearstore requires libaio.h which is absent.")
    endif (NOT HAVE_AIO_H)
    if (NOT HAVE_UUID)
        message(FATAL_ERROR "Linearstore requires uuid which is absent.")
    endif (NOT HAVE_UUID)
    if (NOT HAVE_UUID_H)
        message(FATAL_ERROR "Linearstore requires uuid.h which is absent.")
    endif (NOT HAVE_UUID_H)

    # Journal source files
    set (legacy_jrnl_SOURCES
        qpid/linearstore/jrnl/aio.cpp
        qpid/linearstore/jrnl/cvar.cpp
        qpid/linearstore/jrnl/data_tok.cpp
        qpid/linearstore/jrnl/deq_rec.cpp
        qpid/linearstore/jrnl/enq_map.cpp
        qpid/linearstore/jrnl/enq_rec.cpp
        qpid/linearstore/jrnl/fcntl.cpp
        qpid/linearstore/jrnl/jcntl.cpp
        qpid/linearstore/jrnl/jdir.cpp
        qpid/linearstore/jrnl/jerrno.cpp
        qpid/linearstore/jrnl/jexception.cpp
        qpid/linearstore/jrnl/jinf.cpp
        qpid/linearstore/jrnl/jrec.cpp
        qpid/linearstore/jrnl/lp_map.cpp
        qpid/linearstore/jrnl/lpmgr.cpp
        qpid/linearstore/jrnl/pmgr.cpp
        qpid/linearstore/jrnl/rmgr.cpp
        qpid/linearstore/jrnl/rfc.cpp
        qpid/linearstore/jrnl/rrfc.cpp
        qpid/linearstore/jrnl/slock.cpp
        qpid/linearstore/jrnl/smutex.cpp
        qpid/linearstore/jrnl/time_ns.cpp
        qpid/linearstore/jrnl/txn_map.cpp
        qpid/linearstore/jrnl/txn_rec.cpp
        qpid/linearstore/jrnl/wmgr.cpp
        qpid/linearstore/jrnl/wrfc.cpp
    )

    # linearstore source files
    set (legacy_store_SOURCES
        qpid/linearstore/StorePlugin.cpp
        qpid/linearstore/BindingDbt.cpp
        qpid/linearstore/BufferValue.cpp
        qpid/linearstore/DataTokenImpl.cpp
        qpid/linearstore/IdDbt.cpp
        qpid/linearstore/IdSequence.cpp
        qpid/linearstore/JournalImpl.cpp
        qpid/linearstore/MessageStoreImpl.cpp
        qpid/linearstore/PreparedTransaction.cpp
        qpid/linearstore/TxnCtxt.cpp
    )

    # linearstore include directories
    get_property(dirs DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR} PROPERTY INCLUDE_DIRECTORIES)
    set (legacy_include_DIRECTORIES
        ${dirs}
        ${CMAKE_CURRENT_SOURCE_DIR}/qpid/linearstore
    )

    if(NOT EXISTS ${CMAKE_CURRENT_BINARY_DIR}/db-inc.h)
      message(STATUS "Including BDB from ${DB_INCLUDE_DIR}/db_cxx.h")
        file(WRITE 
             ${CMAKE_CURRENT_BINARY_DIR}/db-inc.h
             "#include <${DB_INCLUDE_DIR}/db_cxx.h>\n")
    endif()

    add_library (linearstore MODULE
        ${legacy_jrnl_SOURCES}
        ${legacy_store_SOURCES}
        ${legacy_qmf_SOURCES}
    )

    set_target_properties (linearstore PROPERTIES
        PREFIX ""
        COMPILE_DEFINITIONS _IN_QPID_BROKER
        OUTPUT_NAME linearstore
        INCLUDE_DIRECTORIES "${legacy_include_DIRECTORIES}"
    )

    target_link_libraries (linearstore
        aio
        uuid
        qpidcommon qpidtypes qpidbroker
        ${DB_LIBRARY}
    )

install(TARGETS linearstore
        DESTINATION ${QPIDD_MODULE_DIR}
        COMPONENT ${QPID_COMPONENT_BROKER})

else (BUILD_LINEARSTORE)
    message(STATUS "Linearstore is excluded from build.")
endif (BUILD_LINEARSTORE)
