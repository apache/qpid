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

#include "Exchange.h"
#include "ExchangeRegistry.h"
#include "qpid/management/ManagementAgent.h"

using namespace qpid::broker;
using qpid::framing::Buffer;
using qpid::framing::FieldTable;
using qpid::management::ManagementAgent;
using qpid::management::ManagementObject;
using qpid::management::Manageable;
using qpid::management::Args;

Exchange::Exchange (const string& _name, Manageable* parent) :
    name(_name), durable(false), persistenceId(0)
{
    if (parent != 0)
    {
        ManagementAgent::shared_ptr agent = ManagementAgent::getAgent ();
        if (agent.get () != 0)
        {
            mgmtExchange = management::Exchange::shared_ptr
                (new management::Exchange (agent.get(), this, parent, _name, durable));
            agent->addObject (mgmtExchange);
        }
    }
}

Exchange::Exchange(const string& _name, bool _durable, const qpid::framing::FieldTable& _args,
                   Manageable* parent)
    : name(_name), durable(_durable), args(_args), alternateUsers(0), persistenceId(0)
{
    if (parent != 0)
    {
        ManagementAgent::shared_ptr agent = ManagementAgent::getAgent ();
        if (agent.get () != 0)
        {
            mgmtExchange = management::Exchange::shared_ptr
                (new management::Exchange (agent.get(), this, parent, _name, durable));
            if (!durable) {
                if (name == "")
                    agent->addObject (mgmtExchange, 4, 1);  // Special default exchange ID
                else if (name == "qpid.management")
                    agent->addObject (mgmtExchange, 5, 1);  // Special management exchange ID
                else
                    agent->addObject (mgmtExchange);
            }
        }
    }
}

Exchange::~Exchange ()
{
    if (mgmtExchange.get () != 0)
        mgmtExchange->resourceDestroy ();
}

void Exchange::setPersistenceId(uint64_t id) const
{
    if (mgmtExchange != 0 && persistenceId == 0)
    {
        ManagementAgent::shared_ptr agent = ManagementAgent::getAgent ();
        agent->addObject (mgmtExchange, id, 2);
    }
    persistenceId = id;
}

Exchange::shared_ptr Exchange::decode(ExchangeRegistry& exchanges, Buffer& buffer)
{
    string name;
    string type;
    FieldTable args;
    
    buffer.getShortString(name);
    bool durable(buffer.getOctet());
    buffer.getShortString(type);
    buffer.get(args);

    return exchanges.declare(name, type, durable, args).first;
}

void Exchange::encode(Buffer& buffer) const 
{
    buffer.putShortString(name);
    buffer.putOctet(durable);
    buffer.putShortString(getType());
    buffer.put(args);
}

uint32_t Exchange::encodedSize() const 
{ 
    return name.size() + 1/*short string size*/
        + 1 /*durable*/
        + getType().size() + 1/*short string size*/
        + args.size(); 
}

ManagementObject::shared_ptr Exchange::GetManagementObject (void) const
{
    return dynamic_pointer_cast<ManagementObject> (mgmtExchange);
}

Exchange::Binding::Binding(const string& _key, Queue::shared_ptr _queue, Exchange* parent,
                           FieldTable _args)
    : queue(_queue), key(_key), args(_args)
{
    if (parent != 0)
    {
        ManagementAgent::shared_ptr agent = ManagementAgent::getAgent ();
        if (agent.get() != 0)
        {
            ManagementObject::shared_ptr mo = queue->GetManagementObject();
            if (mo.get() != 0)
            {
                uint64_t queueId = mo->getObjectId();
                mgmtBinding = management::Binding::shared_ptr
                    (new management::Binding (agent.get(), this, (Manageable*) parent, queueId, key, args));
                agent->addObject (mgmtBinding);
            }
        }
    }
}

Exchange::Binding::~Binding ()
{
    if (mgmtBinding.get () != 0)
        mgmtBinding->resourceDestroy ();
}

ManagementObject::shared_ptr Exchange::Binding::GetManagementObject () const
{
    return dynamic_pointer_cast<ManagementObject> (mgmtBinding);
}

Manageable::status_t Exchange::Binding::ManagementMethod (uint32_t, Args&)
{
    return Manageable::STATUS_UNKNOWN_METHOD;
}
