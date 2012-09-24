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
 * \file QueueAsyncContext.h
 */

#ifndef qpid_broker_QueueAsyncContext_h_
#define qpid_broker_QueueAsyncContext_h_

#include "qpid/broker/AsyncResultHandle.h"
#include "qpid/broker/AsyncStore.h"
#include "qpid/broker/TxnHandle.h"

#include "qpid/asyncStore/AsyncOperation.h"

#include <boost/intrusive_ptr.hpp>
#include <boost/shared_ptr.hpp>

namespace qpid {
namespace broker {

//class PersistableMessage;
class PersistableQueue;

typedef void (*AsyncResultCallback)(const AsyncResultHandle* const);

class QueueAsyncContext: public BrokerAsyncContext
{
public:
    QueueAsyncContext(boost::shared_ptr<PersistableQueue> q,
                      AsyncResultCallback rcb,
                      AsyncResultQueue* const arq);
/*    QueueAsyncContext(boost::shared_ptr<PersistableQueue> q,
                      boost::intrusive_ptr<PersistableMessage> msg,
                      AsyncResultCallback rcb,
                      AsyncResultQueue* const arq);*/
    QueueAsyncContext(boost::shared_ptr<PersistableQueue> q,
                      SimpleTxnBuffer* tb,
                      AsyncResultCallback rcb,
                      AsyncResultQueue* const arq);
/*    QueueAsyncContext(boost::shared_ptr<PersistableQueue> q,
                      boost::intrusive_ptr<PersistableMessage> msg,
                      SimpleTxnBuffer* tb,
                      AsyncResultCallback rcb,
                      AsyncResultQueue* const arq);*/
    virtual ~QueueAsyncContext();
    boost::shared_ptr<PersistableQueue> getQueue() const;
//    boost::intrusive_ptr<PersistableMessage> getMessage() const;
    SimpleTxnBuffer* getTxnBuffer() const;
    AsyncResultQueue* getAsyncResultQueue() const;
    AsyncResultCallback getAsyncResultCallback() const;
    void invokeCallback(const AsyncResultHandle* const arh) const;
    void destroy();

private:
    boost::shared_ptr<PersistableQueue> m_q;
//    boost::intrusive_ptr<PersistableMessage> m_msg;
    SimpleTxnBuffer* m_tb;
    AsyncResultCallback m_rcb;
    AsyncResultQueue* const m_arq;
};

}} // namespace qpid::broker

#endif // qpid_broker_QueueAsyncContext_h_
