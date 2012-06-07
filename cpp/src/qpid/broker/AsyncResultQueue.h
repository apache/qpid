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

/**
 * \file AsyncResultQueue.h
 */

#ifndef qpid_broker_AsyncResultQueue_h_
#define qpid_broker_AsyncResultQueue_h_

#include "qpid/sys/PollableQueue.h"

namespace qpid {
namespace broker {

class AsyncResultHandle;

class AsyncResultQueue
{
public:
    AsyncResultQueue(const boost::shared_ptr<qpid::sys::Poller>& poller);
    virtual ~AsyncResultQueue();
    void submit(AsyncResultHandle* rh);
//    static void submit(AsyncResultQueue* arq, AsyncResultHandle* rh);

protected:
    typedef qpid::sys::PollableQueue<const AsyncResultHandle*> ResultQueue;
    ResultQueue m_resQueue;

    ResultQueue::Batch::const_iterator handle(const ResultQueue::Batch& e);
};

}} // namespace qpid::broker

#endif // qpid_broker_AsyncResultQueue_h_
