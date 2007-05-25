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
#ifndef _RecoveryManagerImpl_
#define _RecoveryManagerImpl_

#include <list>
#include "DtxManager.h"
#include "ExchangeRegistry.h"
#include "QueueRegistry.h"
#include "RecoveryManager.h"

namespace qpid {
namespace broker {

    class RecoveryManagerImpl : public RecoveryManager{
        QueueRegistry& queues;
        ExchangeRegistry& exchanges;
        DtxManager& dtxMgr;
        const uint64_t stagingThreshold;
    public:
        RecoveryManagerImpl(QueueRegistry& queues, ExchangeRegistry& exchanges, DtxManager& dtxMgr, uint64_t stagingThreshold);
        ~RecoveryManagerImpl();

        RecoverableExchange::shared_ptr recoverExchange(framing::Buffer& buffer);
        RecoverableQueue::shared_ptr recoverQueue(framing::Buffer& buffer);
        RecoverableMessage::shared_ptr recoverMessage(framing::Buffer& buffer);
        RecoverableTransaction::shared_ptr recoverTransaction(const std::string& xid, 
                                                              std::auto_ptr<TPCTransactionContext> txn);
        void recoveryComplete();

        static uint8_t decodeMessageType(framing::Buffer& buffer);
        static void encodeMessageType(const Message& msg, framing::Buffer& buffer);
        static uint32_t encodedMessageTypeSize();
    };

    
}
}


#endif
