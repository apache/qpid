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
#ifndef _HeadersExchange_
#define _HeadersExchange_

#include <vector>
#include "qpid/broker/Exchange.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/broker/Message.h"
#include "qpid/concurrent/MonitorImpl.h"
#include "qpid/broker/Queue.h"

namespace qpid {
namespace broker {


class HeadersExchange : public virtual Exchange {    
    typedef std::pair<qpid::framing::FieldTable, Queue::shared_ptr> Binding;
    typedef std::vector<Binding> Bindings;

    Bindings bindings;
    qpid::concurrent::MonitorImpl lock;

  public:
    static const std::string typeName;

    HeadersExchange(const string& name);
    
    virtual std::string getType(){ return typeName; }            
        
    virtual void bind(Queue::shared_ptr queue, const string& routingKey, qpid::framing::FieldTable* args);

    virtual void unbind(Queue::shared_ptr queue, const string& routingKey, qpid::framing::FieldTable* args);

    virtual void route(Deliverable& msg, const string& routingKey, qpid::framing::FieldTable* args);

    virtual ~HeadersExchange();

    static bool match(const qpid::framing::FieldTable& bindArgs, const qpid::framing::FieldTable& msgArgs);
};



}
}

#endif
