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
 * \file SimpleTxnPublish.h
 */

#ifndef qpid_broker_SimpleTxnPublish_h_
#define qpid_broker_SimpleTxnPublish_h_

#include "SimpleDeliverable.h"
#include "SimpleTxnOp.h"

#include <boost/intrusive_ptr.hpp>
#include <boost/shared_ptr.hpp>
#include <list>


namespace qpid {
namespace broker {

class SimpleQueuedMessage;
class SimpleMessage;
class SimpleQueue;

class SimpleTxnPublish : public SimpleTxnOp,
                         public SimpleDeliverable
{
public:
    SimpleTxnPublish(boost::intrusive_ptr<SimpleMessage> msg);
    virtual ~SimpleTxnPublish();

    // --- Interface TxOp ---
    bool prepare(SimpleTxnBuffer* tb) throw();
    void commit() throw();
    void rollback() throw();

    // --- Interface Deliverable ---
    uint64_t contentSize();
    void deliverTo(const boost::shared_ptr<SimpleQueue>& queue);
    SimpleMessage& getMessage();

private:
    boost::intrusive_ptr<SimpleMessage> m_msg;
    std::list<boost::shared_ptr<SimpleQueuedMessage> > m_queues;
    std::list<boost::shared_ptr<SimpleQueuedMessage> > m_prepared;
};

}} // namespace qpid::broker

#endif // qpid_broker_SimpleTxnPublish_h_
