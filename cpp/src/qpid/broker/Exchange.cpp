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

#include "qpid/broker/Exchange.h"
#include "qpid/broker/ExchangeRegistry.h"
#include "qpid/broker/FedOps.h"
#include "qpid/broker/Broker.h"
#include "qpid/management/ManagementAgent.h"
#include "qpid/broker/Queue.h"
#include "qpid/log/Statement.h"
#include "qpid/framing/MessageProperties.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/broker/DeliverableMessage.h"

using namespace qpid::broker;
using namespace qpid::framing;
using qpid::framing::Buffer;
using qpid::framing::FieldTable;
using qpid::sys::Mutex;
using qpid::management::ManagementAgent;
using qpid::management::ManagementObject;
using qpid::management::Manageable;
using qpid::management::Args;
namespace _qmf = qmf::org::apache::qpid::broker;

namespace
{
    const std::string qpidMsgSequence("qpid.msg_sequence");
    const std::string qpidSequenceCounter("qpid.sequence_counter");
    const std::string qpidIVE("qpid.ive");
    const std::string QPID_MANAGEMENT("qpid.management");
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

void Exchange::doRoute(Deliverable& msg, ConstBindingList b)
{
    int count = 0;

    if (b.get()) {
        // Block the content release if the message is transient AND there is more than one binding
        if (!msg.getMessage().isPersistent() && b->size() > 1) {
            msg.getMessage().blockContentRelease();
        }

        for(std::vector<Binding::shared_ptr>::const_iterator i = b->begin(); i != b->end(); i++, count++) {
            msg.deliverTo((*i)->queue);
            if ((*i)->mgmtBinding != 0)
                (*i)->mgmtBinding->inc_msgMatched();
        }
    }

    if (mgmtExchange != 0)
    {
        mgmtExchange->inc_msgReceives  ();
        mgmtExchange->inc_byteReceives (msg.contentSize ());
        if (count == 0)
        {
            //QPID_LOG(warning, "Exchange " << getName() << " could not route message; no matching binding found");
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

void Exchange::routeIVE(){
    if (ive && lastMsg.get()){
        DeliverableMessage dmsg(lastMsg);
        route(dmsg, lastMsg->getRoutingKey(), lastMsg->getApplicationHeaders());
    }
}


Exchange::Exchange (const string& _name, Manageable* parent, Broker* b) :
    name(_name), durable(false), persistenceId(0), sequence(false),
    sequenceNo(0), ive(false), mgmtExchange(0), broker(b)
{
    if (parent != 0 && broker != 0)
    {
        ManagementAgent* agent = broker->getManagementAgent();
        if (agent != 0)
        {
            mgmtExchange = new _qmf::Exchange (agent, this, parent, _name);
            mgmtExchange->set_durable(durable);
            mgmtExchange->set_autoDelete(false);
            agent->addObject(mgmtExchange, 0, durable);
        }
    }
}

Exchange::Exchange(const string& _name, bool _durable, const qpid::framing::FieldTable& _args,
                   Manageable* parent, Broker* b)
    : name(_name), durable(_durable), alternateUsers(0), persistenceId(0),
      args(_args), sequence(false), sequenceNo(0), ive(false), mgmtExchange(0), broker(b)
{
    if (parent != 0 && broker != 0)
    {
        ManagementAgent* agent = broker->getManagementAgent();
        if (agent != 0)
        {
            mgmtExchange = new _qmf::Exchange (agent, this, parent, _name);
            mgmtExchange->set_durable(durable);
            mgmtExchange->set_autoDelete(false);
            mgmtExchange->set_arguments(ManagementAgent::toMap(args));
            agent->addObject(mgmtExchange, 0, durable);
        }
    }

    sequence = _args.get(qpidMsgSequence);
    if (sequence) {
        QPID_LOG(debug, "Configured exchange " <<  _name  << " with Msg sequencing");
        args.setInt64(std::string(qpidSequenceCounter), sequenceNo);
    }

    ive = _args.get(qpidIVE);
    if (ive) QPID_LOG(debug, "Configured exchange " <<  _name  << " with Initial Value");
}

Exchange::~Exchange ()
{
    if (mgmtExchange != 0)
        mgmtExchange->resourceDestroy ();
}

void Exchange::setAlternate(Exchange::shared_ptr _alternate)
{
    alternate = _alternate;
    if (mgmtExchange != 0) {
        if (alternate.get() != 0)
            mgmtExchange->set_altExchange(alternate->GetManagementObject()->getObjectId());
        else
            mgmtExchange->clr_altExchange();
    }
}

void Exchange::setPersistenceId(uint64_t id) const
{
    persistenceId = id;
}

Exchange::shared_ptr Exchange::decode(ExchangeRegistry& exchanges, Buffer& buffer)
{
    string name;
    string type;
    string altName;
    FieldTable args;

    buffer.getShortString(name);
    bool durable(buffer.getOctet());
    buffer.getShortString(type);
    buffer.get(args);
    // For backwards compatibility on restoring exchanges from before the alt-exchange update, perform check
    if (buffer.available())
        buffer.getShortString(altName);

    try {
        Exchange::shared_ptr exch = exchanges.declare(name, type, durable, args).first;
        exch->sequenceNo = args.getAsInt64(qpidSequenceCounter);
        exch->alternateName.assign(altName);
        return exch;
    } catch (const UnknownExchangeTypeException&) {
        QPID_LOG(warning, "Could not create exchange " << name << "; type " << type << " is not recognised");
        return Exchange::shared_ptr();
    }
}

void Exchange::encode(Buffer& buffer) const
{
    buffer.putShortString(name);
    buffer.putOctet(durable);
    buffer.putShortString(getType());
    if (args.isSet(qpidSequenceCounter))
        args.setInt64(std::string(qpidSequenceCounter),sequenceNo);
    buffer.put(args);
    buffer.putShortString(alternate.get() ? alternate->getName() : string(""));
}

uint32_t Exchange::encodedSize() const
{
    return name.size() + 1/*short string size*/
        + 1 /*durable*/
        + getType().size() + 1/*short string size*/
        + (alternate.get() ? alternate->getName().size() : 0) + 1/*short string size*/
        + args.encodedSize();
}

void Exchange::recoveryComplete(ExchangeRegistry& exchanges)
{
    if (!alternateName.empty()) {
        try {
            Exchange::shared_ptr ae = exchanges.get(alternateName);
            setAlternate(ae);
        } catch (const NotFoundException&) {
            QPID_LOG(warning, "Could not set alternate exchange \"" << alternateName << "\": does not exist.");
        }
    }
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

void Exchange::propagateFedOp(const string& routingKey, const string& tags, const string& op, const string& origin, qpid::framing::FieldTable* extra_args)
{
    Mutex::ScopedLock l(bridgeLock);
    string myOp(op.empty() ? fedOpBind : op);

    for (std::vector<DynamicBridge*>::iterator iter = bridgeVector.begin();
         iter != bridgeVector.end(); iter++)
        (*iter)->propagateBinding(routingKey, tags, op, origin, extra_args);
}

Exchange::Binding::Binding(const string& _key, Queue::shared_ptr _queue, Exchange* _parent,
                           FieldTable _args, const string& _origin)
    : parent(_parent), queue(_queue), key(_key), args(_args), origin(_origin), mgmtBinding(0)
{
}

Exchange::Binding::~Binding ()
{
    if (mgmtBinding != 0) {
        ManagementObject* mo = queue->GetManagementObject();
        if (mo != 0)
            static_cast<_qmf::Queue*>(mo)->dec_bindingCount();
        mgmtBinding->resourceDestroy ();
    }
}

void Exchange::Binding::startManagement()
{
    if (parent != 0)
    {
        Broker* broker = parent->getBroker();
        if (broker != 0) {
            ManagementAgent* agent = broker->getManagementAgent();
            if (agent != 0) {
                ManagementObject* mo = queue->GetManagementObject();
                if (mo != 0) {
                    management::ObjectId queueId = mo->getObjectId();

                    mgmtBinding = new _qmf::Binding
                        (agent, this, (Manageable*) parent, queueId, key, ManagementAgent::toMap(args));
                    if (!origin.empty())
                        mgmtBinding->set_origin(origin);
                    agent->addObject(mgmtBinding);
                    static_cast<_qmf::Queue*>(mo)->inc_bindingCount();
                }
            }
        }
    }
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

void Exchange::setProperties(const boost::intrusive_ptr<Message>& msg) {
    msg->getProperties<DeliveryProperties>()->setExchange(getName());
}

bool Exchange::routeWithAlternate(Deliverable& msg)
{
    route(msg, msg.getMessage().getRoutingKey(), msg.getMessage().getApplicationHeaders());
    if (!msg.delivered && alternate) {
        alternate->route(msg, msg.getMessage().getRoutingKey(), msg.getMessage().getApplicationHeaders());
    }
    return msg.delivered;
}
