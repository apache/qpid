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

#include "qpid/RefCounted.h"
#include "qmf/PrivateImplRef.h"
#include "qmf/exceptions.h"
#include "qmf/AgentSession.h"
#include "qmf/AgentEventImpl.h"
#include "qmf/SchemaIdImpl.h"
#include "qmf/SchemaImpl.h"
#include "qmf/DataAddrImpl.h"
#include "qmf/DataImpl.h"
#include "qmf/Query.h"
#include "qpid/sys/Mutex.h"
#include "qpid/sys/Condition.h"
#include "qpid/sys/Thread.h"
#include "qpid/sys/Runnable.h"
#include "qpid/log/Statement.h"
#include "qpid/messaging/Connection.h"
#include "qpid/messaging/Session.h"
#include "qpid/messaging/Receiver.h"
#include "qpid/messaging/Sender.h"
#include "qpid/messaging/Message.h"
#include "qpid/messaging/AddressParser.h"
#include "qpid/management/Buffer.h"
#include <queue>
#include <map>
#include <set>
#include <iostream>
#include <memory>

using namespace std;
using namespace qpid::messaging;
using namespace qmf;
using qpid::types::Variant;

namespace qmf {
    class AgentSessionImpl : public virtual qpid::RefCounted, public qpid::sys::Runnable {
    public:
        ~AgentSessionImpl();

        //
        // Methods from API handle
        //
        AgentSessionImpl(Connection& c, const string& o);
        void setDomain(const string& d) { checkOpen(); domain = d; }
        void setVendor(const string& v) { checkOpen(); attributes["_vendor"] = v; }
        void setProduct(const string& p) { checkOpen(); attributes["_product"] = p; }
        void setInstance(const string& i) { checkOpen(); attributes["_instance"] = i; }
        void setAttribute(const string& k, const qpid::types::Variant& v) { checkOpen(); attributes[k] = v; }
        const string& getName() const { return agentName; }
        void open();
        void close();
        bool nextEvent(AgentEvent& e, Duration t);

        void registerSchema(Schema& s);
        DataAddr addData(Data& d, const string& n, bool persist);
        void delData(const DataAddr&);

        void authAccept(AgentEvent& e);
        void authReject(AgentEvent& e, const string& m);
        void raiseException(AgentEvent& e, const string& s);
        void raiseException(AgentEvent& e, const Data& d);
        void response(AgentEvent& e, const Data& d);
        void complete(AgentEvent& e);
        void methodSuccess(AgentEvent& e);
        void raiseEvent(const Data& d);

    private:
        typedef map<DataAddr, Data, DataAddrCompare> DataIndex;

        mutable qpid::sys::Mutex lock;
        qpid::sys::Condition cond;
        Connection connection;
        Session session;
        string domain;
        Variant::Map attributes;
        Variant::Map options;
        string agentName;
        bool opened;
        queue<AgentEvent> eventQueue;
        qpid::sys::Thread* thread;
        bool threadCanceled;
        uint32_t bootSequence;
        uint32_t interval;
        uint64_t lastHeartbeat;
        uint64_t lastVisit;
        bool externalStorage;
        bool autoAllowQueries;
        bool autoAllowMethods;
        uint64_t schemaUpdateTime;
        string directBase;
        string topicBase;

        set<string> packages;
        map<SchemaId, Schema, SchemaIdCompare> schemata;
        DataIndex globalIndex;
        map<SchemaId, DataIndex, SchemaIdCompare> schemaIndex;

        void checkOpen();
        void setAgentName();
        void enqueueEvent(const AgentEvent&);
        void handleLocateRequest(const Variant::Map& content, const Message& msg);
        void handleMethodRequest(const Variant::Map& content, const Message& msg);
        void handleQueryRequest(const Variant::Map& content, const Message& msg);
        void handleV1SchemaRequest(qpid::management::Buffer&, uint32_t, const Message&);
        void dispatch(Message);
        void sendHeartbeat();
        bool predicateMatch(const Query&, const Data&);
        void flushResponses(AgentEvent&, bool);
        void periodicProcessing(uint64_t);
        void run();
    };
}

typedef qmf::PrivateImplRef<AgentSession> PI;

AgentSession::AgentSession(AgentSessionImpl* impl) { PI::ctor(*this, impl); }
AgentSession::AgentSession(const AgentSession& s) : qmf::Handle<AgentSessionImpl>() { PI::copy(*this, s); }
AgentSession::~AgentSession() { PI::dtor(*this); }
AgentSession& AgentSession::operator=(const AgentSession& s) { return PI::assign(*this, s); }

