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

#include "qmf/AgentImpl.h"
#include "qmf/PrivateImplRef.h"
#include "qmf/ConsoleEventImpl.h"
#include "qmf/ConsoleSession.h"
#include "qmf/DataImpl.h"
#include "qmf/Query.h"
#include "qmf/SchemaImpl.h"
#include "qmf/agentCapability.h"
#include "qmf/constants.h"
#include "qpid/messaging/Sender.h"
#include "qpid/messaging/AddressParser.h"
#include "qpid/management/Buffer.h"
#include "qpid/log/Statement.h"
#include <boost/lexical_cast.hpp>

using qpid::types::Variant;
using qpid::messaging::Duration;
using qpid::messaging::Message;
using qpid::messaging::Sender;
using namespace std;
using namespace qmf;

typedef PrivateImplRef<Agent> PI;

Agent::Agent(AgentImpl* impl) { PI::ctor(*this, impl); }
Agent::Agent(const Agent& s) : qmf::Handle<AgentImpl>() { PI::copy(*this, s); }
Agent::~Agent() { PI::dtor(*this); }
Agent& Agent::operator=(const Agent& s) { return PI::assign(*this, s); }
string Agent::getName() const { return isValid() ? impl->getName() : ""; }
uint32_t Agent::getEpoch() const { return isValid() ? impl->getEpoch() : 0; }
string Agent::getVendor() const { return isValid() ? impl->getVendor() : ""; }
string Agent::getProduct() const { return isValid() ? impl->getProduct() : ""; }
string Agent::getInstance() const { return isValid() ? impl->getInstance() : ""; }
const Variant& Agent::getAttribute(const string& k) const { return impl->getAttribute(k); }
const Variant::Map& Agent::getAttributes() const { return impl->getAttributes(); }
ConsoleEvent Agent::querySchema(Duration t) { return impl->querySchema(t); }
uint32_t Agent::querySchemaAsync() { return impl->querySchemaAsync(); }
ConsoleEvent Agent::query(const Query& q, Duration t) { return impl->query(q, t); }
ConsoleEvent Agent::query(const string& q, Duration t) { return impl->query(q, t); }
uint32_t Agent::queryAsync(const Query& q) { return impl->queryAsync(q); }
uint32_t Agent::queryAsync(const string& q) { return impl->queryAsync(q); }
ConsoleEvent Agent::callMethod(const string& m, const Variant::Map& a, const DataAddr& d, Duration t) { return impl->callMethod(m, a, d, t); }
uint32_t Agent::callMethodAsync(const string& m, const Variant::Map& a, const DataAddr& d) { return impl->callMethodAsync(m, a, d); }
uint32_t Agent::getPackageCount() const { return impl->getPackageCount(); }
const string& Agent::getPackage(uint32_t i) const { return impl->getPackage(i); }
uint32_t Agent::getSchemaIdCount(const string& p) const { return impl->getSchemaIdCount(p); }
SchemaId Agent::getSchemaId(const string& p, uint32_t i) const { return impl->getSchemaId(p, i); }
Schema Agent::getSchema(const SchemaId& s, Duration t) { return impl->getSchema(s, t); }



AgentImpl::AgentImpl(const std::string& n, uint32_t e, ConsoleSessionImpl& s) :
    name(n), directSubject(n), epoch(e), session(s), touched(true), untouchedCount(0), capability(0),
    sender(session.directSender), schemaCache(s.schemaCache)
{
}

void AgentImpl::setAttribute(const std::string& k, const qpid::types::Variant& v)
{
    attributes[k] = v;
    if (k == "qmf.agent_capability")
        try {
            capability = v.asUint32();
        } catch (std::exception&) {}
    if (k == "_direct_subject")
        try {
            directSubject = v.asString();
            sender = session.topicSender;
        } catch (std::exception&) {}
}

const Variant& AgentImpl::getAttribute(const string& k) const
{
    Variant::Map::const_iterator iter = attributes.find(k);
    if (iter == attributes.end())
        throw KeyNotFound(k);
    return iter->second;
}


