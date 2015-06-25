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

#include "qmf/AgentSessionImpl.h"

#include <iostream>
#include <memory>

namespace qmf {

using std::string;
using std::map;

using qpid::messaging::Address;
using qpid::messaging::Connection;
using qpid::messaging::Duration;
using qpid::messaging::Message;
using qpid::messaging::Receiver;
using qpid::messaging::Sender;
using qpid::types::Variant;

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
int AgentSession::pendingEvents() const { return impl->pendingEvents(); }
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
void AgentSession::raiseEvent(const Data& d, int s) { impl->raiseEvent(d, s); }

//========================================================================================
// Impl Method Bodies
//========================================================================================

AgentSessionImpl::AgentSessionImpl(Connection& c, const string& options) :
    connection(c), domain("default"), opened(false), eventNotifier(0), thread(0), threadCanceled(false),
    bootSequence(1), interval(60), lastHeartbeat(0), lastVisit(0), forceHeartbeat(false),
    externalStorage(false), autoAllowQueries(true), autoAllowMethods(true),
    maxSubscriptions(64), minSubInterval(3000), subLifetime(300), publicEvents(true),
    listenOnDirect(true), strictSecurity(false), maxThreadWaitTime(5),
    schemaUpdateTime(uint64_t(qpid::sys::Duration::FromEpoch()))
{
    //
    // Set Agent Capability Level
    //
    attributes["qmf.agent_capability"] = AGENT_CAPABILITY_0_8;

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

        iter = optMap.find("max-subscriptions");
        if (iter != optMap.end())
            maxSubscriptions = iter->second.asUint32();

        iter = optMap.find("min-sub-interval");
        if (iter != optMap.end())
            minSubInterval = iter->second.asUint32();

        iter = optMap.find("sub-lifetime");
        if (iter != optMap.end())
            subLifetime = iter->second.asUint32();

        iter = optMap.find("public-events");
        if (iter != optMap.end())
            publicEvents = iter->second.asBool();

        iter = optMap.find("listen-on-direct");
        if (iter != optMap.end())
            listenOnDirect = iter->second.asBool();

        iter = optMap.find("strict-security");
        if (iter != optMap.end())
            strictSecurity = iter->second.asBool();

        iter = optMap.find("max-thread-wait-time");
        if (iter != optMap.end())
            maxThreadWaitTime = iter->second.asUint32();
    }

    if (maxThreadWaitTime > interval)
        maxThreadWaitTime = interval;
}


AgentSessionImpl::~AgentSessionImpl()
{
    if (opened)
        close();

    if (thread) {
        thread->join();
        delete thread;
    }
}


void AgentSessionImpl::open()
{
    if (opened)
        throw QmfException("The session is already open");

    // If the thread exists, join and delete it before creating a new one.
    if (thread) {
        thread->join();
        delete thread;
    }

    const string addrArgs(";{create:never,node:{type:topic}}");
    const string routableAddr("direct-agent.route." + qpid::types::Uuid(true).str());
    attributes["_direct_subject"] = routableAddr;

    // Establish messaging addresses
    setAgentName();
    directBase = "qmf." + domain + ".direct";
    topicBase = "qmf." + domain + ".topic";

    // Create AMQP session, receivers, and senders
    session = connection.createSession();
    Receiver directRx;
    Receiver routableDirectRx = session.createReceiver(topicBase + "/" + routableAddr + addrArgs);
    Receiver topicRx = session.createReceiver(topicBase + "/console.#" + addrArgs);

    if (listenOnDirect && !strictSecurity) {
        directRx = session.createReceiver(directBase + "/" + agentName + addrArgs);
        directRx.setCapacity(64);
    }

    routableDirectRx.setCapacity(64);
    topicRx.setCapacity(64);

    if (!strictSecurity)
        directSender = session.createSender(directBase + addrArgs);
    topicSender = session.createSender(topicBase + addrArgs);

    // Start the receiver thread
    threadCanceled = false;
    opened = true;
    thread = new qpid::sys::Thread(*this);

    // Send an initial agent heartbeat message
    sendHeartbeat();
}


