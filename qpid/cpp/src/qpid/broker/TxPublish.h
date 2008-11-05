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
#ifndef _TxPublish_
#define _TxPublish_

#include "Queue.h"
#include "Deliverable.h"
#include "Message.h"
#include "MessageStore.h"
#include "TxOp.h"

#include <algorithm>
#include <functional>
#include <list>

#include <boost/intrusive_ptr.hpp>

namespace qpid {
    namespace broker {
        /**
         * Defines the behaviour for publish operations on a
         * transactional channel. Messages are routed through
         * exchanges when received but are not at that stage delivered
         * to the matching queues, rather the queues are held in an
         * instance of this class. On prepare() the message is marked
         * enqueued to the relevant queues in the MessagesStore. On
         * commit() the messages will be passed to the queue for
         * dispatch or to be added to the in-memory queue.
         */
        class TxPublish : public TxOp, public Deliverable{
            class Prepare{
                TransactionContext* ctxt;
                boost::intrusive_ptr<Message>& msg;
            public:
                Prepare(TransactionContext* ctxt, boost::intrusive_ptr<Message>& msg);
                void operator()(const boost::shared_ptr<Queue>& queue);            
            };

            class Commit{
                boost::intrusive_ptr<Message>& msg;
            public:
                Commit(boost::intrusive_ptr<Message>& msg);
                void operator()(const boost::shared_ptr<Queue>& queue);            
            };

            boost::intrusive_ptr<Message> msg;
            std::list<Queue::shared_ptr> queues;

        public:
            TxPublish(boost::intrusive_ptr<Message> msg);
            virtual bool prepare(TransactionContext* ctxt) throw();
            virtual void commit() throw();
            virtual void rollback() throw();

	    virtual Message& getMessage() { return *msg; };
            
            virtual void deliverTo(const boost::shared_ptr<Queue>& queue);

            virtual ~TxPublish(){}
            virtual void accept(TxOpConstVisitor& visitor) const { visitor(*this); }

            uint64_t contentSize();

            boost::intrusive_ptr<Message> getMessage() const { return msg; }
            const std::list<Queue::shared_ptr> getQueues() const { return queues; }
        };
    }
}


#endif
