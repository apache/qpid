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

#include "qpid/broker/Broker.h"
#include "qpid/broker/DeliverableMessage.h"
#include "qpid/broker/Exchange.h"
#include "qpid/broker/ExchangeRegistry.h"
#include "qpid/broker/FedOps.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/amqp_0_10/MessageTransfer.h"
#include "qpid/framing/MessageProperties.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/log/Statement.h"
#include "qpid/management/ManagementAgent.h"
#include "qpid/sys/ExceptionHolder.h"
#include <stdexcept>

namespace qpid {
namespace broker {

using std::string;

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
            msg.getMessage().addAnnotation(qpidMsgSequence,parent->sequenceNo);
        }
        if (parent->ive) {
            parent->lastMsg = msg.getMessage();
        }
    }
}

Exchange::PreRoute::~PreRoute(){
    if (parent && (parent->sequence || parent->ive)){
        parent->sequenceLock.unlock();
    }
}

namespace {
/** Store information about an exception to be thrown later.
 * If multiple exceptions are stored, save the first of the "most severe"
 * exceptions, SESSION is les sever than CONNECTION etc.
 */
class  ExInfo {
  public:
    enum Type { NONE, SESSION, CONNECTION, OTHER };

    ExInfo(string exchange) : type(NONE), exchange(exchange) {}
    void store(Type type_, const qpid::sys::ExceptionHolder& exception_, const boost::shared_ptr<Queue>& queue) {
        QPID_LOG(warning, "Exchange " << exchange << " cannot deliver to  queue "
                 <<  queue->getName() << ": " << exception_.what());
        if (type < type_) {     // Replace less severe exception
            type = type_;
            exception = exception_;
        }
    }

    void raise() {
        exception.raise();
    }

  private:
    Type type;
    string exchange;
    qpid::sys::ExceptionHolder exception;
};
}

void Exchange::doRoute(Deliverable& msg, ConstBindingList b)
{
    int count = 0;

    if (b.get()) {
        ExInfo error(getName()); // Save exception to throw at the end.
        for(std::vector<Binding::shared_ptr>::const_iterator i = b->begin(); i != b->end(); i++, count++) {
            try {
                msg.deliverTo((*i)->queue);
                if ((*i)->mgmtBinding != 0)
                    (*i)->mgmtBinding->inc_msgMatched();
            }
            catch (const SessionException& e) {
                error.store(ExInfo::SESSION, framing::createSessionException(e.code, e.what()),(*i)->queue);
            }
            catch (const ConnectionException& e) {
                error.store(ExInfo::CONNECTION, framing::createConnectionException(e.code, e.what()), (*i)->queue);
            }
            catch (const std::exception& e) {
                error.store(ExInfo::OTHER, qpid::sys::ExceptionHolder(new Exception(e.what())), (*i)->queue);
            }
        }
        error.raise();
    }

    if (mgmtExchange != 0)
    {
        qmf::org::apache::qpid::broker::Exchange::PerThreadStats *eStats = mgmtExchange->getStatistics();
        uint64_t contentSize = msg.getMessage().getMessageSize();

        eStats->msgReceives += 1;
        eStats->byteReceives += contentSize;
        if (count == 0)
        {
            //QPID_LOG(warning, "Exchange " << getName() << " could not route message; no matching binding found");
            eStats->msgDrops += 1;
            eStats->byteDrops += contentSize;
            if (brokerMgmtObject)
                brokerMgmtObject->inc_discardsNoRoute();
        }
        else
        {
            eStats->msgRoutes += count;
            eStats->byteRoutes += count * contentSize;
        }

        mgmtExchange->statisticsUpdated();
    }
}

void Exchange::routeIVE(){
    if (ive && lastMsg){
        DeliverableMessage dmsg(lastMsg, 0);
        route(dmsg);
    }
}


