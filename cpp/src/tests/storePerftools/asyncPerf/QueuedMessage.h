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

#include "qpid/broker/EnqueueHandle.h"

#include <boost/intrusive_ptr.hpp>

namespace tests {
namespace storePerftools {
namespace asyncPerf {

class SimplePersistableMessage;
class SimplePersistableQueue;

class QueuedMessage
{
public:
    QueuedMessage();
    QueuedMessage(SimplePersistableQueue* q,
                  boost::intrusive_ptr<SimplePersistableMessage> msg);
    QueuedMessage(const QueuedMessage& qm);
    ~QueuedMessage();
    QueuedMessage& operator=(const QueuedMessage& rhs);
    boost::intrusive_ptr<SimplePersistableMessage> payload() const;
    const qpid::broker::EnqueueHandle& enqHandle() const;
    qpid::broker::EnqueueHandle& enqHandle();

private:
    SimplePersistableQueue* m_queue;
    boost::intrusive_ptr<SimplePersistableMessage> m_msg;
    qpid::broker::EnqueueHandle m_enqHandle;
};

}}} // namespace tests::storePerfTools

#endif // tests_storePerftools_asyncPerf_QueuedMessage_h_
