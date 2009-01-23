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
#include "qpid/agent/ManagementAgent.h"
#include "qpid/management/ManagementBroker.h"
#include "qpid/log/Statement.h"
#include "qpid/framing/MessageProperties.h"
#include "DeliverableMessage.h"

using namespace qpid::broker;
using namespace qpid::framing;
using qpid::framing::Buffer;
using qpid::framing::FieldTable;
using qpid::sys::Mutex;
using qpid::management::ManagementAgent;
using qpid::management::ManagementBroker;
using qpid::management::ManagementObject;
using qpid::management::Manageable;
using qpid::management::Args;
namespace _qmf = qmf::org::apache::qpid::broker;

namespace 
{
const std::string qpidMsgSequence("qpid.msg_sequence");
const std::string qpidSequenceCounter("qpid.sequence_counter");
const std::string qpidIVE("qpid.ive");
const std::string qpidFedOp("qpid.fed.op");
const std::string qpidFedTags("qpid.fed.tags");
const std::string qpidFedOrigin("qpid.fed.origin");

const std::string fedOpBind("B");
const std::string fedOpUnbind("U");
const std::string fedOpReorigin("R");
const std::string fedOpHello("H");
}


Exchange::PreRoute::PreRoute(Deliverable& msg, Exchange* _p):parent(_p) {
    if (parent){
        if (parent->sequence || parent->ive) parent->sequenceLock.lock();
        
        if (parent->sequence){
            parent->sequenceNo++;
            msg.getMessage().getProperties<MessageProperties>()->getApplicationHeaders().setInt64(qpidMsgSequence,parent->sequenceNo); 
        } 
        if (parent->ive) {
            parent->lastMsg =  &( msg.getMessage());
        }
    }
}

Exchange::PreRoute::~PreRoute(){
    if (parent && (parent->sequence || parent->ive)){
        parent->sequenceLock.unlock();
    }
}

void Exchange::routeIVE(){
    if (ive && lastMsg.get()){
        DeliverableMessage dmsg(lastMsg);
        route(dmsg, lastMsg->getRoutingKey(), lastMsg->getApplicationHeaders());
    }
}


Exchange::Exchange (const string& _name, Manageable* parent) :
    name(_name), durable(false), persistenceId(0), sequence(false), 
    sequenceNo(0), ive(false), mgmtExchange(0)
{
    if (parent != 0)
    {
        ManagementAgent* agent = ManagementAgent::Singleton::getInstance();
        if (agent != 0)
        {
            mgmtExchange = new _qmf::Exchange (agent, this, parent, _name, durable);
            agent->addObject (mgmtExchange);
        }
    }
}

Exchange::Exchange(const string& _name, bool _durable, const qpid::framing::FieldTable& _args,
                   Manageable* parent)
    : name(_name), durable(_durable), args(_args), alternateUsers(0), persistenceId(0), 
      sequence(false), sequenceNo(0), ive(false), mgmtExchange(0)
{
    if (parent != 0)
    {
        ManagementAgent* agent = ManagementAgent::Singleton::getInstance();
        if (agent != 0)
        {
            mgmtExchange = new _qmf::Exchange (agent, this, parent, _name, durable);
            mgmtExchange->set_arguments(args);
            if (!durable) {
                if (name == "") {
                    agent->addObject (mgmtExchange, 0x1000000000000004LL);  // Special default exchange ID
                } else if (name == "qpid.management") {
                    agent->addObject (mgmtExchange, 0x1000000000000005LL);  // Special management exchange ID
                } else {
                    ManagementBroker* mb = dynamic_cast<ManagementBroker*>(agent);
                    agent->addObject (mgmtExchange, mb ? mb->allocateId(this) : 0);
                }
            }
        }
    }

    sequence = _args.get(qpidMsgSequence);
    if (sequence) {
        QPID_LOG(debug, "Configured exchange "+ _name +" with Msg sequencing");
        args.setInt64(std::string(qpidSequenceCounter), sequenceNo);
    }

    ive = _args.get(qpidIVE);
    if (ive) QPID_LOG(debug, "Configured exchange "+ _name +" with Initial Value");
}