AgentSession::AgentSession(Connection& c, const string& o) { PI::ctor(*this, new AgentSessionImpl(c, o)); }
void AgentSession::setDomain(const string& d) { impl->setDomain(d); }
void AgentSession::setVendor(const string& v) { impl->setVendor(v); }
void AgentSession::setProduct(const string& p) { impl->setProduct(p); }
void AgentSession::setInstance(const string& i) { impl->setInstance(i); }
void AgentSession::setAttribute(const string& k, const qpid::types::Variant& v) { impl->setAttribute(k, v); }
const string& AgentSession::getName() const { return impl->getName(); }
void AgentSession::open() { impl->open(); }
void AgentSession::close() { impl->close(); }
bool AgentSession::nextEvent(AgentEvent& e, Duration t) { return impl->nextEvent(e, t); }
void AgentSession::registerSchema(Schema& s) { impl->registerSchema(s); }
DataAddr AgentSession::addData(Data& d, const string& n, bool p) { return impl->addData(d, n, p); }
void AgentSession::delData(const DataAddr& a) { impl->delData(a); }
void AgentSession::authAccept(AgentEvent& e) { impl->authAccept(e); }
void AgentSession::authReject(AgentEvent& e, const string& m) { impl->authReject(e, m); }
void AgentSession::raiseException(AgentEvent& e, const string& s) { impl->raiseException(e, s); }
void AgentSession::raiseException(AgentEvent& e, const Data& d) { impl->raiseException(e, d); }
void AgentSession::response(AgentEvent& e, const Data& d) { impl->response(e, d); }
void AgentSession::complete(AgentEvent& e) { impl->complete(e); }
void AgentSession::methodSuccess(AgentEvent& e) { impl->methodSuccess(e); }
void AgentSession::raiseEvent(const Data& d) { impl->raiseEvent(d); }

//========================================================================================
// Impl Method Bodies
//========================================================================================

AgentSessionImpl::AgentSessionImpl(Connection& c, const string& options) :
    connection(c), domain("default"), opened(false), thread(0), threadCanceled(false),
    bootSequence(1), interval(60), lastHeartbeat(0), lastVisit(0), externalStorage(false),
    autoAllowQueries(true), autoAllowMethods(true),
    schemaUpdateTime(uint64_t(qpid::sys::Duration(qpid::sys::EPOCH, qpid::sys::now())))
{
    //
    // Set Capability Level to 1
    //
    attributes["_capability_level"] = 1;

    if (!options.empty()) {
        qpid::messaging::AddressParser parser(options);
        Variant::Map optMap;
        Variant::Map::const_iterator iter;

        parser.parseMap(optMap);

        iter = optMap.find("domain");
        if (iter != optMap.end())
            domain = iter->second.asString();

        iter = optMap.find("interval");
        if (iter != optMap.end()) {
            interval = iter->second.asUint32();
            if (interval < 1)
                interval = 1;
        }

        iter = optMap.find("external");
        if (iter != optMap.end())
            externalStorage = iter->second.asBool();

        iter = optMap.find("allow-queries");
        if (iter != optMap.end())
            autoAllowQueries = iter->second.asBool();

        iter = optMap.find("allow-methods");
        if (iter != optMap.end())
            autoAllowMethods = iter->second.asBool();
    }
}


AgentSessionImpl::~AgentSessionImpl()
{
    if (opened)
        close();
}


void AgentSessionImpl::open()
{
    if (opened)
        throw QmfException("The session is already open");
    opened = true;

    // Establish messaging addresses
    setAgentName();
    directBase = "qmf." + domain + ".direct";
    topicBase = "qmf." + domain + ".topic";

    // Create AMQP session, receivers, and senders
    session = connection.createSession();
    Receiver directRx = session.createReceiver(directBase + "/" + agentName);
    Receiver topicRx = session.createReceiver(topicBase + "/console.#");

    directRx.setCapacity(64);
    topicRx.setCapacity(64);

    // Start the receiver thread
    threadCanceled = false;
    thread = new qpid::sys::Thread(*this);

    // Send an initial agent heartbeat message
    sendHeartbeat();
}


