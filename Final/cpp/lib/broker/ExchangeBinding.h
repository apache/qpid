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
#ifndef _ExchangeBinding_
#define _ExchangeBinding_

#include <Binding.h>
#include <FieldTable.h>
#include <BrokerQueue.h>

namespace qpid {
    namespace broker {
        class Exchange;
        class Queue;

        class ExchangeBinding : public virtual Binding{
            Exchange* e;
            Queue::shared_ptr q;
            const string key;
            const qpid::framing::FieldTable* args;
        public:
            ExchangeBinding(Exchange* _e, Queue::shared_ptr _q, const string& _key, const qpid::framing::FieldTable* _args);
            virtual void cancel();
            virtual ~ExchangeBinding();
        };
    }
}


#endif

