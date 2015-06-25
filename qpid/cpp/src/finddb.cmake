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

if(UNIX)
# - Find BerkeleyDB
# Find the BerkeleyDB includes and library
# This module defines
# DB_CXX_INCLUDE_DIR, where to find db_cxx.h, etc.
# DB_LIBRARIES, the libraries needed to use BerkeleyDB.
# DB_FOUND, If false, do not try to use BerkeleyDB.
# also defined, but not for general use are
# DB_LIBRARY, where to find the BerkeleyDB library.

FIND_PATH(DB_CXX_INCLUDE_DIR db_cxx.h
    /usr/local/include/db4
    /usr/local/include/libdb4
    /usr/local/include
    /usr/include/db4
    /usr/include/libdb4
    /usr/include
)

SET(DB_NAMES ${DB_NAMES} db_cxx db_cxx-4)
FIND_LIBRARY(DB_LIBRARY
    NAMES ${DB_NAMES}
    PATHS /usr/lib /usr/local/lib
)

IF (DB_LIBRARY AND DB_CXX_INCLUDE_DIR)
    SET(DB_LIBRARIES ${DB_LIBRARY})
    SET(DB_FOUND "YES")
ELSE (DB_LIBRARY AND DB_CXX_INCLUDE_DIR)
    UNSET( DB_FOUND )
ENDIF (DB_LIBRARY AND DB_CXX_INCLUDE_DIR)


IF (DB_FOUND)
    IF (NOT DB_FIND_QUIETLY)
        MESSAGE(STATUS "Found BerkeleyDB: ${DB_LIBRARIES}")
    ENDIF (NOT DB_FIND_QUIETLY)
ELSE (DB_FOUND)
    IF (DB_FIND_REQUIRED)
        MESSAGE(FATAL_ERROR "Could not find BerkeleyDB library")
    ENDIF (DB_FIND_REQUIRED)
ENDIF (DB_FOUND)

# Deprecated declarations.
SET (NATIVE_DB_INCLUDE_PATH ${DB_CXX_INCLUDE_DIR} )
GET_FILENAME_COMPONENT (NATIVE_DB_LIB_PATH ${DB_LIBRARY} PATH)

MARK_AS_ADVANCED(
    DB_LIBRARY
    DB_CXX_INCLUDE_DIR
)

else(UNIX)
    MESSAGE(STATUS "BerkeleyDB is ignored on non-Unix platforms")
    UNSET( DB_FOUND )
endif(UNIX)
