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
 * \file SimpleMessageAsyncContext.h
 */

#ifndef qpid_broker_SimpleMessageAsyncContext_h_
#define qpid_broker_SimpleMessageAsyncContext_h_

#include "AsyncStore.h" // BrokerAsyncContext

#include <boost/intrusive_ptr.hpp>
#include <boost/shared_ptr.hpp>

namespace qpid  {
namespace broker {

class SimpleMessage;
class SimpleQueue;

class SimpleMessageAsyncContext : public BrokerAsyncContext
{
public:
    SimpleMessageAsyncContext(boost::intrusive_ptr<SimpleMessage> msg,
                              boost::shared_ptr<SimpleQueue> q);
    virtual ~SimpleMessageAsyncContext();
    boost::intrusive_ptr<SimpleMessage> getMessage() const;
    boost::shared_ptr<SimpleQueue> getQueue() const;
    void destroy();

private:
    boost::intrusive_ptr<SimpleMessage> m_msg;
    boost::shared_ptr<SimpleQueue> m_q;
};

}} // namespace qpid::broker

#endif // qpid_broker_SimpleMessageAsyncContext_h_
