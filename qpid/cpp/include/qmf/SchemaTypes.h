#ifndef QMF_SCHEMA_TYPES_H
#define QMF_SCHEMA_TYPES_H
/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

#if !defined(QMF_USE_DEPRECATED_API) && !defined(qmf2_EXPORTS) && !defined(SWIG)
#  error "The API defined in this file has been DEPRECATED and will be removed in the future."
#  error "Define 'QMF_USE_DEPRECATED_API' to enable continued use of the API."
#endif

namespace qmf {

    const int SCHEMA_TYPE_DATA = 1;
    const int SCHEMA_TYPE_EVENT = 2;

    const int SCHEMA_DATA_VOID = 1;
    const int SCHEMA_DATA_BOOL = 2;
    const int SCHEMA_DATA_INT = 3;
    const int SCHEMA_DATA_FLOAT = 4;
    const int SCHEMA_DATA_STRING = 5;
    const int SCHEMA_DATA_MAP = 6;
    const int SCHEMA_DATA_LIST = 7;
    const int SCHEMA_DATA_UUID = 8;

    const int ACCESS_READ_CREATE = 1;
    const int ACCESS_READ_WRITE = 2;
    const int ACCESS_READ_ONLY = 3;

    const int DIR_IN = 1;
    const int DIR_OUT = 2;
    const int DIR_IN_OUT = 3;

    const int SEV_EMERG = 0;
    const int SEV_ALERT = 1;
    const int SEV_CRIT = 2;
    const int SEV_ERROR = 3;
    const int SEV_WARN = 4;
    const int SEV_NOTICE = 5;
    const int SEV_INFORM = 6;
    const int SEV_DEBUG = 7;
}

#endif