void AgentSessionImpl::close()
{
    if (!opened)
        throw QmfException("The session is already closed");

    // Stop and join the receiver thread
    threadCanceled = true;
    thread->join();
    delete thread;

    // Close the AMQP session
    session.close();
    opened = false;
}


bool AgentSessionImpl::nextEvent(AgentEvent& event, Duration timeout)
{
    uint64_t milliseconds = timeout.getMilliseconds();
    qpid::sys::Mutex::ScopedLock l(lock);

    if (eventQueue.empty())
        cond.wait(lock, qpid::sys::AbsTime(qpid::sys::now(),
                                           qpid::sys::Duration(milliseconds * qpid::sys::TIME_MSEC)));

    if (!eventQueue.empty()) {
        event = eventQueue.front();
        eventQueue.pop();
        return true;
    }

    return false;
}


void AgentSessionImpl::registerSchema(Schema& schema)
{
    qpid::sys::Mutex::ScopedLock l(lock);
    schemaUpdateTime = uint64_t(qpid::sys::Duration(qpid::sys::EPOCH, qpid::sys::now()));

    if (!schema.isFinalized())
        schema.finalize();

    const SchemaId& schemaId(schema.getSchemaId());
    const string& packageName(schemaId.getPackageName());

    packages.insert(packageName);
    schemata[schemaId] = schema;
    schemaIndex[schemaId] = DataIndex();
}


DataAddr AgentSessionImpl::addData(Data& data, const string& name, bool persistent)
{
    if (externalStorage)
        throw QmfException("addData() must not be called when the 'external' option is enabled.");

    string dataName;
    if (name.empty())
        dataName = qpid::types::Uuid(true).str();
    else
        dataName = name;

    DataAddr addr(dataName, agentName, persistent ? 0 : bootSequence);
    data.setAddr(addr);

    {
        qpid::sys::Mutex::ScopedLock l(lock);
        DataIndex::const_iterator iter = globalIndex.find(addr);
        if (iter != globalIndex.end())
            throw QmfException("Duplicate Data Address");

        globalIndex[addr] = data;
        if (data.hasSchema())
            schemaIndex[data.getSchemaId()][addr] = data;
    }

    //
    // TODO: Survey active subscriptions to see if they need to hear about this new data.
    //

    return addr;
}


void AgentSessionImpl::delData(const DataAddr& addr)
{
    {
        qpid::sys::Mutex::ScopedLock l(lock);
        DataIndex::iterator iter = globalIndex.find(addr);
        if (iter == globalIndex.end())
            return;
        if (iter->second.hasSchema()) {
            const SchemaId& schemaId(iter->second.getSchemaId());
            schemaIndex[schemaId].erase(addr);
        }
        globalIndex.erase(iter);
    }

    //
    // TODO: Survey active subscriptions to see if they need to hear about this deleted data.
    //
}


void AgentSessionImpl::authAccept(AgentEvent& authEvent)
{
    auto_ptr<AgentEventImpl> eventImpl(new AgentEventImpl(AGENT_QUERY));
    eventImpl->setQuery(authEvent.getQuery());
    eventImpl->setUserId(authEvent.getUserId());
    eventImpl->setReplyTo(AgentEventImplAccess::get(authEvent).getReplyTo());
    eventImpl->setCorrelationId(AgentEventImplAccess::get(authEvent).getCorrelationId());
    AgentEvent event(eventImpl.release());

    if (externalStorage) {
        enqueueEvent(event);
        return;
    }

    const Query& query(authEvent.getQuery());
    if (query.getDataAddr().isValid()) {
        {
            qpid::sys::Mutex::ScopedLock l(lock);
            DataIndex::const_iterator iter = globalIndex.find(query.getDataAddr());
            if (iter != globalIndex.end())
                response(event, iter->second);
        }
        complete(event);
        return;
    }

    if (query.getSchemaId().isValid()) {
        {
            qpid::sys::Mutex::ScopedLock l(lock);
            map<SchemaId, DataIndex>::const_iterator iter = schemaIndex.find(query.getSchemaId());
            if (iter != schemaIndex.end())
                for (DataIndex::const_iterator dIter = iter->second.begin(); dIter != iter->second.end(); dIter++)
                    if (predicateMatch(query, dIter->second))
                        response(event, dIter->second);
        }
        complete(event);
        return;
    }

    const string& className(query.getClassName());
    const string& packageName(query.getPackageName());

    if (className.empty()) {
        raiseException(event, "Query is Invalid");
        return;
    }

    {
        qpid::sys::Mutex::ScopedLock l(lock);
        map<SchemaId, DataIndex>::const_iterator sIter;
        for (sIter = schemaIndex.begin(); sIter != schemaIndex.end(); sIter++) {
            const SchemaId& schemaId(sIter->first);
            if (schemaId.getName() == className &&
                (packageName.empty() || schemaId.getPackageName() == packageName))
                for (DataIndex::const_iterator dIter = sIter->second.begin(); dIter != sIter->second.end(); dIter++)
                    if (predicateMatch(query, dIter->second))
                        response(event, dIter->second);
        }
    }
    complete(event);
}


