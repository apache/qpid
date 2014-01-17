#ifndef _sys_windows_uuid_h
#define _sys_windows_uuid_h

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

#include "qpid/types/ImportExport.h"
#include <qpid/sys/IntegerTypes.h>

namespace qpid { namespace sys { const size_t UuidSize = 16; }}
typedef uint8_t uuid_t[qpid::sys::UuidSize];

QPID_TYPES_EXTERN void uuid_generate (uuid_t out);
QPID_TYPES_EXTERN int  uuid_parse (const char *in, uuid_t uu);  // Returns 0 on success, else -1
QPID_TYPES_EXTERN void uuid_unparse (const uuid_t uu, char *out);

#endif  /*!_sys_windows_uuid_h*/
