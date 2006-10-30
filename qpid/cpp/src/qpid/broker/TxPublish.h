/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
#ifndef _TxPublish_
#define _TxPublish_

#include <algorithm>
#include <functional>
#include <list>
#include "qpid/broker/Deliverable.h"
#include "qpid/broker/Message.h"
#include "qpid/broker/MessageStore.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/TxOp.h"

namespace qpid {
    namespace broker {
        class TxPublish : public TxOp, public Deliverable{
            class Prepare{
                Message::shared_ptr& msg;
                const string* const xid;
            public:
                Prepare(Message::shared_ptr& msg, const string* const xid);
                void operator()(Queue::shared_ptr& queue);            
            };

            class Commit{
                Message::shared_ptr& msg;
            public:
                Commit(Message::shared_ptr& msg);
                void operator()(Queue::shared_ptr& queue);            
            };

            Message::shared_ptr msg;
            std::list<Queue::shared_ptr> queues;

        public:
            TxPublish(Message::shared_ptr msg);
            virtual bool prepare() throw();
            virtual void commit() throw();
            virtual void rollback() throw();

            virtual void deliverTo(Queue::shared_ptr& queue);

            virtual ~TxPublish(){}
        };
    }
}


#endif