void AgentSessionImpl::authReject(AgentEvent& event, const string& error)
{
    raiseException(event, "Action Forbidden - " + error);
}


void AgentSessionImpl::raiseException(AgentEvent& event, const string& error)
{
    Data exception(new DataImpl());
    exception.setProperty("error_text", error);
    raiseException(event, exception);
}


void AgentSessionImpl::raiseException(AgentEvent& event, const Data& data)
{
    Message msg;
    Variant::Map map;
    Variant::Map& headers(msg.getProperties());

    headers["method"] = "response";
    headers["qmf.opcode"] = "_exception";
    headers["qmf.content"] = "_data";
    headers["qmf.agent"] = agentName;
    headers["x-amqp-0-10.app-id"] = "qmf2";

    AgentEventImpl& eventImpl(AgentEventImplAccess::get(event));
    const DataImpl& dataImpl(DataImplAccess::get(data));

    msg.setCorrelationId(eventImpl.getCorrelationId());
    encode(dataImpl.asMap(), msg);
    Sender sender(session.createSender(eventImpl.getReplyTo()));
    sender.send(msg);
    sender.close();

    QPID_LOG(trace, "SENT Exception to=" << eventImpl.getReplyTo());
}


void AgentSessionImpl::response(AgentEvent& event, const Data& data)
{
    AgentEventImpl& impl(AgentEventImplAccess::get(event));
    uint32_t count = impl.enqueueData(data);
    if (count >= 8)
        flushResponses(event, false);
}


void AgentSessionImpl::complete(AgentEvent& event)
{
    flushResponses(event, true);
}


void AgentSessionImpl::methodSuccess(AgentEvent& event)
{
    Message msg;
    Variant::Map map;
    Variant::Map& headers(msg.getProperties());

    headers["method"] = "response";
    headers["qmf.opcode"] = "_method_response";
    headers["qmf.agent"] = agentName;
    headers["x-amqp-0-10.app-id"] = "qmf2";

    AgentEventImpl& eventImpl(AgentEventImplAccess::get(event));

    const Variant::Map& outArgs(eventImpl.getReturnArguments());
    const Variant::Map& outSubtypes(eventImpl.getReturnArgumentSubtypes());

    map["_arguments"] = outArgs;
    if (!outSubtypes.empty())
        map["_subtypes"] = outSubtypes;

    msg.setCorrelationId(eventImpl.getCorrelationId());
    encode(map, msg);
    Sender sender(session.createSender(eventImpl.getReplyTo()));
    sender.send(msg);
    sender.close();

    QPID_LOG(trace, "SENT MethodResponse to=" << eventImpl.getReplyTo());
}


void AgentSessionImpl::raiseEvent(const Data& data)
{
    Message msg;
    Variant::Map map;
    Variant::Map& headers(msg.getProperties());

    std::stringstream address;

    address << topicBase << "/agent.ind.event";

    // TODO: add severity.package.class to key
    //       or modify to send only to subscriptions with matching queries

    headers["method"] = "indication";
    headers["qmf.opcode"] = "_data_indication";
    headers["qmf.content"] = "_event";
    headers["qmf.agent"] = agentName;
    headers["x-amqp-0-10.app-id"] = "qmf2";

    encode(DataImplAccess::get(data).asMap(), msg);
    Sender sender(session.createSender(address.str()));
    sender.send(msg);
    sender.close();

    QPID_LOG(trace, "SENT EventIndication to=" << address.str());
}


void AgentSessionImpl::checkOpen()
{
    if (opened)
        throw QmfException("Operation must be performed before calling open()");
}


void AgentSessionImpl::enqueueEvent(const AgentEvent& event)
{
    qpid::sys::Mutex::ScopedLock l(lock);
    bool notify = eventQueue.empty();
    eventQueue.push(event);
    if (notify)
        cond.notify();
}