Exchange::Exchange (const string& _name, Manageable* parent, Broker* b) :
    name(_name), durable(false), autodelete(false), alternateUsers(0), otherUsers(0), persistenceId(0), sequence(false),
    sequenceNo(0), ive(false), broker(b), destroyed(false)
{
    if (parent != 0 && broker != 0)
    {
        ManagementAgent* agent = broker->getManagementAgent();
        if (agent != 0)
        {
            mgmtExchange = _qmf::Exchange::shared_ptr(new _qmf::Exchange (agent, this, parent, _name));
            mgmtExchange->set_durable(durable);
            mgmtExchange->set_autoDelete(autodelete);
            agent->addObject(mgmtExchange, 0, durable);
            if (broker)
                brokerMgmtObject = boost::dynamic_pointer_cast<qmf::org::apache::qpid::broker::Broker>(broker->GetManagementObject());
        }
    }
}

Exchange::Exchange(const string& _name, bool _durable, bool _autodelete, const qpid::framing::FieldTable& _args,
                   Manageable* parent, Broker* b)
    : name(_name), durable(_durable), autodelete(_autodelete), alternateUsers(0), otherUsers(0), persistenceId(0),
      args(_args), sequence(false), sequenceNo(0), ive(false), broker(b), destroyed(false)
{
    if (parent != 0 && broker != 0)
    {
        ManagementAgent* agent = broker->getManagementAgent();
        if (agent != 0)
        {
            mgmtExchange = _qmf::Exchange::shared_ptr(new _qmf::Exchange (agent, this, parent, _name));
            mgmtExchange->set_durable(durable);
            mgmtExchange->set_autoDelete(autodelete);
            mgmtExchange->set_arguments(ManagementAgent::toMap(args));
            agent->addObject(mgmtExchange, 0, durable);
            if (broker)
                brokerMgmtObject = boost::dynamic_pointer_cast<qmf::org::apache::qpid::broker::Broker>(broker->GetManagementObject());
        }
    }

    sequence = !!_args.get(qpidMsgSequence);
    if (sequence) {
        QPID_LOG(debug, "Configured exchange " <<  _name  << " with Msg sequencing");
        args.setInt64(std::string(qpidSequenceCounter), sequenceNo);
    }

    ive = !!_args.get(qpidIVE);
    if (ive) {
        QPID_LOG(debug, "Configured exchange " <<  _name  << " with Initial Value");
    }
}

Exchange::~Exchange ()
{
    if (mgmtExchange != 0)
        mgmtExchange->resourceDestroy ();
}

void Exchange::setAlternate(Exchange::shared_ptr _alternate)
{
    alternate = _alternate;
    alternate->incAlternateUsers();
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
    // Check autodelete bool; for backwards compatibility if the bool isn't present, assume false
    bool _autodelete = ((buffer.available()) && (buffer.getInt8()));

    try {
        Exchange::shared_ptr exch = exchanges.declare(name, type, durable, _autodelete, args).first;
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
    buffer.putInt8(isAutoDelete());
}

uint32_t Exchange::encodedSize() const
{
    return name.size() + 1/*short string size*/
        + 1 /*durable*/
        + getType().size() + 1/*short string size*/
        + (alternate.get() ? alternate->getName().size() : 0) + 1/*short string size*/
        + args.encodedSize()
        + 1 /* autodelete bool as int_8 */;
}

void Exchange::recoveryComplete(ExchangeRegistry& exchanges)
{
    if (!alternateName.empty()) {
        Exchange::shared_ptr ae = exchanges.find(alternateName);
        if (ae) setAlternate(ae);
        else QPID_LOG(warning, "Could not set alternate exchange \""
                      << alternateName << "\": does not exist.");
    }
}

ManagementObject::shared_ptr Exchange::GetManagementObject (void) const
{
    return mgmtExchange;
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
    : parent(_parent), queue(_queue), key(_key), args(_args), origin(_origin)
{
}

Exchange::Binding::~Binding ()
{
    if (mgmtBinding != 0) {
        mgmtBinding->debugStats("destroying");
        _qmf::Queue::shared_ptr mo = boost::dynamic_pointer_cast<_qmf::Queue>(queue->GetManagementObject());
        if (mo != 0)
            mo->dec_bindingCount();
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
                _qmf::Queue::shared_ptr mo = boost::dynamic_pointer_cast<_qmf::Queue>(queue->GetManagementObject());
                if (mo != 0) {
                    management::ObjectId queueId = mo->getObjectId();

                    mgmtBinding = _qmf::Binding::shared_ptr(new _qmf::Binding
                        (agent, this, (Manageable*) parent, queueId, key, ManagementAgent::toMap(args)));
                    if (!origin.empty())
                        mgmtBinding->set_origin(origin);
                    agent->addObject(mgmtBinding);
                    mo->inc_bindingCount();
                }
            }
        }
    }
}

