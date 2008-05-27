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

#ifndef _Completion_
#define _Completion_

#include <boost/shared_ptr.hpp>
#include "Future.h"
#include "SessionImpl.h"

namespace qpid {
namespace client {

/** 
 * Returned by asynchronous commands that do not return any result.
 * You can use this to wait for an individual command to complete.
 * \clientapi
 */
class Completion
{
protected:
    Future future;
    shared_ptr<SessionImpl> session;

public:
    Completion() {}

    Completion(Future f, shared_ptr<SessionImpl> s) : future(f), session(s) {}

    /** Wait for the command to complete */
    void wait()
    {
        future.wait(*session);
    }

    bool isComplete() {
        return future.isComplete(*session);
    }
};

}}

#endif