void AgentSessionImpl::setAgentName()
{
    Variant::Map::iterator iter;
    string vendor;
    string product;
    string instance;

    iter = attributes.find("_vendor");
    if (iter == attributes.end())
        attributes["_vendor"] = vendor;
    else
        vendor = iter->second.asString();

    iter = attributes.find("_product");
    if (iter == attributes.end())
        attributes["_product"] = product;
    else
        product = iter->second.asString();

    iter = attributes.find("_instance");
    if (iter == attributes.end()) {
        instance = qpid::types::Uuid(true).str();
        attributes["_instance"] = instance;
    } else
        instance = iter->second.asString();

    agentName = vendor + ":" + product + ":" + instance;
    attributes["_name"] = agentName;
}


void AgentSessionImpl::handleLocateRequest(const Variant::Map&, const Message& msg)
{
    QPID_LOG(trace, "RCVD AgentLocateRequest");

    Message reply;
    Variant::Map map;
    Variant::Map& headers(reply.getProperties());

    headers["method"] = "indication";
    headers["qmf.opcode"] = "_agent_locate_response";
    headers["qmf.agent"] = agentName;
    headers["x-amqp-0-10.app-id"] = "qmf2";

    map["_values"] = attributes;
    map["_values"].asMap()["timestamp"] = uint64_t(qpid::sys::Duration(qpid::sys::EPOCH, qpid::sys::now()));
    map["_values"].asMap()["heartbeat_interval"] = interval;
    map["_values"].asMap()["epoch"] = bootSequence;
    map["_values"].asMap()["schemaUpdated"] = schemaUpdateTime;

    encode(map, reply);
    Sender sender = session.createSender(msg.getReplyTo());
    sender.send(reply);
    QPID_LOG(trace, "SENT AgentLocateResponse to=" << msg.getReplyTo());
    sender.close();
}


void AgentSessionImpl::handleMethodRequest(const Variant::Map& content, const Message& msg)
{
    QPID_LOG(trace, "RCVD MethodRequest map=" << content << " from=" << msg.getReplyTo());

    //
    // Construct an AgentEvent to be sent to the application.
    //
    auto_ptr<AgentEventImpl> eventImpl(new AgentEventImpl(AGENT_METHOD));
    eventImpl->setUserId(msg.getUserId());
    eventImpl->setReplyTo(msg.getReplyTo());
    eventImpl->setCorrelationId(msg.getCorrelationId());

    Variant::Map::const_iterator iter;

    iter = content.find("_method_name");
    if (iter == content.end()) {
        AgentEvent event(eventImpl.release());
        raiseException(event, "Malformed MethodRequest: missing _method_name field");
        return;
    }
    eventImpl->setMethodName(iter->second.asString());

    iter = content.find("_object_id");
    if (iter != content.end())
        eventImpl->setDataAddr(DataAddr(new DataAddrImpl(iter->second.asMap())));

    iter = content.find("_arguments");
    if (iter != content.end())
        eventImpl->setArguments(iter->second.asMap());

    iter = content.find("_subtypes");
    if (iter != content.end())
        eventImpl->setArgumentSubtypes(iter->second.asMap());

    enqueueEvent(AgentEvent(eventImpl.release()));
}


void AgentSessionImpl::handleQueryRequest(const Variant::Map& content, const Message& msg)
{
    QPID_LOG(trace, "RCVD QueryRequest query=" << content << " from=" << msg.getReplyTo());

    //
    // Construct an AgentEvent to be sent to the application or directly handled by the agent.
    //
    auto_ptr<AgentEventImpl> eventImpl(new AgentEventImpl(AGENT_AUTH_QUERY));
    eventImpl->setUserId(msg.getUserId());
    eventImpl->setReplyTo(msg.getReplyTo());
    eventImpl->setCorrelationId(msg.getCorrelationId());

    Query query;
    Variant::Map::const_iterator iter;

    iter = content.find("_what");
    if (iter == content.end()) {
        QPID_LOG(error, "Received QueryRequest with no _what element");
        return;
    }

    if (iter->second.asString() == "OBJECT") {
        //
        // This is an object query, handle the various flavors of query.
        //
        iter = content.find("_object_id");
        if (iter != content.end()) {
            auto_ptr<DataAddrImpl> addrImpl(new DataAddrImpl(iter->second.asMap()));
            query = Query(DataAddr(addrImpl.release()));
        } else {
            iter = content.find("_schema_id");
            if (iter != content.end()) {
                const Variant::Map& map(iter->second.asMap());
                string className;
                string packageName;

                iter = map.find("_class_name");
                if (iter == map.end()) {
                    QPID_LOG(error, "Received QueryRequest with no invalid schemaId");
                    return;
                }

                className = iter->second.asString();
                iter = map.find("_package_name");
                if (iter != map.end())
                    packageName = iter->second.asString();

                query = Query(className, packageName);
            } else {
                QPID_LOG(error, "Received QueryRequest with no valid elements");
                return;
            }
        }

        eventImpl->setQuery(query);

        if (autoAllowQueries) {
            AgentEvent ae(eventImpl.release());
            authAccept(ae);
        } else
            enqueueEvent(AgentEvent(eventImpl.release()));

    } else if (iter->second.asString() == "SCHEMA") {
        // TODO: process a v2 schema request
    }
}


