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
 * \file AsyncResultQueueImpl.h
 */

#ifndef qpid_broker_AsyncResultQueueImpl_h_
#define qpid_broker_AsyncResultQueueImpl_h_

#include "qpid/broker/AsyncStore.h"
#include "qpid/sys/PollableQueue.h"

namespace qpid {
namespace broker {

class AsyncResultHandle;

class AsyncResultQueueImpl : public AsyncResultQueue
{
public:
    AsyncResultQueueImpl(const boost::shared_ptr<qpid::sys::Poller>& poller);
    virtual ~AsyncResultQueueImpl();
    virtual void submit(boost::shared_ptr<AsyncResultHandle> arh);

private:
    typedef qpid::sys::PollableQueue<boost::shared_ptr<const AsyncResultHandle> > ResultQueue;
    ResultQueue m_resQueue;

    // Callback function for pollable queue, defined in qpid::sys::PollableQueue
    ResultQueue::Batch::const_iterator handle(const ResultQueue::Batch& e);
};

}} // namespace qpid::broker

#endif // qpid_broker_AsyncResultQueueImpl_h_