ConsoleEvent AgentImpl::query(const Query& query, Duration timeout)
{
    boost::shared_ptr<SyncContext> context(new SyncContext());
    uint32_t correlator(session.correlator());
    ConsoleEvent result;

    {
        qpid::sys::Mutex::ScopedLock l(lock);
        contextMap[correlator] = context;
    }
    try {
        sendQuery(query, correlator);
        {
            uint64_t milliseconds = timeout.getMilliseconds();
            qpid::sys::Mutex::ScopedLock cl(context->lock);
            if (!context->response.isValid() || !context->response.isFinal())
                context->cond.wait(context->lock,
                                   qpid::sys::AbsTime(qpid::sys::now(),
                                                      qpid::sys::Duration(milliseconds * qpid::sys::TIME_MSEC)));
            if (context->response.isValid() &&
                ((context->response.getType() == CONSOLE_QUERY_RESPONSE && context->response.isFinal()) ||
                 (context->response.getType() == CONSOLE_EXCEPTION)))
                result = context->response;
            else {
                auto_ptr<ConsoleEventImpl> impl(new ConsoleEventImpl(CONSOLE_EXCEPTION));
                Data exception(new DataImpl());
                exception.setProperty("error_text", "Timed out waiting for the agent to respond");
                impl->addData(exception);
                result = ConsoleEvent(impl.release());
            }
        }
    } catch (qpid::types::Exception&) {
    }

    {
        qpid::sys::Mutex::ScopedLock l(lock);
        contextMap.erase(correlator);
    }

    return result;
}


ConsoleEvent AgentImpl::query(const string& text, Duration timeout)
{
    return query(stringToQuery(text), timeout);
}


uint32_t AgentImpl::queryAsync(const Query& query)
{
    uint32_t correlator(session.correlator());

    sendQuery(query, correlator);
    return correlator;
}


uint32_t AgentImpl::queryAsync(const string& text)
{
    return queryAsync(stringToQuery(text));
}


ConsoleEvent AgentImpl::callMethod(const string& method, const Variant::Map& args, const DataAddr& addr, Duration timeout)
{
    boost::shared_ptr<SyncContext> context(new SyncContext());
    uint32_t correlator(session.correlator());
    ConsoleEvent result;

    {
        qpid::sys::Mutex::ScopedLock l(lock);
        contextMap[correlator] = context;
    }
    try {
        sendMethod(method, args, addr, correlator);
        {
            uint64_t milliseconds = timeout.getMilliseconds();
            qpid::sys::Mutex::ScopedLock cl(context->lock);
            if (!context->response.isValid())
                context->cond.wait(context->lock,
                                   qpid::sys::AbsTime(qpid::sys::now(),
                                                      qpid::sys::Duration(milliseconds * qpid::sys::TIME_MSEC)));
            if (context->response.isValid())
                result = context->response;
            else {
                auto_ptr<ConsoleEventImpl> impl(new ConsoleEventImpl(CONSOLE_EXCEPTION));
                Data exception(new DataImpl());
                exception.setProperty("error_text", "Timed out waiting for the agent to respond");
                impl->addData(exception);
                result = ConsoleEvent(impl.release());
            }
        }
    } catch (qpid::types::Exception&) {
    }

    {
        qpid::sys::Mutex::ScopedLock l(lock);
        contextMap.erase(correlator);
    }

    return result;
}


uint32_t AgentImpl::callMethodAsync(const string& method, const Variant::Map& args, const DataAddr& addr)
{
    uint32_t correlator(session.correlator());

    sendMethod(method, args, addr, correlator);
    return correlator;
}


uint32_t AgentImpl::getPackageCount() const
{
    qpid::sys::Mutex::ScopedLock l(lock);

    //
    // Populate the package set.
    //
    for (set<SchemaId, SchemaIdCompare>::const_iterator iter = schemaIdSet.begin(); iter != schemaIdSet.end(); iter++)
        packageSet.insert(iter->getPackageName());

    return packageSet.size();
}


const string& AgentImpl::getPackage(uint32_t idx) const
{
    qpid::sys::Mutex::ScopedLock l(lock);
    uint32_t count(0);
    for (set<string>::const_iterator iter = packageSet.begin(); iter != packageSet.end(); iter++) {
        if (idx == count)
            return *iter;
        count++;
    }
    throw IndexOutOfRange();
}


uint32_t AgentImpl::getSchemaIdCount(const string& pname) const
{
    qpid::sys::Mutex::ScopedLock l(lock);
    uint32_t count(0);
    for (set<SchemaId, SchemaIdCompare>::const_iterator iter = schemaIdSet.begin(); iter != schemaIdSet.end(); iter++)
        if (iter->getPackageName() == pname)
            count++;
    return count;
}


SchemaId AgentImpl::getSchemaId(const string& pname, uint32_t idx) const
{
    qpid::sys::Mutex::ScopedLock l(lock);
    uint32_t count(0);
    for (set<SchemaId, SchemaIdCompare>::const_iterator iter = schemaIdSet.begin(); iter != schemaIdSet.end(); iter++) {
        if (iter->getPackageName() == pname) {
            if (idx == count)
                return *iter;
            count++;
        }
    }
    throw IndexOutOfRange();
}


