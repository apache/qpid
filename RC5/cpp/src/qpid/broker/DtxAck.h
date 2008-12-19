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
#ifndef _DtxAck_
#define _DtxAck_

#include <algorithm>
#include <functional>
#include <list>
#include "qpid/framing/SequenceSet.h"
#include "DeliveryRecord.h"
#include "TxOp.h"

namespace qpid {
    namespace broker {
        class DtxAck : public TxOp{
            std::list<DeliveryRecord> pending;

        public:
            DtxAck(const framing::SequenceSet& acked, std::list<DeliveryRecord>& unacked);
            virtual bool prepare(TransactionContext* ctxt) throw();
            virtual void commit() throw();
            virtual void rollback() throw();
            virtual ~DtxAck(){}
            virtual void accept(TxOpConstVisitor& visitor) const { visitor(*this); }
        };
    }
}


#endif
