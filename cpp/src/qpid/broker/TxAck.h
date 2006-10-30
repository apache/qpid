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
#ifndef _TxAck_
#define _TxAck_

#include <algorithm>
#include <functional>
#include <list>
#include "qpid/broker/AccumulatedAck.h"
#include "qpid/broker/DeliveryRecord.h"
#include "qpid/broker/TxOp.h"

namespace qpid {
    namespace broker {
        class TxAck : public TxOp{
            AccumulatedAck acked;
            std::list<DeliveryRecord>& unacked;
        public:
            TxAck(AccumulatedAck acked, std::list<DeliveryRecord>& unacked);
            virtual bool prepare() throw();
            virtual void commit() throw();
            virtual void rollback() throw();
            virtual ~TxAck(){}
        };
    }
}


#endif