Schema AgentImpl::getSchema(const SchemaId& id, Duration timeout)
{
    if (!schemaCache->haveSchema(id))
        //
        // The desired schema is not in the cache.  We need to asynchronously query the remote
        // agent for the information.  The call to schemaCache->getSchema will block waiting for
        // the response to be received.
        //
        sendSchemaRequest(id);

    return schemaCache->getSchema(id, timeout);
}


void AgentImpl::handleException(const Variant::Map& content, const Message& msg)
{
    const string& cid(msg.getCorrelationId());
    Variant::Map::const_iterator aIter;
    uint32_t correlator;
    boost::shared_ptr<SyncContext> context;

    try { correlator = boost::lexical_cast<uint32_t>(cid); }
    catch(const boost::bad_lexical_cast&) { correlator = 0; }

    {
        qpid::sys::Mutex::ScopedLock l(lock);
        map<uint32_t, boost::shared_ptr<SyncContext> >::iterator iter = contextMap.find(correlator);
        if (iter != contextMap.end())
            context = iter->second;
    }

    if (context.get() != 0) {
        //
        // This exception is associated with a synchronous request.
        //
        qpid::sys::Mutex::ScopedLock cl(context->lock);
        context->response = ConsoleEvent(new ConsoleEventImpl(CONSOLE_EXCEPTION));
        ConsoleEventImplAccess::get(context->response).addData(new DataImpl(content, this));
        ConsoleEventImplAccess::get(context->response).setAgent(this);
        context->cond.notify();
    } else {
        //
        // This exception is associated with an asynchronous request.
        //
        auto_ptr<ConsoleEventImpl> eventImpl(new ConsoleEventImpl(CONSOLE_EXCEPTION));
        eventImpl->setCorrelator(correlator);
        eventImpl->setAgent(this);
        eventImpl->addData(new DataImpl(content, this));
        session.enqueueEvent(eventImpl.release());
    }
}


void AgentImpl::handleMethodResponse(const Variant::Map& response, const Message& msg)
{
    const string& cid(msg.getCorrelationId());
    Variant::Map::const_iterator aIter;
    Variant::Map argMap;
    uint32_t correlator;
    boost::shared_ptr<SyncContext> context;

    QPID_LOG(trace, "RCVD MethodResponse cid=" << cid << " map=" << response);

    aIter = response.find("_arguments");
    if (aIter != response.end())
        argMap = aIter->second.asMap();

    try { correlator = boost::lexical_cast<uint32_t>(cid); }
    catch(const boost::bad_lexical_cast&) { correlator = 0; }

    {
        qpid::sys::Mutex::ScopedLock l(lock);
        map<uint32_t, boost::shared_ptr<SyncContext> >::iterator iter = contextMap.find(correlator);
        if (iter != contextMap.end())
            context = iter->second;
    }

    if (context.get() != 0) {
        //
        // This response is associated with a synchronous request.
        //
        qpid::sys::Mutex::ScopedLock cl(context->lock);
        context->response = ConsoleEvent(new ConsoleEventImpl(CONSOLE_METHOD_RESPONSE));
        ConsoleEventImplAccess::get(context->response).setArguments(argMap);
        ConsoleEventImplAccess::get(context->response).setAgent(this);
        context->cond.notify();
    } else {
        //
        // This response is associated with an asynchronous request.
        //
        auto_ptr<ConsoleEventImpl> eventImpl(new ConsoleEventImpl(CONSOLE_METHOD_RESPONSE));
        eventImpl->setCorrelator(correlator);
        eventImpl->setAgent(this);
        eventImpl->setArguments(argMap);
        session.enqueueEvent(eventImpl.release());
    }
}


