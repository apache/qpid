#ifndef _sys_uuid_h
#define _sys_uuid_h

/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#include "qpid/types/ImportExport.h"

#include "qpid/sys/IntegerTypes.h"

namespace qpid {
namespace sys {

const int UuidSize = 16;
typedef uint8_t uuid_t[UuidSize];

extern "C"
QPID_TYPES_EXTERN void uuid_generate (uint8_t out[UuidSize]);

}}

#endif /* _sys_uuid_h */
