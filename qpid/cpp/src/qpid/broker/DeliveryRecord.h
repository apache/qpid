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
#ifndef _DeliveryRecord_
#define _DeliveryRecord_

#include <algorithm>
#include <list>
#include <qpid/broker/AccumulatedAck.h>
#include <qpid/broker/Message.h>
#include <qpid/broker/Prefetch.h>
#include <qpid/broker/Queue.h>

namespace qpid {
    namespace broker {
        class Channel;

        /**
         * Record of a delivery for which an ack is outstanding.
         */
        class DeliveryRecord{
            mutable Message::shared_ptr msg;
            mutable Queue::shared_ptr queue;
            string consumerTag;
            u_int64_t deliveryTag;
            bool pull;

        public:
            DeliveryRecord(Message::shared_ptr msg, Queue::shared_ptr queue, const string consumerTag, const u_int64_t deliveryTag);
            DeliveryRecord(Message::shared_ptr msg, Queue::shared_ptr queue, const u_int64_t deliveryTag);
            
            void discard(TransactionContext* ctxt = 0) const;
            bool matches(u_int64_t tag) const;
            bool coveredBy(const AccumulatedAck* const range) const;
            void requeue() const;
            void redeliver(Channel* const) const;
            void addTo(Prefetch* const prefetch) const;
            void subtractFrom(Prefetch* const prefetch) const;
        };

        typedef std::list<DeliveryRecord>::iterator ack_iterator; 
    }
}


#endif
