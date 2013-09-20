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
#ifndef _RecoveredEnqueue_
#define _RecoveredEnqueue_

#include "qpid/broker/Deliverable.h"
#include "qpid/broker/Message.h"
#include "qpid/broker/MessageStore.h"
#include "qpid/broker/TxOp.h"

#include <algorithm>
#include <functional>
#include <list>

namespace qpid {
namespace broker {
class RecoveredEnqueue : public TxOp{
    boost::shared_ptr<Queue> queue;
    Message msg;

  public:
    RecoveredEnqueue(boost::shared_ptr<Queue> queue, Message msg);
    virtual bool prepare(TransactionContext* ctxt) throw();
    virtual void commit() throw();
    virtual void rollback() throw();
    // TODO aconway 2013-07-08: revisit
    virtual void callObserver(const boost::shared_ptr<TransactionObserver>&) {}
    virtual ~RecoveredEnqueue(){}

    boost::shared_ptr<Queue> getQueue() const { return queue; }
    Message getMessage() const { return msg; }
};
}
}


#endif
