#ifndef QPID_SYS_POSIX_POLLABLECONDITION_H
#define QPID_SYS_POSIX_POLLABLECONDITION_H

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

#include "qpid/sys/IOHandle.h"

namespace qpid {
namespace sys {

/**
 * A pollable condition to integrate in-process conditions with IO
 * conditions in a polling loop.
 *
 * Setting the condition makes it readable for a poller.
 * 
 * Writable/disconnected conditions are undefined and should not be
 * polled for.
 */
class PollableCondition : public sys::IOHandle {
  public:
    PollableCondition();

    /** Set the condition, triggers readable in a poller. */ 
    void set();

    /** Get the current state of the condition, then clear it.
     *@return The state of the condition before it was cleared.
     */
    bool clear();

  private:
    int writeFd;
};
}} // namespace qpid::sys

#endif  /*!QPID_SYS_POSIX_POLLABLECONDITION_H*/
