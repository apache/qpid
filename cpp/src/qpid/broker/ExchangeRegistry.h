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
#ifndef _ExchangeRegistry_
#define _ExchangeRegistry_

#include <map>
#include "qpid/broker/Exchange.h"
#include "qpid/concurrent/Monitor.h"

namespace qpid {
namespace broker {
    class ExchangeRegistry{
        typedef std::map<string, Exchange*> ExchangeMap;
        ExchangeMap exchanges;
        qpid::concurrent::Monitor* lock;
    public:
        ExchangeRegistry();
        void declare(Exchange* exchange);
        void destroy(const string& name);
        Exchange* get(const string& name);
        Exchange* getDefault();
        inline qpid::concurrent::Monitor* getLock(){ return lock; }
        ~ExchangeRegistry();
    };
}
}


#endif
