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
#include "qpid/log/Statement.h"
#include "qpid/messaging/AddressParser.h"
#include "qpid/messaging/Sender.h"
#include "qpid/messaging/Receiver.h"

using namespace std;
using namespace qpid::messaging;
using namespace qmf;
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
uint32_t ConsoleSession::getAgentCount() const { return impl->getAgentCount(); }
Agent ConsoleSession::getAgent(uint32_t i) const { return impl->getAgent(i); }
Agent ConsoleSession::getConnectedBrokerAgent() const { return impl->getConnectedBrokerAgent(); }

//========================================================================================
// Impl Method Bodies
//========================================================================================

ConsoleSessionImpl::ConsoleSessionImpl(Connection& c, const string& options) :
    connection(c), domain("default"), opened(false), thread(0), threadCanceled(false),
    lastVisit(0), schemaCache(new SchemaCache())
{
    if (!options.empty()) {
        qpid::messaging::AddressParser parser(options);
        Variant::Map optMap;
        Variant::Map::const_iterator iter;

        parser.parseMap(optMap);

        iter = optMap.find("domain");
        if (iter != optMap.end())
            domain = iter->second.asString();
    }
}


ConsoleSessionImpl::~ConsoleSessionImpl()
{
    if (opened)
        close();
}


void ConsoleSessionImpl::setAgentFilter(const string&)
{
    //
    // TODO: Setup the new agent filter
    // TODO: Purge the agent list of any agents that don't match the filter
    // TODO: Send an agent locate with the new filter
    //
}


void ConsoleSessionImpl::open()
{
    if (opened)
        throw QmfException("The session is already open");

    // Establish messaging addresses
    directBase = "qmf." + domain + ".direct";
    topicBase = "qmf." + domain + ".topic";

    string myKey("qmf-console-" + qpid::types::Uuid(true).str());

    replyAddress = Address(directBase + "/" + myKey + ";{node:{type:topic}}");

    // Create AMQP session, receivers, and senders
    session = connection.createSession();
    Receiver directRx = session.createReceiver(replyAddress);
    Receiver topicRx = session.createReceiver(topicBase + "/agent.#"); // TODO: be more discriminating
    Receiver legacyRx = session.createReceiver("amq.direct/" + myKey + ";{node:{type:topic}}");

    directRx.setCapacity(64);
    topicRx.setCapacity(64);
    legacyRx.setCapacity(64);

    // Start the receiver thread
    threadCanceled = false;
    thread = new qpid::sys::Thread(*this);

    // Send an agent_locate to direct address 'broker' to identify the connected-broker-agent.
    sendBrokerLocate();

    opened = true;
}


void ConsoleSessionImpl::close()
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


bool ConsoleSessionImpl::nextEvent(ConsoleEvent& event, Duration timeout)
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


void ConsoleSessionImpl::enqueueEvent(const ConsoleEvent& event)
{
    qpid::sys::Mutex::ScopedLock l(lock);
    enqueueEventLH(event);
}


void ConsoleSessionImpl::enqueueEventLH(const ConsoleEvent& event)
{
    bool notify = eventQueue.empty();
    eventQueue.push(event);
    if (notify)
        cond.notify();
}


void ConsoleSessionImpl::dispatch(Message msg)
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
        const string& opcode = iter->second.asString();

        iter = properties.find("qmf.agent");
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
            (opcode == "_agent_heartbeat_indication" || opcode == "_agent_locate_response")) {
            //
            // This is the one case where it's ok (necessary actually) to receive a QMFv2
            // message from an unknown agent (how else are they going to get known?)
            //
            Variant::Map content;
            decode(msg, content);
            handleAgentUpdate(agentName, content, msg);
            return;
        }

        if (!agent.isValid()) {
            QPID_LOG(trace, "Received a QMFv2 message with opcode=" << opcode <<
                     " from an unknown agent " << agentName);
            return;
        }

        AgentImpl& agentImpl(AgentImplAccess::get(agent));

        if (msg.getContentType() == "amqp/map") {
            Variant::Map content;
            decode(msg, content);

            if      (opcode == "_exception")       agentImpl.handleException(content, msg);
            else if (opcode == "_method_response") agentImpl.handleMethodResponse(content, msg);
            else
                QPID_LOG(error, "Received a map-formatted QMFv2 message with opcode=" << opcode);

            return;
        }

        if (msg.getContentType() == "amqp/list") {
            Variant::List content;
            decode(msg, content);

            if      (opcode == "_query_response")  agentImpl.handleQueryResponse(content, msg);
            else if (opcode == "_data_indication") agentImpl.handleDataIndication(content, msg);
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

    headers["method"] = "request";
    headers["qmf.opcode"] = "_agent_locate_request";
    headers["x-amqp-0-10.app-id"] = "qmf2";

    msg.setReplyTo(replyAddress);
    msg.setCorrelationId("broker-locate");
    Sender sender(session.createSender(directBase + "/broker"));
    sender.send(msg);
    sender.close();

    QPID_LOG(trace, "SENT AgentLocate to broker");
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
    Variant::Map attrs(iter->second.asMap());

    //
    // TODO: Check this agent against the agent filter.  Exit if it doesn't match.
    //       (only if this isn't the connected broker agent)
    //

    iter = content.find("epoch");
    if (iter != content.end())
        epoch = iter->second.asUint32();

    {
        qpid::sys::Mutex::ScopedLock l(lock);
        map<string, Agent>::iterator aIter = agents.find(agentName);
        if (aIter == agents.end()) {
            auto_ptr<AgentImpl> impl(new AgentImpl(agentName, epoch, *this));
            for (iter = attrs.begin(); iter != attrs.end(); iter++)
                if (iter->first != "epoch")
                    impl->setAttribute(iter->first, iter->second);
            agent = Agent(impl.release());
            agents[agentName] = agent;

            auto_ptr<ConsoleEventImpl> eventImpl(new ConsoleEventImpl(CONSOLE_AGENT_ADD));
            eventImpl->setAgent(agent);
            enqueueEventLH(ConsoleEvent(eventImpl.release()));
        } else
            agent = aIter->second;

        if (cid == "broker-locate")
            connectedBrokerAgent = agent;
    }

    AgentImplAccess::get(agent).touch();

    //
    // Changes we are interested in:
    //
    //   agentEpoch    - indicates that the agent restarted since we last heard from it
    //   schemaUpdated - indicates that the agent has registered new schemata
    //
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
    // TODO: Handle the aging of agent records
    //
}


void ConsoleSessionImpl::run()
{
    QPID_LOG(debug, "ConsoleSession thread started");

    try {
        while (!threadCanceled) {
            periodicProcessing((uint64_t) qpid::sys::Duration(qpid::sys::EPOCH, qpid::sys::now()) /
                               qpid::sys::TIME_SEC);

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
        enqueueEvent(ConsoleEvent(new ConsoleEventImpl(CONSOLE_THREAD_FAILED)));
    }

    QPID_LOG(debug, "ConsoleSession thread exiting");
}

