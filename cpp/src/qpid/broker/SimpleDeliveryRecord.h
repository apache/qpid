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
 * \file SimpleDeliveryRecord.h
 */

#ifndef qpid_broker_SimpleDeliveryRecord_h_
#define qpid_broker_SimpleDeliveryRecord_h_

#include <boost/shared_ptr.hpp>

namespace qpid  {
namespace broker {

class SimpleConsumer;
class SimpleQueuedMessage;
class SimpleTxnBuffer;

class SimpleDeliveryRecord {
public:
    SimpleDeliveryRecord(boost::shared_ptr<SimpleQueuedMessage> qm,
                         SimpleConsumer& sc,
                         bool accepted);
    virtual ~SimpleDeliveryRecord();
    bool accept();
    bool isAccepted() const;
    bool setEnded();
    bool isEnded() const;
    bool isRedundant() const;
    void dequeue(qpid::broker::SimpleTxnBuffer* tb);
    void committed() const;
    boost::shared_ptr<SimpleQueuedMessage> getQueuedMessage() const;
private:
    boost::shared_ptr<SimpleQueuedMessage> m_queuedMessage;
    SimpleConsumer& m_msgConsumer;
    bool m_accepted : 1;
    bool m_ended : 1;
};

}} // namespace qpid::broker

#endif // qpid_broker_SimpleDeliveryRecord_h_