void AgentImpl::handleDataIndication(const Variant::List& list, const Message& msg)
{
    Variant::Map::const_iterator aIter;
    const Variant::Map& props(msg.getProperties());
    boost::shared_ptr<SyncContext> context;

    aIter = props.find("qmf.content");
    if (aIter == props.end())
        return;

    string content_type(aIter->second.asString());
    if (content_type != "_event")
        return;

    for (Variant::List::const_iterator lIter = list.begin(); lIter != list.end(); lIter++) {
        const Variant::Map& eventMap(lIter->asMap());
        Data data(new DataImpl(eventMap, this));
        int severity(SEV_NOTICE);
        uint64_t timestamp(0);

        aIter = eventMap.find("_severity");
        if (aIter != eventMap.end())
            severity = int(aIter->second.asInt8());

        aIter = eventMap.find("_timestamp");
        if (aIter != eventMap.end())
            timestamp = aIter->second.asUint64();

        auto_ptr<ConsoleEventImpl> eventImpl(new ConsoleEventImpl(CONSOLE_EVENT));
        eventImpl->setAgent(this);
        eventImpl->addData(data);
        eventImpl->setSeverity(severity);
        eventImpl->setTimestamp(timestamp);
        if (data.hasSchema())
            learnSchemaId(data.getSchemaId());
        session.enqueueEvent(eventImpl.release());
    }
}


void AgentImpl::handleQueryResponse(const Variant::List& list, const Message& msg)
{
    const string& cid(msg.getCorrelationId());
    Variant::Map::const_iterator aIter;
    const Variant::Map& props(msg.getProperties());
    uint32_t correlator;
    bool final(false);
    boost::shared_ptr<SyncContext> context;

    aIter = props.find("partial");
    if (aIter == props.end())
        final = true;

    aIter = props.find("qmf.content");
    if (aIter == props.end())
        return;

    string content_type(aIter->second.asString());
    if (content_type != "_schema" && content_type != "_schema_id" && content_type != "_data")
        return;

    try { correlator = boost::lexical_cast<uint32_t>(cid); }
    catch(const boost::bad_lexical_cast&) { correlator = 0; }

    {
        qpid::sys::Mutex::ScopedLock l(lock);
        map<uint32_t, boost::shared_ptr<SyncContext> >::iterator iter = contextMap.find(correlator);
        if (iter != contextMap.end())
            context = iter->second;
    }

    if (context.get() != 0) {
        //
        // This response is associated with a synchronous request.
        //
        qpid::sys::Mutex::ScopedLock cl(context->lock);
        if (!context->response.isValid())
            context->response = ConsoleEvent(new ConsoleEventImpl(CONSOLE_QUERY_RESPONSE));

        if (content_type == "_data")
            for (Variant::List::const_iterator lIter = list.begin(); lIter != list.end(); lIter++) {
                Data data(new DataImpl(lIter->asMap(), this));
                ConsoleEventImplAccess::get(context->response).addData(data);
                if (data.hasSchema())
                    learnSchemaId(data.getSchemaId());
            }
        else if (content_type == "_schema_id")
            for (Variant::List::const_iterator lIter = list.begin(); lIter != list.end(); lIter++) {
                SchemaId schemaId(new SchemaIdImpl(lIter->asMap()));
                ConsoleEventImplAccess::get(context->response).addSchemaId(schemaId);
                learnSchemaId(schemaId);
            }
        else if (content_type == "_schema")
            for (Variant::List::const_iterator lIter = list.begin(); lIter != list.end(); lIter++) {
                Schema schema(new SchemaImpl(lIter->asMap()));
                schemaCache->declareSchema(schema);
            }

        if (final) {
            ConsoleEventImplAccess::get(context->response).setFinal();
            ConsoleEventImplAccess::get(context->response).setAgent(this);
            context->cond.notify();
        }
    } else {
        //
        // This response is associated with an asynchronous request.
        //
        auto_ptr<ConsoleEventImpl> eventImpl(new ConsoleEventImpl(CONSOLE_QUERY_RESPONSE));
        eventImpl->setCorrelator(correlator);
        eventImpl->setAgent(this);

        if (content_type == "_data")
            for (Variant::List::const_iterator lIter = list.begin(); lIter != list.end(); lIter++) {
                Data data(new DataImpl(lIter->asMap(), this));
                eventImpl->addData(data);
                if (data.hasSchema())
                    learnSchemaId(data.getSchemaId());
            }
        else if (content_type == "_schema_id")
            for (Variant::List::const_iterator lIter = list.begin(); lIter != list.end(); lIter++) {
                SchemaId schemaId(new SchemaIdImpl(lIter->asMap()));
                eventImpl->addSchemaId(schemaId);
                learnSchemaId(schemaId);
            }
        else if (content_type == "_schema")
            for (Variant::List::const_iterator lIter = list.begin(); lIter != list.end(); lIter++) {
                Schema schema(new SchemaImpl(lIter->asMap()));
                schemaCache->declareSchema(schema);
            }

        if (final)
            eventImpl->setFinal();
        if (content_type != "_schema")
            session.enqueueEvent(eventImpl.release());
    }
}