void AgentSessionImpl::closeAsync()
{
    if (!opened)
        return;

    // Stop the receiver thread.  Don't join it until the destructor is called or open() is called.
    threadCanceled = true;
    opened = false;
}


void AgentSessionImpl::close()
{
    closeAsync();

    if (thread) {
        thread->join();
        delete thread;
        thread = 0;
    }
}


bool AgentSessionImpl::nextEvent(AgentEvent& event, Duration timeout)
{
    uint64_t milliseconds = timeout.getMilliseconds();
    qpid::sys::Mutex::ScopedLock l(lock);

    if (eventQueue.empty() && milliseconds > 0) {
        int64_t nsecs(qpid::sys::TIME_INFINITE);
        if ((uint64_t)(nsecs / 1000000) > milliseconds)
            nsecs = (int64_t) milliseconds * 1000000;
        qpid::sys::Duration then(nsecs);
        cond.wait(lock, qpid::sys::AbsTime(qpid::sys::now(), then));
    }

    if (!eventQueue.empty()) {
        event = eventQueue.front();
        eventQueue.pop();
        if (eventQueue.empty())
            alertEventNotifierLH(false);
        return true;
    }

    return false;
}


int AgentSessionImpl::pendingEvents() const
{
    qpid::sys::Mutex::ScopedLock l(lock);
    return eventQueue.size();
}


void AgentSessionImpl::setEventNotifier(EventNotifierImpl* notifier)
{
    qpid::sys::Mutex::ScopedLock l(lock);
    eventNotifier = notifier;
}

EventNotifierImpl* AgentSessionImpl::getEventNotifier() const
{
    qpid::sys::Mutex::ScopedLock l(lock);
    return eventNotifier;
}


void AgentSessionImpl::registerSchema(Schema& schema)
{
    if (!schema.isFinalized())
        schema.finalize();
    const SchemaId& schemaId(schema.getSchemaId());

    qpid::sys::Mutex::ScopedLock l(lock);
    schemata[schemaId] = schema;
    schemaIndex[schemaId] = DataIndex();

    //
    // Get the news out at the next periodic interval that there is new schema information.
    //
    schemaUpdateTime = uint64_t(qpid::sys::Duration::FromEpoch());
    forceHeartbeat = true;
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
    std::auto_ptr<AgentEventImpl> eventImpl(new AgentEventImpl(AGENT_QUERY));
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
            map<SchemaId, DataIndex, SchemaIdCompareNoHash>::const_iterator iter = schemaIndex.find(query.getSchemaId());
            if (iter != schemaIndex.end())
                for (DataIndex::const_iterator dIter = iter->second.begin(); dIter != iter->second.end(); dIter++)
                    if (query.matchesPredicate(dIter->second.getProperties()))
                        response(event, dIter->second);
        }
        complete(event);
        return;
    }

    raiseException(event, "Query is Invalid");
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

    headers[protocol::HEADER_KEY_METHOD] = protocol::HEADER_METHOD_RESPONSE;
    headers[protocol::HEADER_KEY_OPCODE] = protocol::HEADER_OPCODE_EXCEPTION;
    headers[protocol::HEADER_KEY_CONTENT] = protocol::HEADER_CONTENT_DATA;
    headers[protocol::HEADER_KEY_AGENT] = agentName;
    headers[protocol::HEADER_KEY_APP_ID] = protocol::HEADER_APP_ID_QMF;

    AgentEventImpl& eventImpl(AgentEventImplAccess::get(event));
    const DataImpl& dataImpl(DataImplAccess::get(data));

    msg.setCorrelationId(eventImpl.getCorrelationId());
    encode(dataImpl.asMap(), msg);
    send(msg, eventImpl.getReplyTo());

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

    headers[protocol::HEADER_KEY_METHOD] = protocol::HEADER_METHOD_RESPONSE;
    headers[protocol::HEADER_KEY_OPCODE] = protocol::HEADER_OPCODE_METHOD_RESPONSE;
    headers[protocol::HEADER_KEY_AGENT] = agentName;
    headers[protocol::HEADER_KEY_APP_ID] = protocol::HEADER_APP_ID_QMF;

    AgentEventImpl& eventImpl(AgentEventImplAccess::get(event));

    const Variant::Map& outArgs(eventImpl.getReturnArguments());
    const Variant::Map& outSubtypes(eventImpl.getReturnArgumentSubtypes());

    map["_arguments"] = outArgs;
    if (!outSubtypes.empty())
        map["_subtypes"] = outSubtypes;

    msg.setCorrelationId(eventImpl.getCorrelationId());
    encode(map, msg);
    send(msg, eventImpl.getReplyTo());

    QPID_LOG(trace, "SENT MethodResponse to=" << eventImpl.getReplyTo());
}


