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
#ifndef _TxAck_
#define _TxAck_

#include <algorithm>
#include <functional>
#include <list>
#include <AccumulatedAck.h>
#include <DeliveryRecord.h>
#include <TxOp.h>

namespace qpid {
    namespace broker {
        /**
         * Defines the transactional behaviour for acks received by a
         * transactional channel.
         */
        class TxAck : public TxOp{
            AccumulatedAck& acked;
            std::list<DeliveryRecord>& unacked;
            const std::string* const xid;

        public:
            /**
             * @param acked a representation of the accumulation of
             * acks received
             * @param unacked the record of delivered messages
             */
            TxAck(AccumulatedAck& acked, std::list<DeliveryRecord>& unacked, const std::string* const xid = 0);
            virtual bool prepare(TransactionContext* ctxt) throw();
            virtual void commit() throw();
            virtual void rollback() throw();
            virtual ~TxAck(){}
        };
    }
}


#endif