Query AgentImpl::stringToQuery(const std::string& text)
{
    qpid::messaging::AddressParser parser(text);
    Variant::Map map;
    Variant::Map::const_iterator iter;
    string className;
    string packageName;

    parser.parseMap(map);

    iter = map.find("class");
    if (iter != map.end())
        className = iter->second.asString();

    iter = map.find("package");
    if (iter != map.end())
        packageName = iter->second.asString();

    Query query(QUERY_OBJECT, className, packageName);

    iter = map.find("where");
    if (iter != map.end())
        query.setPredicate(iter->second.asList());

    return query;
}


void AgentImpl::sendQuery(const Query& query, uint32_t correlator)
{
    Message msg;
    Variant::Map map;
    Variant::Map& headers(msg.getProperties());

    headers[protocol::HEADER_KEY_METHOD] = protocol::HEADER_METHOD_REQUEST;
    headers[protocol::HEADER_KEY_OPCODE] = protocol::HEADER_OPCODE_QUERY_REQUEST;
    headers[protocol::HEADER_KEY_APP_ID] = protocol::HEADER_APP_ID_QMF;

    msg.setReplyTo(session.replyAddress);
    msg.setCorrelationId(boost::lexical_cast<string>(correlator));
    msg.setSubject(directSubject);
    string userId(session.connection.getAuthenticatedUsername());
    if (!userId.empty())
        msg.setUserId(userId);
    encode(QueryImplAccess::get(query).asMap(), msg);
    if (sender.isValid()) {
        sender.send(msg);
        QPID_LOG(trace, "SENT QueryRequest to=" << sender.getName() << "/" << directSubject << " cid=" << correlator);
    }
}


void AgentImpl::sendMethod(const string& method, const Variant::Map& args, const DataAddr& addr, uint32_t correlator)
{
    Message msg;
    Variant::Map map;
    Variant::Map& headers(msg.getProperties());

    headers[protocol::HEADER_KEY_METHOD] = protocol::HEADER_METHOD_REQUEST;
    headers[protocol::HEADER_KEY_OPCODE] = protocol::HEADER_OPCODE_METHOD_REQUEST;
    headers[protocol::HEADER_KEY_APP_ID] = protocol::HEADER_APP_ID_QMF;

    map["_method_name"] = method;
    map["_object_id"] = addr.asMap();
    map["_arguments"] = args;

    msg.setReplyTo(session.replyAddress);
    msg.setCorrelationId(boost::lexical_cast<string>(correlator));
    msg.setSubject(directSubject);
    string userId(session.connection.getAuthenticatedUsername());
    if (!userId.empty())
        msg.setUserId(userId);
    encode(map, msg);
    if (sender.isValid()) {
        sender.send(msg);
        QPID_LOG(trace, "SENT MethodRequest method=" << method << " to=" << sender.getName() << "/" << directSubject << " content=" << map << " cid=" << correlator);
    }
}

void AgentImpl::sendSchemaRequest(const SchemaId& id)
{
    uint32_t correlator(session.correlator());

    if (capability >= AGENT_CAPABILITY_V2_SCHEMA) {
        Query query(QUERY_SCHEMA, id);
        sendQuery(query, correlator);
        return;
    }

#define RAW_BUFFER_SIZE 1024
    char rawBuffer[RAW_BUFFER_SIZE];
    qpid::management::Buffer buffer(rawBuffer, RAW_BUFFER_SIZE);

    buffer.putOctet('A');
    buffer.putOctet('M');
    buffer.putOctet('2');
    buffer.putOctet('S');
    buffer.putLong(correlator);
    buffer.putShortString(id.getPackageName());
    buffer.putShortString(id.getName());
    buffer.putBin128(id.getHash().data());

    string content(rawBuffer, buffer.getPosition());

    Message msg;
    msg.setReplyTo(session.replyAddress);
    msg.setContent(content);
    msg.setSubject(directSubject);
    string userId(session.connection.getAuthenticatedUsername());
    if (!userId.empty())
        msg.setUserId(userId);
    if (sender.isValid()) {
        sender.send(msg);
        QPID_LOG(trace, "SENT V1SchemaRequest to=" << sender.getName() << "/" << directSubject);
    }
}


void AgentImpl::learnSchemaId(const SchemaId& id)
{
    schemaCache->declareSchemaId(id);
    schemaIdSet.insert(id);
}


AgentImpl& AgentImplAccess::get(Agent& item)
{
    return *item.impl;
}


const AgentImpl& AgentImplAccess::get(const Agent& item)
{
    return *item.impl;
}
