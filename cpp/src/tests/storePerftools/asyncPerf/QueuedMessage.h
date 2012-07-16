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
 * \file QueuedMessage.h
 */

#ifndef tests_storePerftools_asyncPerf_QueuedMessage_h_
#define tests_storePerftools_asyncPerf_QueuedMessage_h_

#include "qpid/broker/AsyncStore.h"

#include <boost/enable_shared_from_this.hpp>
#include <boost/intrusive_ptr.hpp>

namespace qpid {
namespace broker {

class TxnHandle;

}}

namespace tests {
namespace storePerftools {
namespace asyncPerf {

class SimpleMessage;
class SimpleQueue;

class QueuedMessage : public boost::enable_shared_from_this<QueuedMessage>
{
public:
    QueuedMessage();
    QueuedMessage(SimpleQueue* q,
                  boost::intrusive_ptr<SimpleMessage> msg);
    QueuedMessage(const QueuedMessage& qm);
    QueuedMessage(QueuedMessage* const qm);
    virtual ~QueuedMessage();
    SimpleQueue* getQueue() const;
    boost::intrusive_ptr<SimpleMessage> payload() const;

    // -- Transaction handling ---
    void prepareEnqueue(qpid::broker::TxnHandle& th);
    void commitEnqueue();
    void abortEnqueue();

private:
    SimpleQueue* m_queue;
    boost::intrusive_ptr<SimpleMessage> m_msg;
};

}}} // namespace tests::storePerfTools

#endif // tests_storePerftools_asyncPerf_QueuedMessage_h_