void AgentSessionImpl::handleV1SchemaRequest(qpid::management::Buffer& buffer, uint32_t seq, const Message& msg)
{
    string packageName;
    string className;
    uint8_t hashBits[16];

    buffer.getShortString(packageName);
    buffer.getShortString(className);
    buffer.getBin128(hashBits);

    QPID_LOG(trace, "RCVD QMFv1 SchemaRequest for " << packageName << ":" << className);

    qpid::types::Uuid hash(hashBits);
    map<SchemaId, Schema>::const_iterator iter;
    string replyContent;

    SchemaId dataId(SCHEMA_TYPE_DATA, packageName, className);
    dataId.setHash(hash);

    iter = schemata.find(dataId);
    if (iter != schemata.end())
        replyContent = SchemaImplAccess::get(iter->second).asV1Content(seq);
    else {
        SchemaId eventId(SCHEMA_TYPE_EVENT, packageName, className);
        eventId.setHash(hash);
        iter = schemata.find(dataId);
        if (iter != schemata.end())
            replyContent = SchemaImplAccess::get(iter->second).asV1Content(seq);
        else
            return;
    }

    Message reply;
    Variant::Map& headers(reply.getProperties());

    headers["qmf.agent"] = agentName;
    reply.setContent(replyContent);

    Sender sender = session.createSender(msg.getReplyTo());
    sender.send(reply);
    QPID_LOG(trace, "SENT QMFv1 SchemaResponse to=" << msg.getReplyTo());
    sender.close();
}


void AgentSessionImpl::dispatch(Message msg)
{
    const Variant::Map& properties(msg.getProperties());
    Variant::Map::const_iterator iter;

    iter = properties.find("x-amqp-0-10.app-id");
    if (iter != properties.end() && iter->second.asString() == "qmf2") {
        //
        // Dispatch a QMFv2 formatted message
        //
        iter = properties.find("qmf.opcode");
        if (iter == properties.end()) {
            QPID_LOG(trace, "Message received with no 'qmf.opcode' header");
            return;
        }

        if (msg.getContentType() != "amqp/map") {
            QPID_LOG(trace, "Message received with content type '" << msg.getContentType() <<
                     "'. Expected 'amqp/map'");
            return;
        }

        Variant::Map content;
        decode(msg, content);

        const string& opcode = iter->second.asString();

        if      (opcode == "_agent_locate_request") handleLocateRequest(content, msg);
        else if (opcode == "_method_request")       handleMethodRequest(content, msg);
        else if (opcode == "_query_request")        handleQueryRequest(content, msg);
        else {
            QPID_LOG(trace, "Unknown QMFv2 opcode: " << opcode);
        }
    } else {
        //
        // Dispatch a QMFv1 formatted message
        //
        const string& body(msg.getContent());
        if (body.size() < 8)
            return;
        qpid::management::Buffer buffer(const_cast<char*>(body.c_str()), body.size());

        if (buffer.getOctet() != 'A') return;
        if (buffer.getOctet() != 'M') return;
        if (buffer.getOctet() != '2') return;
        char v1Opcode(buffer.getOctet());
        uint32_t seq(buffer.getLong());

        if (v1Opcode == 'S') handleV1SchemaRequest(buffer, seq, msg);
        else {
            QPID_LOG(trace, "Unknown or Unsupported QMFv1 opcode: " << v1Opcode);
        }
    }
}


