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

#include "qmf/PrivateImplRef.h"
#include "qmf/ConsoleSessionImpl.h"
#include "qmf/AgentImpl.h"
#include "qmf/SchemaId.h"
#include "qmf/SchemaImpl.h"
#include "qmf/ConsoleEventImpl.h"
#include "qmf/constants.h"
#include "qpid/log/Statement.h"
#include "qpid/messaging/AddressParser.h"
#include "qpid/messaging/Sender.h"
#include "qpid/messaging/Receiver.h"

using namespace std;
using namespace qmf;
using qpid::messaging::Address;
using qpid::messaging::Connection;
using qpid::messaging::Receiver;
using qpid::messaging::Sender;
using qpid::messaging::Duration;
using qpid::messaging::Message;
using qpid::types::Variant;

typedef qmf::PrivateImplRef<ConsoleSession> PI;

ConsoleSession::ConsoleSession(ConsoleSessionImpl* impl) { PI::ctor(*this, impl); }
ConsoleSession::ConsoleSession(const ConsoleSession& s) : qmf::Handle<ConsoleSessionImpl>() { PI::copy(*this, s); }
ConsoleSession::~ConsoleSession() { PI::dtor(*this); }
ConsoleSession& ConsoleSession::operator=(const ConsoleSession& s) { return PI::assign(*this, s); }

ConsoleSession::ConsoleSession(Connection& c, const string& o) { PI::ctor(*this, new ConsoleSessionImpl(c, o)); }
void ConsoleSession::setDomain(const string& d) { impl->setDomain(d); }
void ConsoleSession::setAgentFilter(const string& f) { impl->setAgentFilter(f); }
void ConsoleSession::open() { impl->open(); }
void ConsoleSession::close() { impl->close(); }
bool ConsoleSession::nextEvent(ConsoleEvent& e, Duration t) { return impl->nextEvent(e, t); }
int ConsoleSession::pendingEvents() const { return impl->pendingEvents(); }
uint32_t ConsoleSession::getAgentCount() const { return impl->getAgentCount(); }
Agent ConsoleSession::getAgent(uint32_t i) const { return impl->getAgent(i); }
Agent ConsoleSession::getConnectedBrokerAgent() const { return impl->getConnectedBrokerAgent(); }
Subscription ConsoleSession::subscribe(const Query& q, const string& f, const string& o) { return impl->subscribe(q, f, o); }
Subscription ConsoleSession::subscribe(const string& q, const string& f, const string& o) { return impl->subscribe(q, f, o); }

//========================================================================================
// Impl Method Bodies
//========================================================================================

