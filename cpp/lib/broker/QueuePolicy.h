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
#ifndef _QueuePolicy_
#define _QueuePolicy_

#include <BrokerMessage.h>

namespace qpid {
    namespace broker {
        class QueuePolicy
        {
            const u_int32_t maxCount;
            const u_int64_t maxSize;
            u_int32_t count;
            u_int64_t size;
            
            bool checkCount(Message::shared_ptr& msg);
            bool checkSize(Message::shared_ptr& msg);
        public:
            QueuePolicy(u_int32_t maxCount, u_int64_t maxSize);
            void enqueued(Message::shared_ptr& msg, MessageStore* store);
            void dequeued(Message::shared_ptr& msg, MessageStore* store);
        };
    }
}


#endif