void AgentSessionImpl::raiseEvent(const Data& data)
{
    int severity(SEV_NOTICE);
    if (data.hasSchema()) {
        const Schema& schema(DataImplAccess::get(data).getSchema());
        if (schema.isValid())
            severity = schema.getDefaultSeverity();
    }

    raiseEvent(data, severity);
}


void AgentSessionImpl::raiseEvent(const Data& data, int severity)
{
    Message msg;
    Variant::Map map;
    Variant::Map& headers(msg.getProperties());
    string subject("agent.ind.event");

    if (data.hasSchema()) {
        const SchemaId& schemaId(data.getSchemaId());
        if (schemaId.getType() != SCHEMA_TYPE_EVENT)
            throw QmfException("Cannot call raiseEvent on data that is not an Event");
        subject = subject + "." + schemaId.getPackageName() + "." + schemaId.getName();
    }

    if (severity < SEV_EMERG || severity > SEV_DEBUG)
        throw QmfException("Invalid severity value");

    headers[protocol::HEADER_KEY_METHOD] = protocol::HEADER_METHOD_INDICATION;
    headers[protocol::HEADER_KEY_OPCODE] = protocol::HEADER_OPCODE_DATA_INDICATION;
    headers[protocol::HEADER_KEY_CONTENT] = protocol::HEADER_CONTENT_EVENT;
    headers[protocol::HEADER_KEY_AGENT] = agentName;
    headers[protocol::HEADER_KEY_APP_ID] = protocol::HEADER_APP_ID_QMF;
    msg.setSubject(subject);

    Variant::List list;
    Variant::Map dataAsMap(DataImplAccess::get(data).asMap());
    dataAsMap["_severity"] = severity;
    dataAsMap["_timestamp"] = uint64_t(qpid::sys::Duration::FromEpoch());
    list.push_back(dataAsMap);
    encode(list, msg);
    topicSender.send(msg);

    QPID_LOG(trace, "SENT EventIndication to=" << topicSender.getName() << "/" << subject);
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
    if (notify) {
        cond.notify();
        alertEventNotifierLH(true);
    }
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


void AgentSessionImpl::handleLocateRequest(const Variant::List& predicate, const Message& msg)
{
    QPID_LOG(trace, "RCVD AgentLocateRequest from=" << msg.getReplyTo());

    if (!predicate.empty()) {
        Query agentQuery(QUERY_OBJECT);
        agentQuery.setPredicate(predicate);
        if (!agentQuery.matchesPredicate(attributes)) {
            QPID_LOG(trace, "AgentLocate predicate does not match this agent, ignoring");
            return;
        }
    }

    Message reply;
    Variant::Map map;
    Variant::Map& headers(reply.getProperties());

    headers[protocol::HEADER_KEY_METHOD] = protocol::HEADER_METHOD_INDICATION;
    headers[protocol::HEADER_KEY_OPCODE] = protocol::HEADER_OPCODE_AGENT_LOCATE_RESPONSE;
    headers[protocol::HEADER_KEY_AGENT] = agentName;
    headers[protocol::HEADER_KEY_APP_ID] = protocol::HEADER_APP_ID_QMF;

    map["_values"] = attributes;
    map["_values"].asMap()[protocol::AGENT_ATTR_TIMESTAMP] = uint64_t(qpid::sys::Duration::FromEpoch());
    map["_values"].asMap()[protocol::AGENT_ATTR_HEARTBEAT_INTERVAL] = interval;
    map["_values"].asMap()[protocol::AGENT_ATTR_EPOCH] = bootSequence;
    map["_values"].asMap()[protocol::AGENT_ATTR_SCHEMA_UPDATED_TIMESTAMP] = schemaUpdateTime;

    encode(map, reply);
    send(reply, msg.getReplyTo());
    QPID_LOG(trace, "SENT AgentLocateResponse to=" << msg.getReplyTo());
}


void AgentSessionImpl::handleMethodRequest(const Variant::Map& content, const Message& msg)
{
    QPID_LOG(trace, "RCVD MethodRequest map=" << content << " from=" << msg.getReplyTo() << " cid=" << msg.getCorrelationId());

    //
    // Construct an AgentEvent to be sent to the application.
    //
    std::auto_ptr<AgentEventImpl> eventImpl(new AgentEventImpl(AGENT_METHOD));
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

    iter = content.find("_arguments");
    if (iter != content.end())
        eventImpl->setArguments(iter->second.asMap());

    iter = content.find("_subtypes");
    if (iter != content.end())
        eventImpl->setArgumentSubtypes(iter->second.asMap());

    iter = content.find("_object_id");
    if (iter != content.end()) {
        DataAddr addr(new DataAddrImpl(iter->second.asMap()));
        eventImpl->setDataAddr(addr);
        if (!externalStorage) {
            DataIndex::const_iterator iter(globalIndex.find(addr));
            if (iter == globalIndex.end()) {
                AgentEvent event(eventImpl.release());
                raiseException(event, "No data object found with the specified address");
                return;
            }

            const Schema& schema(DataImplAccess::get(iter->second).getSchema());
            if (schema.isValid()) {
                eventImpl->setSchema(schema);
                for (Variant::Map::const_iterator aIter = eventImpl->getArguments().begin();
                     aIter != eventImpl->getArguments().end(); aIter++) {
                    const Schema& schema(DataImplAccess::get(iter->second).getSchema());
                    if (!SchemaImplAccess::get(schema).isValidMethodInArg(eventImpl->getMethodName(), aIter->first, aIter->second)) {
                        AgentEvent event(eventImpl.release());
                        raiseException(event, "Invalid argument: " + aIter->first);
                        return;
                    }
                }
            }
        }
    }

    enqueueEvent(AgentEvent(eventImpl.release()));
}


void AgentSessionImpl::handleQueryRequest(const Variant::Map& content, const Message& msg)
{
    QPID_LOG(trace, "RCVD QueryRequest query=" << content << " from=" << msg.getReplyTo() << " cid=" << msg.getCorrelationId());

    //
    // Construct an AgentEvent to be sent to the application or directly handled by the agent.
    //
    std::auto_ptr<QueryImpl> queryImpl(new QueryImpl(content));
    std::auto_ptr<AgentEventImpl> eventImpl(new AgentEventImpl(AGENT_AUTH_QUERY));
    eventImpl->setUserId(msg.getUserId());
    eventImpl->setReplyTo(msg.getReplyTo());
    eventImpl->setCorrelationId(msg.getCorrelationId());
    eventImpl->setQuery(queryImpl.release());
    AgentEvent ae(eventImpl.release());

    if (ae.getQuery().getTarget() == QUERY_SCHEMA_ID || ae.getQuery().getTarget() == QUERY_SCHEMA) {
        handleSchemaRequest(ae);
        return;
    }

    if (autoAllowQueries)
        authAccept(ae);
    else
        enqueueEvent(ae);
}


void AgentSessionImpl::handleSchemaRequest(AgentEvent& event)
{
    SchemaMap::const_iterator iter;
    string error;
    const Query& query(event.getQuery());

    Message msg;
    Variant::List content;
    Variant::Map map;
    Variant::Map& headers(msg.getProperties());

    headers[protocol::HEADER_KEY_METHOD] = protocol::HEADER_METHOD_RESPONSE;
    headers[protocol::HEADER_KEY_OPCODE] = protocol::HEADER_OPCODE_QUERY_RESPONSE;
    headers[protocol::HEADER_KEY_AGENT] = agentName;
    headers[protocol::HEADER_KEY_APP_ID] = protocol::HEADER_APP_ID_QMF;

    {
        qpid::sys::Mutex::ScopedLock l(lock);
        if (query.getTarget() == QUERY_SCHEMA_ID) {
            headers[protocol::HEADER_KEY_CONTENT] = "_schema_id";
            for (iter = schemata.begin(); iter != schemata.end(); iter++)
                content.push_back(SchemaIdImplAccess::get(iter->first).asMap());
        } else if (query.getSchemaId().isValid()) {
            headers[protocol::HEADER_KEY_CONTENT] = "_schema";
            iter = schemata.find(query.getSchemaId());
            if (iter != schemata.end())
                content.push_back(SchemaImplAccess::get(iter->second).asMap());
        } else {
            error = "Invalid Schema Query: Requests for SCHEMA must supply a valid schema ID.";
        }
    }

    if (!error.empty()) {
        raiseException(event, error);
        return;
    }

    AgentEventImpl& eventImpl(AgentEventImplAccess::get(event));

    msg.setCorrelationId(eventImpl.getCorrelationId());
    encode(content, msg);
    send(msg, eventImpl.getReplyTo());

    QPID_LOG(trace, "SENT QueryResponse(Schema) to=" << eventImpl.getReplyTo());
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
    map<SchemaId, Schema, SchemaIdCompare>::const_iterator iter;
    string replyContent;

    SchemaId dataId(SCHEMA_TYPE_DATA, packageName, className);
    dataId.setHash(hash);

    {
        qpid::sys::Mutex::ScopedLock l(lock);
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
    }

    Message reply;
    Variant::Map& headers(reply.getProperties());

    headers[protocol::HEADER_KEY_AGENT] = agentName;
    reply.setContent(replyContent);

    send(reply, msg.getReplyTo());
    QPID_LOG(trace, "SENT QMFv1 SchemaResponse to=" << msg.getReplyTo());
}


void AgentSessionImpl::dispatch(Message msg)
{
    const Variant::Map& properties(msg.getProperties());
    Variant::Map::const_iterator iter;

    //
    // If strict-security is enabled, make sure that reply-to address complies with the
    // strict-security addressing pattern (i.e. start with 'qmf.<domain>.topic/direct-console.').
    //
    if (strictSecurity && msg.getReplyTo()) {
        if (msg.getReplyTo().getName() != topicBase || msg.getReplyTo().getSubject().find("direct-console.") != 0) {
            QPID_LOG(warning, "Reply-to violates strict-security policy: " << msg.getReplyTo().str());
            return;
        }
    }

    iter = properties.find(protocol::HEADER_KEY_APP_ID);
    if (iter != properties.end() && iter->second.asString() == protocol::HEADER_APP_ID_QMF) {
        //
        // Dispatch a QMFv2 formatted message
        //
        iter = properties.find(protocol::HEADER_KEY_OPCODE);
        if (iter == properties.end()) {
            QPID_LOG(trace, "Message received with no 'qmf.opcode' header");
            return;
        }

        const string& opcode = iter->second.asString();

        if (msg.getContentType() == "amqp/list") {
            Variant::List content;
            decode(msg, content);

            if (opcode == protocol::HEADER_OPCODE_AGENT_LOCATE_REQUEST) handleLocateRequest(content, msg);
            else {
                QPID_LOG(trace, "Unexpected QMFv2 opcode with 'amqp/list' content: " << opcode);
            }

        } else if (msg.getContentType() == "amqp/map") {
            Variant::Map content;
            decode(msg, content);

            if      (opcode == protocol::HEADER_OPCODE_METHOD_REQUEST) handleMethodRequest(content, msg);
            else if (opcode == protocol::HEADER_OPCODE_QUERY_REQUEST)  handleQueryRequest(content, msg);
            else {
                QPID_LOG(trace, "Unexpected QMFv2 opcode with 'amqp/map' content: " << opcode);
            }
        } else {
            QPID_LOG(trace, "Unexpected QMFv2 content type.  Expected amqp/list or amqp/map");
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

    address << "agent.ind.heartbeat";

    // append .<vendor>.<product> to address key if present.
    Variant::Map::const_iterator v;
    if ((v = attributes.find("_vendor")) != attributes.end() && !v->second.getString().empty()) {
        address << "." << v->second.getString();
        if ((v = attributes.find("_product")) != attributes.end() && !v->second.getString().empty()) {
            address << "." << v->second.getString();
        }
    }

    headers[protocol::HEADER_KEY_METHOD] = protocol::HEADER_METHOD_INDICATION;
    headers[protocol::HEADER_KEY_OPCODE] = protocol::HEADER_OPCODE_AGENT_HEARTBEAT_INDICATION;
    headers[protocol::HEADER_KEY_AGENT] = agentName;
    headers[protocol::HEADER_KEY_APP_ID] = protocol::HEADER_APP_ID_QMF;
    msg.setSubject(address.str());

    map["_values"] = attributes;
    map["_values"].asMap()[protocol::AGENT_ATTR_TIMESTAMP] = uint64_t(qpid::sys::Duration::FromEpoch());
    map["_values"].asMap()[protocol::AGENT_ATTR_HEARTBEAT_INTERVAL] = interval;
    map["_values"].asMap()[protocol::AGENT_ATTR_EPOCH] = bootSequence;
    map["_values"].asMap()[protocol::AGENT_ATTR_SCHEMA_UPDATED_TIMESTAMP] = schemaUpdateTime;

    encode(map, msg);
    topicSender.send(msg);
    QPID_LOG(trace, "SENT AgentHeartbeat name=" << agentName);
}


void AgentSessionImpl::send(Message msg, const Address& to)
{
    Sender sender;

    if (strictSecurity && to.getName() != topicBase) {
        QPID_LOG(warning, "Address violates strict-security policy: " << to);
        return;
    }

    if (to.getName() == directBase) {
        msg.setSubject(to.getSubject());
        sender = directSender;
    } else if (to.getName() == topicBase) {
        msg.setSubject(to.getSubject());
        sender = topicSender;
    } else
        sender = session.createSender(to);

    sender.send(msg);
}


void AgentSessionImpl::flushResponses(AgentEvent& event, bool final)
{
    Message msg;
    Variant::Map map;
    Variant::Map& headers(msg.getProperties());

    headers[protocol::HEADER_KEY_METHOD] = protocol::HEADER_METHOD_RESPONSE;
    headers[protocol::HEADER_KEY_OPCODE] = protocol::HEADER_OPCODE_QUERY_RESPONSE;
    headers[protocol::HEADER_KEY_CONTENT] = protocol::HEADER_CONTENT_DATA;
    headers[protocol::HEADER_KEY_AGENT] = agentName;
    headers[protocol::HEADER_KEY_APP_ID] = protocol::HEADER_APP_ID_QMF;
    if (!final)
        headers[protocol::HEADER_KEY_PARTIAL] = Variant();

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
    send(msg, eventImpl.getReplyTo());

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
    //uint64_t thisInterval(seconds - lastVisit);
    lastVisit = seconds;

    //
    // First time through, set lastHeartbeat to the current time.
    //
    if (lastHeartbeat == 0)
        lastHeartbeat = seconds;

    //
    // If the hearbeat interval has elapsed, send a heartbeat.
    //
    if (forceHeartbeat || (seconds - lastHeartbeat >= interval)) {
        lastHeartbeat = seconds;
        forceHeartbeat = false;
        sendHeartbeat();
    }

    //
    // TODO: process any active subscriptions on their intervals.
    //
}


void AgentSessionImpl::alertEventNotifierLH(bool readable)
{
    if (eventNotifier)
        eventNotifier->setReadable(readable);
}


void AgentSessionImpl::run()
{
    QPID_LOG(debug, "AgentSession thread started for agent " << agentName);

    try {
        while (!threadCanceled) {
            periodicProcessing((uint64_t) qpid::sys::Duration::FromEpoch() / qpid::sys::TIME_SEC);

            Receiver rx;
            bool valid = session.nextReceiver(rx, Duration::SECOND * maxThreadWaitTime);
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

    session.close();
    QPID_LOG(debug, "AgentSession thread exiting for agent " << agentName);
}


AgentSessionImpl& AgentSessionImplAccess::get(AgentSession& session)
{
    return *session.impl;
}


const AgentSessionImpl& AgentSessionImplAccess::get(const AgentSession& session)
{
    return *session.impl;
}

}
