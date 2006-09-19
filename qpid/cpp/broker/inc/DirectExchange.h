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
#ifndef _DirectExchange_
#define _DirectExchange_

#include <map>
#include <vector>
#include "Exchange.h"
#include "FieldTable.h"
#include "Message.h"
#include "MonitorImpl.h"
#include "Queue.h"

namespace qpid {
namespace broker {
    class DirectExchange : public virtual Exchange{
        const string name;
        std::map<string, std::vector<Queue::shared_ptr> > bindings;
        qpid::concurrent::MonitorImpl lock;

    public:
        static const std::string typeName;
        
        DirectExchange(const string& name);
        
        inline virtual const string& getName(){ return name; }
        
        virtual void bind(Queue::shared_ptr queue, const string& routingKey, qpid::framing::FieldTable* args);

        virtual void unbind(Queue::shared_ptr queue, const string& routingKey, qpid::framing::FieldTable* args);

        virtual void route(Message::shared_ptr& msg, const string& routingKey, qpid::framing::FieldTable* args);

        virtual ~DirectExchange();
    };
}
}


#endif
