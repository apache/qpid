#ifndef QPID_SYS_SYSTEMINFO_H
#define QPID_SYS_SYSTEMINFO_H

/*
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

namespace qpid {
namespace sys {

/**
 * Retrieve information about the system we are running on.
 * Results may be dependent on OS/hardware. 
 */
class SystemInfo
{
  public:
    /** Estimate available concurrency, e.g. number of CPU cores.
     * -1 means estimate not available on this platform.
     */
    static long concurrency();
};

}} // namespace qpid::sys



#endif  /*!QPID_SYS_SYSTEMINFO_H*/
