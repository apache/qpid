#ifndef QPID_MESSAGING_SHUTDOWN_H
#define QPID_MESSAGING_SHUTDOWN_H

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
 */

#include "qpid/messaging/ImportExport.h"

namespace qpid {
namespace messaging {

/** Shut down the qpid::messaging library, clean up resources and stop background threads.
 * Note you cannot use any of the qpid::messaging classes or functions after calling this.
 *
 * It is is not normally necessary to call this, the library cleans up automatically on process exit.
 * You can use it to clean up resources early in unusual situations.
 */
QPID_MESSAGING_EXTERN void shutdown();

}}

#endif // QPID_MESSAGING_SHUTDOWN_H
