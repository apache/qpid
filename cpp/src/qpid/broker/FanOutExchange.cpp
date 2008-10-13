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
#include "FanOutExchange.h"
#include <algorithm>

using namespace qpid::broker;
using namespace qpid::framing;
using namespace qpid::sys;
namespace _qmf = qmf::org::apache::qpid::broker;

FanOutExchange::FanOutExchange(const std::string& _name, Manageable* _parent) :
    Exchange(_name, _parent)
{
    if (mgmtExchange != 0)
        mgmtExchange->set_type (typeName);
}

FanOutExchange::FanOutExchange(const std::string& _name, bool _durable,
                               const FieldTable& _args, Manageable* _parent) :
    Exchange(_name, _durable, _args, _parent)
{
    if (mgmtExchange != 0)
        mgmtExchange->set_type (typeName);
}

bool FanOutExchange::bind(Queue::shared_ptr queue, const string& /*key*/, const FieldTable* /*args*/)
{
    Binding::shared_ptr binding (new Binding ("", queue, this));
    if (bindings.add_unless(binding, MatchQueue(queue))) {
        if (mgmtExchange != 0) {
            mgmtExchange->inc_bindingCount();
            ((_qmf::Queue*) queue->GetManagementObject())->inc_bindingCount();
        }
        return true;
    } else {
        return false;
    }
}

bool FanOutExchange::unbind(Queue::shared_ptr queue, const string& /*key*/, const FieldTable* /*args*/)
{
    if (bindings.remove_if(MatchQueue(queue))) {
        if (mgmtExchange != 0) {
            mgmtExchange->dec_bindingCount();
            ((_qmf::Queue*) queue->GetManagementObject())->dec_bindingCount();
        }
        return true;
    } else {
        return false;
    }
}

void FanOutExchange::route(Deliverable& msg, const string& /*routingKey*/, const FieldTable* /*args*/){
    preRoute(msg);
    uint32_t count(0);

    BindingsArray::ConstPtr p = bindings.snapshot();
    if (p.get()){
        for(std::vector<Binding::shared_ptr>::const_iterator i = p->begin(); i != p->end(); ++i, count++){
            msg.deliverTo((*i)->queue);
            if ((*i)->mgmtBinding != 0)
                (*i)->mgmtBinding->inc_msgMatched ();
        }
    }
    
    if (mgmtExchange != 0)
    {
        mgmtExchange->inc_msgReceives  ();
        mgmtExchange->inc_byteReceives (msg.contentSize ());
        if (count == 0)
        {
            mgmtExchange->inc_msgDrops  ();
            mgmtExchange->inc_byteDrops (msg.contentSize ());
        }
        else
        {
            mgmtExchange->inc_msgRoutes  (count);
            mgmtExchange->inc_byteRoutes (count * msg.contentSize ());
        }
    }
}

bool FanOutExchange::isBound(Queue::shared_ptr queue, const string* const, const FieldTable* const)
{
    BindingsArray::ConstPtr ptr = bindings.snapshot();
    return ptr && std::find_if(ptr->begin(), ptr->end(), MatchQueue(queue)) != ptr->end();
}


FanOutExchange::~FanOutExchange() {}

const std::string FanOutExchange::typeName("fanout");