Exchange::~Exchange ()
{
    if (mgmtExchange != 0)
        mgmtExchange->resourceDestroy ();
}

void Exchange::setPersistenceId(uint64_t id) const
{
    if (mgmtExchange != 0 && persistenceId == 0)
    {
        ManagementAgent* agent = ManagementAgent::Singleton::getInstance();
        agent->addObject (mgmtExchange, 0x2000000000000000LL + id);
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

    Exchange::shared_ptr exch = exchanges.declare(name, type, durable, args).first;
    exch->sequenceNo = args.getAsInt64(qpidSequenceCounter);
    return exch;
}

void Exchange::encode(Buffer& buffer) const 
{
    buffer.putShortString(name);
    buffer.putOctet(durable);
    buffer.putShortString(getType());
    if (args.isSet(qpidSequenceCounter))
        args.setInt64(std::string(qpidSequenceCounter),sequenceNo);
    buffer.put(args);
}

uint32_t Exchange::encodedSize() const 
{ 
    return name.size() + 1/*short string size*/
        + 1 /*durable*/
        + getType().size() + 1/*short string size*/
        + args.encodedSize();
}

ManagementObject* Exchange::GetManagementObject (void) const
{
    return (ManagementObject*) mgmtExchange;
}

void Exchange::registerDynamicBridge(DynamicBridge* db)
{
    if (!supportsDynamicBinding())
        throw Exception("Exchange type does not support dynamic binding");

    {
        Mutex::ScopedLock l(bridgeLock);
        for (std::vector<DynamicBridge*>::iterator iter = bridgeVector.begin();
             iter != bridgeVector.end(); iter++)
            (*iter)->sendReorigin();

        bridgeVector.push_back(db);
    }

    FieldTable args;
    args.setString(qpidFedOp, fedOpReorigin);
    bind(Queue::shared_ptr(), string(), &args);
}

void Exchange::removeDynamicBridge(DynamicBridge* db)
{
    Mutex::ScopedLock l(bridgeLock);
    for (std::vector<DynamicBridge*>::iterator iter = bridgeVector.begin();
         iter != bridgeVector.end(); iter++)
        if (*iter == db) {
            bridgeVector.erase(iter);
            break;
        }
}

void Exchange::handleHelloRequest()
{
}

void Exchange::propagateFedOp(const string& routingKey, const string& tags, const string& op, const string& origin)
{
    Mutex::ScopedLock l(bridgeLock);
    string myOp(op.empty() ? fedOpBind : op);

    for (std::vector<DynamicBridge*>::iterator iter = bridgeVector.begin();
         iter != bridgeVector.end(); iter++)
        (*iter)->propagateBinding(routingKey, tags, op, origin);
}

Exchange::Binding::Binding(const string& _key, Queue::shared_ptr _queue, Exchange* parent,
                           FieldTable _args, const string& origin)
    : queue(_queue), key(_key), args(_args), mgmtBinding(0)
{
    if (parent != 0)
    {
        ManagementAgent* agent = ManagementAgent::Singleton::getInstance();
        if (agent != 0)
        {
            ManagementObject* mo = queue->GetManagementObject();
            if (mo != 0)
            {
                management::ObjectId queueId = mo->getObjectId();
                mgmtBinding = new _qmf::Binding
                    (agent, this, (Manageable*) parent, queueId, key, args);
                if (!origin.empty())
                    mgmtBinding->set_origin(origin);
                ManagementBroker* mb = dynamic_cast<ManagementBroker*>(agent);
                agent->addObject (mgmtBinding, mb ? mb->allocateId(this) : 0);
            }
        }
    }
}

Exchange::Binding::~Binding ()
{
    if (mgmtBinding != 0)
        mgmtBinding->resourceDestroy ();
}

ManagementObject* Exchange::Binding::GetManagementObject () const
{
    return (ManagementObject*) mgmtBinding;
}

Exchange::MatchQueue::MatchQueue(Queue::shared_ptr q) : queue(q) {}

bool Exchange::MatchQueue::operator()(Exchange::Binding::shared_ptr b)
{
    return b->queue == queue;
}