void AgentSessionImpl::sendHeartbeat()
{
    Message msg;
    Variant::Map map;
    Variant::Map& headers(msg.getProperties());
    std::stringstream address;

    address << topicBase << "/agent.ind.heartbeat";

    // append .<vendor>.<product> to address key if present.
    Variant::Map::const_iterator v;
    if ((v = attributes.find("_vendor")) != attributes.end() && !v->second.getString().empty()) {
        address << "." << v->second.getString();
        if ((v = attributes.find("_product")) != attributes.end() && !v->second.getString().empty()) {
            address << "." << v->second.getString();
        }
    }

    headers["method"] = "indication";
    headers["qmf.opcode"] = "_agent_heartbeat_indication";
    headers["qmf.agent"] = agentName;
    headers["x-amqp-0-10.app-id"] = "qmf2";

    map["_values"] = attributes;
    map["_values"].asMap()["timestamp"] = uint64_t(qpid::sys::Duration(qpid::sys::EPOCH, qpid::sys::now()));
    map["_values"].asMap()["heartbeat_interval"] = interval;
    map["_values"].asMap()["epoch"] = bootSequence;
    map["_values"].asMap()["schemaUpdated"] = schemaUpdateTime;

    encode(map, msg);
    Sender sender = session.createSender(address.str());
    sender.send(msg);
    QPID_LOG(trace, "SENT AgentHeartbeat name=" << agentName);
    sender.close();
}


bool AgentSessionImpl::predicateMatch(const Query&, const Data&)
{
    // TODO: Implement a proper predicate match
    return true;
}


void AgentSessionImpl::flushResponses(AgentEvent& event, bool final)
{
    Message msg;
    Variant::Map map;
    Variant::Map& headers(msg.getProperties());

    headers["method"] = "response";
    headers["qmf.opcode"] = "_query_response";
    headers["qmf.content"] = "_data";
    headers["qmf.agent"] = agentName;
    headers["x-amqp-0-10.app-id"] = "qmf2";
    if (!final)
        headers["partial"] = Variant();

    Variant::List body;
    AgentEventImpl& eventImpl(AgentEventImplAccess::get(event));
    Data data(eventImpl.dequeueData());
    while (data.isValid()) {
        DataImpl& dataImpl(DataImplAccess::get(data));
        body.push_back(dataImpl.asMap());
        data = eventImpl.dequeueData();
    }

    msg.setCorrelationId(eventImpl.getCorrelationId());
    encode(body, msg);
    Sender sender(session.createSender(eventImpl.getReplyTo()));
    sender.send(msg);
    sender.close();

    QPID_LOG(trace, "SENT QueryResponse to=" << eventImpl.getReplyTo());
}


void AgentSessionImpl::periodicProcessing(uint64_t seconds)
{
    //
    // The granularity of this timer is seconds.  Don't waste time looking for work if
    // it's been less than a second since we last visited.
    //
    if (seconds == lastVisit)
        return;
    lastVisit = seconds;

    //
    // First time through, set lastHeartbeat to the current time.
    //
    if (lastHeartbeat == 0)
        lastHeartbeat = seconds;

    //
    // If the hearbeat interval has elapsed, send a heartbeat.
    //
    if (seconds - lastHeartbeat >= interval) {
        lastHeartbeat = seconds;
        sendHeartbeat();
    }

    //
    // TODO: process any active subscriptions on their intervals.
    //
}


void AgentSessionImpl::run()
{
    QPID_LOG(debug, "AgentSession thread started for agent " << agentName);

    try {
        while (!threadCanceled) {
            periodicProcessing((uint64_t) qpid::sys::Duration(qpid::sys::EPOCH, qpid::sys::now()) / qpid::sys::TIME_SEC);

            Receiver rx;
            bool valid = session.nextReceiver(rx, Duration::SECOND);
            if (threadCanceled)
                break;
            if (valid) {
                try {
                    dispatch(rx.fetch());
                } catch (qpid::types::Exception& e) {
                    QPID_LOG(error, "Exception caught in message dispatch: " << e.what());
                }
                session.acknowledge();
            }
        }
    } catch (qpid::types::Exception& e) {
        QPID_LOG(error, "Exception caught in message thread - exiting: " << e.what());
        enqueueEvent(AgentEvent(new AgentEventImpl(AGENT_THREAD_FAILED)));
    }

    QPID_LOG(debug, "AgentSession thread exiting for agent " << agentName);
}