ConsoleSessionImpl::ConsoleSessionImpl(Connection& c, const string& options) :
    connection(c), domain("default"), maxAgentAgeMinutes(5), listenOnDirect(true), strictSecurity(false), maxThreadWaitTime(5),
    opened(false), eventNotifier(0), thread(0), threadCanceled(false), lastVisit(0), lastAgePass(0),
    connectedBrokerInAgentList(false), schemaCache(new SchemaCache()), nextCorrelator(1)
{
    if (!options.empty()) {
        qpid::messaging::AddressParser parser(options);
        Variant::Map optMap;
        Variant::Map::const_iterator iter;

        parser.parseMap(optMap);

        iter = optMap.find("domain");
        if (iter != optMap.end())
            domain = iter->second.asString();

        iter = optMap.find("max-agent-age");
        if (iter != optMap.end())
            maxAgentAgeMinutes = iter->second.asUint32();

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

    if (maxThreadWaitTime > 60)
        maxThreadWaitTime = 60;
}


ConsoleSessionImpl::~ConsoleSessionImpl()
{
    if (opened)
        close();

    if (thread) {
        thread->join();
        delete thread;
    }
}


void ConsoleSessionImpl::setAgentFilter(const string& predicate)
{
    agentQuery = Query(QUERY_OBJECT, predicate);

    //
    // Purge the agent list of any agents that don't match the filter.
    //
    {
        qpid::sys::Mutex::ScopedLock l(lock);
        map<string, Agent> toDelete;
        for (map<string, Agent>::iterator iter = agents.begin(); iter != agents.end(); iter++)
            if (!agentQuery.matchesPredicate(iter->second.getAttributes())) {
                toDelete[iter->first] = iter->second;
                if (iter->second.getName() == connectedBrokerAgent.getName())
                    connectedBrokerInAgentList = false;
            }

        for (map<string, Agent>::iterator iter = toDelete.begin(); iter != toDelete.end(); iter++) {
            agents.erase(iter->first);
            auto_ptr<ConsoleEventImpl> eventImpl(new ConsoleEventImpl(CONSOLE_AGENT_DEL, AGENT_DEL_FILTER));
            eventImpl->setAgent(iter->second);
            enqueueEventLH(eventImpl.release());
        }

        if (!connectedBrokerInAgentList && connectedBrokerAgent.isValid() &&
            agentQuery.matchesPredicate(connectedBrokerAgent.getAttributes())) {
            agents[connectedBrokerAgent.getName()] = connectedBrokerAgent;
            connectedBrokerInAgentList = true;

            //
            // Enqueue a notification of the new agent.
            //
            auto_ptr<ConsoleEventImpl> eventImpl(new ConsoleEventImpl(CONSOLE_AGENT_ADD));
            eventImpl->setAgent(connectedBrokerAgent);
            enqueueEventLH(ConsoleEvent(eventImpl.release()));
        }
    }

    //
    // Broadcast an agent locate request with our new criteria.
    //
    if (opened)
        sendAgentLocate();
}


void ConsoleSessionImpl::open()
{
    if (opened)
        throw QmfException("The session is already open");

    // If the thread exists, join and delete it before creating a new one.
    if (thread) {
        thread->join();
        delete thread;
    }

    // Establish messaging addresses
    directBase = "qmf." + domain + ".direct";
    topicBase = "qmf." + domain + ".topic";

    string myKey("direct-console." + qpid::types::Uuid(true).str());

    replyAddress = Address(topicBase + "/" + myKey + ";{node:{type:topic}}");

    // Create AMQP session, receivers, and senders
    session = connection.createSession();
    Receiver directRx = session.createReceiver(replyAddress);
    Receiver topicRx = session.createReceiver(topicBase + "/agent.#"); // TODO: be more discriminating
    if (!strictSecurity) {
        Receiver legacyRx = session.createReceiver("amq.direct/" + myKey + ";{node:{type:topic}}");
        legacyRx.setCapacity(64);
        directSender = session.createSender(directBase + ";{create:never,node:{type:topic}}");
        directSender.setCapacity(128);
    }

    directRx.setCapacity(64);
    topicRx.setCapacity(128);

    topicSender = session.createSender(topicBase + ";{create:never,node:{type:topic}}");

    topicSender.setCapacity(128);

    // Start the receiver thread
    threadCanceled = false;
    opened = true;
    thread = new qpid::sys::Thread(*this);

    // Send an agent_locate to direct address 'broker' to identify the connected-broker-agent.
    sendBrokerLocate();
    if (agentQuery)
        sendAgentLocate();
}


void ConsoleSessionImpl::closeAsync()
{
    if (!opened)
        throw QmfException("The session is already closed");

    // Stop the receiver thread.  Don't join it until the destructor is called or open() is called.
    threadCanceled = true;
    opened = false;
}


void ConsoleSessionImpl::close()
{
    closeAsync();

    if (thread) {
        thread->join();
        delete thread;
        thread = 0;
    }
}


bool ConsoleSessionImpl::nextEvent(ConsoleEvent& event, Duration timeout)
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


int ConsoleSessionImpl::pendingEvents() const
{
    qpid::sys::Mutex::ScopedLock l(lock);
    return eventQueue.size();
}


void ConsoleSessionImpl::setEventNotifier(EventNotifierImpl* notifier)
{
    qpid::sys::Mutex::ScopedLock l(lock);
    eventNotifier = notifier;
}


EventNotifierImpl* ConsoleSessionImpl::getEventNotifier() const
{
    qpid::sys::Mutex::ScopedLock l(lock);
    return eventNotifier;
}


uint32_t ConsoleSessionImpl::getAgentCount() const
{
    qpid::sys::Mutex::ScopedLock l(lock);
    return agents.size();
}


Agent ConsoleSessionImpl::getAgent(uint32_t i) const
{
    qpid::sys::Mutex::ScopedLock l(lock);
    uint32_t count = 0;
    for (map<string, Agent>::const_iterator iter = agents.begin(); iter != agents.end(); iter++)
        if (count++ == i)
            return iter->second;
    throw IndexOutOfRange();
}


Subscription ConsoleSessionImpl::subscribe(const Query&, const string&, const string&)
{
    return Subscription();
}


Subscription ConsoleSessionImpl::subscribe(const string&, const string&, const string&)
{
    return Subscription();
}


void ConsoleSessionImpl::enqueueEvent(const ConsoleEvent& event)
{
    qpid::sys::Mutex::ScopedLock l(lock);
    enqueueEventLH(event);
}


void ConsoleSessionImpl::enqueueEventLH(const ConsoleEvent& event)
{
    bool notify = eventQueue.empty();
    eventQueue.push(event);
    if (notify) {
        cond.notify();
        alertEventNotifierLH(true);
    }
}


void ConsoleSessionImpl::dispatch(Message msg)
{
    const Variant::Map& properties(msg.getProperties());
    Variant::Map::const_iterator iter;
    Variant::Map::const_iterator oiter;

    oiter = properties.find(protocol::HEADER_KEY_OPCODE);
    iter = properties.find(protocol::HEADER_KEY_APP_ID);
    if (iter == properties.end())
        iter = properties.find("app_id");
    if (iter != properties.end() && iter->second.asString() == protocol::HEADER_APP_ID_QMF && oiter != properties.end()) {
        //
        // Dispatch a QMFv2 formatted message
        //
        const string& opcode = oiter->second.asString();

        iter = properties.find(protocol::HEADER_KEY_AGENT);
        if (iter == properties.end()) {
            QPID_LOG(trace, "Message received with no 'qmf.agent' header");
            return;
        }
        const string& agentName = iter->second.asString();

        Agent agent;
        {
            qpid::sys::Mutex::ScopedLock l(lock);
            map<string, Agent>::iterator aIter = agents.find(agentName);
            if (aIter != agents.end()) {
                agent = aIter->second;
                AgentImplAccess::get(agent).touch();
            }
        }

        if (msg.getContentType() == "amqp/map" &&
            (opcode == protocol::HEADER_OPCODE_AGENT_HEARTBEAT_INDICATION || opcode == protocol::HEADER_OPCODE_AGENT_LOCATE_RESPONSE)) {
            //
            // This is the one case where it's ok (necessary actually) to receive a QMFv2
            // message from an unknown agent (how else are they going to get known?)
            //
            Variant::Map content;
            decode(msg, content);
            handleAgentUpdate(agentName, content, msg);
            return;
        }

        if (!agent.isValid())
            return;

        AgentImpl& agentImpl(AgentImplAccess::get(agent));

        if (msg.getContentType() == "amqp/map") {
            Variant::Map content;
            decode(msg, content);

            if      (opcode == protocol::HEADER_OPCODE_EXCEPTION)       agentImpl.handleException(content, msg);
            else if (opcode == protocol::HEADER_OPCODE_METHOD_RESPONSE) agentImpl.handleMethodResponse(content, msg);
            else
                QPID_LOG(error, "Received a map-formatted QMFv2 message with opcode=" << opcode);

            return;
        }

        if (msg.getContentType() == "amqp/list") {
            Variant::List content;
            decode(msg, content);

            if      (opcode == protocol::HEADER_OPCODE_QUERY_RESPONSE)  agentImpl.handleQueryResponse(content, msg);
            else if (opcode == protocol::HEADER_OPCODE_DATA_INDICATION) agentImpl.handleDataIndication(content, msg);
            else
                QPID_LOG(error, "Received a list-formatted QMFv2 message with opcode=" << opcode);

            return;
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

        if (v1Opcode == 's') handleV1SchemaResponse(buffer, seq, msg);
        else {
            QPID_LOG(trace, "Unknown or Unsupported QMFv1 opcode: " << v1Opcode);
        }
    }
}


void ConsoleSessionImpl::sendBrokerLocate()
{
    Message msg;
    Variant::Map& headers(msg.getProperties());

    headers[protocol::HEADER_KEY_METHOD] = protocol::HEADER_METHOD_REQUEST;
    headers[protocol::HEADER_KEY_OPCODE] = protocol::HEADER_OPCODE_AGENT_LOCATE_REQUEST;
    headers[protocol::HEADER_KEY_APP_ID] = protocol::HEADER_APP_ID_QMF;

    msg.setReplyTo(replyAddress);
    msg.setCorrelationId("broker-locate");
    msg.setSubject("broker");

    Sender sender = session.createSender(directBase + ";{create:never,node:{type:topic}}");
    sender.send(msg);
    sender.close();

    QPID_LOG(trace, "SENT AgentLocate to broker");
}


void ConsoleSessionImpl::sendAgentLocate()
{
    Message msg;
    Variant::Map& headers(msg.getProperties());
    static const string subject("console.request.agent_locate");

    headers[protocol::HEADER_KEY_METHOD] = protocol::HEADER_METHOD_REQUEST;
    headers[protocol::HEADER_KEY_OPCODE] = protocol::HEADER_OPCODE_AGENT_LOCATE_REQUEST;
    headers[protocol::HEADER_KEY_APP_ID] = protocol::HEADER_APP_ID_QMF;

    msg.setReplyTo(replyAddress);
    msg.setCorrelationId("agent-locate");
    msg.setSubject(subject);
    encode(agentQuery.getPredicate(), msg);

    topicSender.send(msg);

    QPID_LOG(trace, "SENT AgentLocate to=" << topicSender.getName() << "/" << subject);
}


void ConsoleSessionImpl::handleAgentUpdate(const string& agentName, const Variant::Map& content, const Message& msg)
{
    Variant::Map::const_iterator iter;
    Agent agent;
    uint32_t epoch(0);
    string cid(msg.getCorrelationId());

    iter = content.find("_values");
    if (iter == content.end())
        return;
    const Variant::Map& in_attrs(iter->second.asMap());
    Variant::Map attrs;

    //
    // Copy the map from the message to "attrs".  Translate any old-style
    // keys to their new key values in the process.
    //
    for (iter = in_attrs.begin(); iter != in_attrs.end(); iter++) {
        if      (iter->first == "epoch")
            attrs[protocol::AGENT_ATTR_EPOCH] = iter->second;
        else if (iter->first == "timestamp")
            attrs[protocol::AGENT_ATTR_TIMESTAMP] = iter->second;
        else if (iter->first == "heartbeat_interval")
            attrs[protocol::AGENT_ATTR_HEARTBEAT_INTERVAL] = iter->second;
        else
            attrs[iter->first] = iter->second;
    }

    iter = attrs.find(protocol::AGENT_ATTR_EPOCH);
    if (iter != attrs.end())
        epoch = iter->second.asUint32();

    if (cid == "broker-locate") {
        qpid::sys::Mutex::ScopedLock l(lock);
        auto_ptr<AgentImpl> impl(new AgentImpl(agentName, epoch, *this));
        for (iter = attrs.begin(); iter != attrs.end(); iter++)
            if (iter->first != protocol::AGENT_ATTR_EPOCH)
                impl->setAttribute(iter->first, iter->second);
        agent = Agent(impl.release());
        connectedBrokerAgent = agent;
        if (!agentQuery || agentQuery.matchesPredicate(attrs)) {
            connectedBrokerInAgentList = true;
            agents[agentName] = agent;

            //
            // Enqueue a notification of the new agent.
            //
            auto_ptr<ConsoleEventImpl> eventImpl(new ConsoleEventImpl(CONSOLE_AGENT_ADD));
            eventImpl->setAgent(agent);
            enqueueEventLH(ConsoleEvent(eventImpl.release()));
        }
        return;
    }

    //
    // Check this agent against the agent filter.  Exit if it doesn't match.
    // (only if this isn't the connected broker agent)
    //
    if (agentQuery && (!agentQuery.matchesPredicate(attrs)))
        return;

    QPID_LOG(trace, "RCVD AgentHeartbeat from an agent matching our filter: " << agentName);

    {
        qpid::sys::Mutex::ScopedLock l(lock);
        map<string, Agent>::iterator aIter = agents.find(agentName);
        if (aIter == agents.end()) {
            //
            // This is a new agent.  We have no current record of its existence.
            //
            auto_ptr<AgentImpl> impl(new AgentImpl(agentName, epoch, *this));
            for (iter = attrs.begin(); iter != attrs.end(); iter++)
                if (iter->first != protocol::AGENT_ATTR_EPOCH)
                    impl->setAttribute(iter->first, iter->second);
            agent = Agent(impl.release());
            agents[agentName] = agent;

            //
            // Enqueue a notification of the new agent.
            //
            auto_ptr<ConsoleEventImpl> eventImpl(new ConsoleEventImpl(CONSOLE_AGENT_ADD));
            eventImpl->setAgent(agent);
            enqueueEventLH(ConsoleEvent(eventImpl.release()));
        } else {
            //
            // This is a refresh of an agent we are already tracking.
            //
            bool detectedRestart(false);
            agent = aIter->second;
            AgentImpl& impl(AgentImplAccess::get(agent));
            impl.touch();
            if (impl.getEpoch() != epoch) {
                //
                // The agent has restarted since the last time we heard from it.
                // Enqueue a notification.
                //
                impl.setEpoch(epoch);
                auto_ptr<ConsoleEventImpl> eventImpl(new ConsoleEventImpl(CONSOLE_AGENT_RESTART));
                eventImpl->setAgent(agent);
                enqueueEventLH(ConsoleEvent(eventImpl.release()));
                detectedRestart = true;
            }

            iter = attrs.find(protocol::AGENT_ATTR_SCHEMA_UPDATED_TIMESTAMP);
            if (iter != attrs.end()) {
                uint64_t ts(iter->second.asUint64());
                if (ts > impl.getAttribute(protocol::AGENT_ATTR_SCHEMA_UPDATED_TIMESTAMP).asUint64()) {
                    //
                    // The agent has added new schema entries since we last heard from it.
                    // Update the attribute and, if this doesn't accompany a restart, enqueue a notification.
                    //
                    if (!detectedRestart) {
                        auto_ptr<ConsoleEventImpl> eventImpl(new ConsoleEventImpl(CONSOLE_AGENT_SCHEMA_UPDATE));
                        eventImpl->setAgent(agent);
                        enqueueEventLH(ConsoleEvent(eventImpl.release()));
                    }
                    impl.setAttribute(protocol::AGENT_ATTR_SCHEMA_UPDATED_TIMESTAMP, iter->second);
                }
            }
        }
    }
}


void ConsoleSessionImpl::handleV1SchemaResponse(qpid::management::Buffer& buffer, uint32_t, const Message&)
{
    QPID_LOG(trace, "RCVD V1SchemaResponse");
    Schema schema(new SchemaImpl(buffer));
    schemaCache->declareSchema(schema);
}


void ConsoleSessionImpl::periodicProcessing(uint64_t seconds)
{
    //
    // The granularity of this timer is seconds.  Don't waste time looking for work if
    // it's been less than a second since we last visited.
    //
    if (seconds == lastVisit)
        return;
    lastVisit = seconds;

    //
    // Handle the aging of agent records
    //
    if (lastAgePass == 0)
        lastAgePass = seconds;
    if (seconds - lastAgePass >= 60) {
        lastAgePass = seconds;
        map<string, Agent> toDelete;
        qpid::sys::Mutex::ScopedLock l(lock);

        for (map<string, Agent>::iterator iter = agents.begin(); iter != agents.end(); iter++)
            if ((iter->second.getName() != connectedBrokerAgent.getName()) &&
                (AgentImplAccess::get(iter->second).age() > maxAgentAgeMinutes))
                toDelete[iter->first] = iter->second;

        for (map<string, Agent>::iterator iter = toDelete.begin(); iter != toDelete.end(); iter++) {
            agents.erase(iter->first);
            auto_ptr<ConsoleEventImpl> eventImpl(new ConsoleEventImpl(CONSOLE_AGENT_DEL, AGENT_DEL_AGED));
            eventImpl->setAgent(iter->second);
            enqueueEventLH(eventImpl.release());
        }
    }
}


void ConsoleSessionImpl::alertEventNotifierLH(bool readable)
{
    if (eventNotifier)
        eventNotifier->setReadable(readable);
}


void ConsoleSessionImpl::run()
{
    QPID_LOG(debug, "ConsoleSession thread started");

    try {
        while (!threadCanceled) {
            periodicProcessing((uint64_t) qpid::sys::Duration::FromEpoch() /
                               qpid::sys::TIME_SEC);

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
        enqueueEvent(ConsoleEvent(new ConsoleEventImpl(CONSOLE_THREAD_FAILED)));
    }

    session.close();
    QPID_LOG(debug, "ConsoleSession thread exiting");
}


ConsoleSessionImpl& ConsoleSessionImplAccess::get(ConsoleSession& session)
{
  return *session.impl;
}


const ConsoleSessionImpl& ConsoleSessionImplAccess::get(const ConsoleSession& session)
{
  return *session.impl;
}