ManagementObject::shared_ptr Exchange::Binding::GetManagementObject () const
{
    return mgmtBinding;
}

Exchange::MatchQueue::MatchQueue(Queue::shared_ptr q) : queue(q) {}

bool Exchange::MatchQueue::operator()(Exchange::Binding::shared_ptr b)
{
    return b->queue == queue;
}

//void Exchange::setProperties(Message& msg) {
//    qpid::broker::amqp_0_10::MessageTransfer::setExchange(msg, getName());
//}

bool Exchange::routeWithAlternate(Deliverable& msg)
{
    route(msg);
    if (!msg.delivered && alternate) {
        alternate->route(msg);
    }
    return msg.delivered;
}

void Exchange::setArgs(const framing::FieldTable& newArgs) {
    args = newArgs;
    if (mgmtExchange) mgmtExchange->set_arguments(ManagementAgent::toMap(args));
}

void Exchange::checkAutodelete()
{
    if (autodelete && !inUse() && broker) {
        broker->getExchanges().destroy(name);
    }
}
void Exchange::incAlternateUsers()
{
    Mutex::ScopedLock l(usersLock);
    alternateUsers++;
}

void Exchange::decAlternateUsers()
{
    Mutex::ScopedLock l(usersLock);
    alternateUsers--;
}

bool Exchange::inUseAsAlternate()
{
    Mutex::ScopedLock l(usersLock);
    return alternateUsers > 0;
}

void Exchange::incOtherUsers()
{
    Mutex::ScopedLock l(usersLock);
    otherUsers++;
}
void Exchange::decOtherUsers(bool isControllingLink=false)
{
    Mutex::ScopedLock l(usersLock);
    assert(otherUsers);
    if (otherUsers) otherUsers--;
    if (autodelete) {
        if (isControllingLink) {
            if (broker) broker->getExchanges().destroy(name);
        } else if (!inUse() && !hasBindings()) {
            checkAutodelete();
        }
    }
}
bool Exchange::inUse() const
{
    Mutex::ScopedLock l(usersLock);
    return alternateUsers > 0 || otherUsers > 0;
}
void Exchange::setDeletionListener(const std::string& key, boost::function0<void> listener)
{
    Mutex::ScopedLock l(usersLock);
    if (listener) deletionListeners[key] = listener;
}
void Exchange::unsetDeletionListener(const std::string& key)
{
    Mutex::ScopedLock l(usersLock);
    deletionListeners.erase(key);
}

void Exchange::destroy()
{
    std::map<std::string, boost::function0<void> > copy;
    {
        Mutex::ScopedLock l(usersLock);
        destroyed = true;
        deletionListeners.swap(copy);
    }
    for (std::map<std::string, boost::function0<void> >::iterator i = copy.begin(); i != copy.end(); ++i) {
        QPID_LOG(debug, "Exchange::destroy() notifying " << i->first);
        if (i->second) i->second();
    }
}
bool Exchange::isDestroyed() const
{
    Mutex::ScopedLock l(usersLock);
    return destroyed;
}
bool Exchange::isAutoDelete() const
{
    return autodelete;
}

}}

