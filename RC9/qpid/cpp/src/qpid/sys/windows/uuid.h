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

#include <Rpc.h>

#ifdef uuid_t   /*  Done in rpcdce.h */
#  undef uuid_t
#endif
#include <qpid/sys/IntegerTypes.h>
namespace qpid { namespace sys { const size_t UuidSize = 16; }}
typedef uint8_t uuid_t[qpid::sys::UuidSize];

void uuid_clear (uuid_t uu);
void uuid_copy (uuid_t dst, const uuid_t src);
void uuid_generate (uuid_t out);
int  uuid_is_null (const uuid_t uu);          // Returns 1 if null, else 0
int  uuid_parse (const char *in, uuid_t uu);  // Returns 0 on success, else -1
void uuid_unparse (const uuid_t uu, char *out);

#endif  /*!_sys_windows_uuid_h*/
