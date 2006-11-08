#ifndef _concurrent_Time_h
#define _concurrent_Time_h

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

#include <stdint.h>

namespace qpid {
namespace sys {

inline int64_t msecsToNsecs(int64_t msecs) { return msecs * 1000 *1000; }
inline int64_t nsecsToMsecs(int64_t nsecs) { return nsecs / (1000 *1000); }

/** Nanoseconds since epoch */
int64_t getTimeNsecs();

/** Milliseconds since epoch */
int64_t getTimeMsecs();

}}

#endif  /*!_concurrent_Time_h*/
