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
#include "qpid/broker/amqp_0_10/Connection.h"

#include "qpid/broker/ConnectionObserver.h"
#include "qpid/broker/SessionOutputException.h"
#include "qpid/broker/SessionState.h"
#include "qpid/broker/Bridge.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/Queue.h"
#include "qpid/management/ManagementAgent.h"
#include "qpid/sys/ConnectionOutputHandler.h"
#include "qpid/sys/SecuritySettings.h"
#include "qpid/sys/Timer.h"

#include "qpid/log/Statement.h"
#include "qpid/ptr_map.h"
#include "qpid/framing/AMQP_ClientProxy.h"
#include "qpid/framing/enum.h"
#include "qpid/framing/MessageTransferBody.h"
#include "qmf/org/apache/qpid/broker/EventClientConnect.h"
#include "qmf/org/apache/qpid/broker/EventClientDisconnect.h"

#include <boost/bind.hpp>
#include <boost/ptr_container/ptr_vector.hpp>

#include <algorithm>
#include <iostream>
#include <assert.h>

using std::string;

using namespace qpid::sys;
using namespace qpid::framing;
using qpid::ptr_map_ptr;
using qpid::management::ManagementAgent;
using qpid::management::ManagementObject;
using qpid::management::Manageable;
using qpid::management::Args;
namespace _qmf = qmf::org::apache::qpid::broker;

namespace qpid {
namespace broker {
namespace amqp_0_10 {

struct ConnectionTimeoutTask : public sys::TimerTask {
    sys::Timer& timer;
    Connection& connection;

    ConnectionTimeoutTask(uint16_t hb, sys::Timer& t, Connection& c) :
        TimerTask(Duration(hb*2*TIME_SEC),"ConnectionTimeout"),
        timer(t),
        connection(c)
    {}

    void touch() {
        restart();
    }

    void fire() {
        // If we get here then we've not received any traffic in the timeout period
        // Schedule closing the connection for the io thread
        QPID_LOG(error, "Connection " << connection.getMgmtId()
                 << " timed out: closing");
        connection.abort();
    }
};
/**
 * A ConnectionOutputHandler that delegates to another
 * ConnectionOutputHandler.  Allows you to inspect outputting frames
 */
class FrameInspector : public sys::ConnectionOutputHandler
{
public:
    FrameInspector(ConnectionOutputHandler* p, framing::FrameHandler* i) :
        next(p),
        intercepter(i)
    {
        assert(next);
        assert(intercepter);
    }

    void close() { next->close(); }
    void abort() { next->abort(); }
    void connectionEstablished() { next->connectionEstablished(); }
    void activateOutput() { next->activateOutput(); }
    void handle(framing::AMQFrame& f) { intercepter->handle(f); next->handle(f); }

private:
    ConnectionOutputHandler* next;
    framing::FrameHandler* intercepter;
};

/**
 * Chained ConnectionOutputHandler that allows outgoing frames to be
 * tracked (for updating mgmt stats).
 */
class OutboundFrameTracker : public framing::FrameHandler
{
public:
    OutboundFrameTracker(Connection& _con) : con(_con) {}
    void handle(framing::AMQFrame& f)
    {
        con.sent(f);
    }
private:
    Connection& con;
};

Connection::Connection(ConnectionOutputHandler* out_,
                       Broker& broker_, const
                       std::string& mgmtId_,
                       const qpid::sys::SecuritySettings& external,
                       bool link_,
                       uint64_t objectId_
) :
    outboundTracker(new OutboundFrameTracker(*this)),
    out(new FrameInspector(out_, outboundTracker.get())),
    broker(broker_),
    framemax(65535),
    heartbeat(0),
    heartbeatmax(120),
    isDefaultRealm(false),
    securitySettings(external),
    link(link_),
    adapter(*this, link),
    mgmtClosing(false),
    mgmtId(mgmtId_),
    links(broker_.getLinks()),
    agent(0),
    timer(broker_.getTimer()),
    objectId(objectId_)
{
    broker.getConnectionObservers().connection(*this);
    assert(agent == 0);
    assert(mgmtObject == 0);
    Manageable* parent = broker.GetVhostObject();
    if (parent != 0) {
        agent = broker.getManagementAgent();
        if (agent != 0) {
            // TODO set last bool true if system connection
            mgmtObject = _qmf::Connection::shared_ptr(new _qmf::Connection(agent, this, parent, mgmtId, !link, false, "AMQP 0-10"));
            agent->addObject(mgmtObject, objectId);
        }
    }
}

void Connection::requestIOProcessing(boost::function0<void> callback)
{
    ScopedLock<Mutex> l(ioCallbackLock);
    ioCallbacks.push(callback);
    if (isOpen()) out->activateOutput();
}

Connection::~Connection()
{
    if (mgmtObject != 0) {
        mgmtObject->debugStats("destroying");
        if (!link)
            agent->raiseEvent(_qmf::EventClientDisconnect(mgmtId, getUserId(), mgmtObject->get_remoteProperties()));
        QPID_LOG_CAT(debug, model, "Delete connection. user:" << getUserId()
            << " rhost:" << mgmtId );
        mgmtObject->resourceDestroy();
    }
    broker.getConnectionObservers().closed(*this);

    if (heartbeatTimer)
        heartbeatTimer->cancel();
    if (timeoutTimer)
        timeoutTimer->cancel();
    if (linkHeartbeatTimer) {
        linkHeartbeatTimer->cancel();
    }
}

void Connection::received(framing::AMQFrame& frame) {
    // Received frame on connection so delay timeout
    restartTimeout();
    bool wasOpen = isOpen();
    adapter.handle(frame);
    if (link) //i.e. we are acting as the client to another broker
        recordFromServer(frame);
    else
        recordFromClient(frame);
    if (!wasOpen && isOpen()) {
        doIoCallbacks(); // Do any callbacks registered before we opened.
        broker.getConnectionObservers().opened(*this);
    }
}

void Connection::sent(const framing::AMQFrame& frame)
{
    if (link) //i.e. we are acting as the client to another broker
        recordFromClient(frame);
    else
        recordFromServer(frame);
}

bool isMessage(const AMQMethodBody* method)
{
    return method && method->isA<qpid::framing::MessageTransferBody>();
}

void Connection::recordFromServer(const framing::AMQFrame& frame)
{
    if (mgmtObject != 0)
    {
        qmf::org::apache::qpid::broker::Connection::PerThreadStats *cStats = mgmtObject->getStatistics();
        cStats->framesToClient += 1;
        cStats->bytesToClient += frame.encodedSize();
        if (isMessage(frame.getMethod())) {
            cStats->msgsToClient += 1;
        }
        mgmtObject->statisticsUpdated();
    }
}

void Connection::recordFromClient(const framing::AMQFrame& frame)
{
    if (mgmtObject != 0)
    {
        qmf::org::apache::qpid::broker::Connection::PerThreadStats *cStats = mgmtObject->getStatistics();
        cStats->framesFromClient += 1;
        cStats->bytesFromClient += frame.encodedSize();
        if (isMessage(frame.getMethod())) {
            cStats->msgsFromClient += 1;
        }
        mgmtObject->statisticsUpdated();
    }
}

string Connection::getAuthMechanism()
{
    if (!link)
        return string("ANONYMOUS");

    return links.getAuthMechanism(mgmtId);
}

string Connection::getUsername ( )
{
    if (!link)
        return string("anonymous");

    return links.getUsername(mgmtId);
}

string Connection::getPassword ( )
{
    if (!link)
        return string("");

    return links.getPassword(mgmtId);
}

string Connection::getHost ( )
{
    if (!link)
        return string("");

    return links.getHost(mgmtId);
}

uint16_t Connection::getPort ( )
{
    if (!link)
        return 0;

    return links.getPort(mgmtId);
}

string Connection::getAuthCredentials()
{
    if (!link)
        return string();

    if (mgmtObject != 0)
    {
        if (links.getAuthMechanism(mgmtId) == "ANONYMOUS")
            mgmtObject->set_authIdentity("anonymous");
        else
            mgmtObject->set_authIdentity(links.getAuthIdentity(mgmtId));
    }

    return links.getAuthCredentials(mgmtId);
}

void Connection::notifyConnectionForced(const string& text)
{
    broker.getConnectionObservers().forced(*this, text);
}

void Connection::setUserId(const string& uid)
{
    userId = uid;
    size_t at = userId.find('@');
    userName = userId.substr(0, at);
    isDefaultRealm = (
        at!= std::string::npos &&
        getBroker().getRealm() == userId.substr(at+1,userId.size()));
   raiseConnectEvent();
}

void Connection::raiseConnectEvent() {
    if (mgmtObject != 0) {
        mgmtObject->set_authIdentity(userId);
        agent->raiseEvent(_qmf::EventClientConnect(mgmtId, userId, mgmtObject->get_remoteProperties()));
    }

    QPID_LOG_CAT(debug, model, "Create connection. user:" << userId
        << " rhost:" << mgmtId );
}

void Connection::close(connection::CloseCode code, const string& text)
{
    QPID_LOG_IF(error, code != connection::CLOSE_CODE_NORMAL, "Connection " << mgmtId << " closed by error: " << text << "(" << code << ")");
    if (heartbeatTimer)
        heartbeatTimer->cancel();
    if (timeoutTimer)
        timeoutTimer->cancel();
    if (linkHeartbeatTimer) {
        linkHeartbeatTimer->cancel();
    }
    adapter.close(code, text);
    //make sure we delete dangling pointers from outputTasks before deleting sessions
    outputTasks.removeAll();
    channels.clear();
    out->close();
}

void Connection::activateOutput()
{
    out->activateOutput();
}

void Connection::addOutputTask(OutputTask* t)
{
    outputTasks.addOutputTask(t);
}

void Connection::removeOutputTask(OutputTask* t)
{
    outputTasks.removeOutputTask(t);
}

void Connection::closed(){ // Physically closed, suspend open sessions.
    if (heartbeatTimer)
        heartbeatTimer->cancel();
    if (timeoutTimer)
        timeoutTimer->cancel();
    if (linkHeartbeatTimer) {
        linkHeartbeatTimer->cancel();
    }
    try {
        while (!channels.empty())
            ptr_map_ptr(channels.begin())->handleDetach();
    } catch(std::exception& e) {
        QPID_LOG(error, QPID_MSG("While closing connection: " << e.what()));
        assert(0);
    }
}

void Connection::doIoCallbacks() {
    if (!isOpen()) return; // Don't process IO callbacks until we are open.
    ScopedLock<Mutex> l(ioCallbackLock);
    while (!ioCallbacks.empty()) {
        boost::function0<void> cb = ioCallbacks.front();
        ioCallbacks.pop();
        ScopedUnlock<Mutex> ul(ioCallbackLock);
        cb(); // Lend the IO thread for management processing
    }
}

bool Connection::doOutput() {
    try {
        doIoCallbacks();
        if (mgmtClosing) {
            closed();
            close(connection::CLOSE_CODE_CONNECTION_FORCED, "Closed by Management Request");
        } else {
            //then do other output as needed:
            return outputTasks.doOutput();
	}
    }catch(const SessionOutputException& e){
        getChannel(e.channel).handleException(e);
        return true;
    }catch(ConnectionException& e){
        close(e.code, e.getMessage());
    }catch(std::exception& e){
        close(connection::CLOSE_CODE_CONNECTION_FORCED, e.what());
    }
    return false;
}

void Connection::sendHeartbeat() {
    requestIOProcessing(boost::bind(&ConnectionHandler::heartbeat, &adapter));
}

void Connection::closeChannel(uint16_t id) {
    ChannelMap::iterator i = channels.find(id);
    if (i != channels.end()) channels.erase(i);
}

SessionHandler& Connection::getChannel(ChannelId id) {
    ChannelMap::iterator i=channels.find(id);
    if (i == channels.end()) {
        i = channels.insert(id, new SessionHandler(*this, id)).first;
    }
    return *ptr_map_ptr(i);
}

ManagementObject::shared_ptr Connection::GetManagementObject(void) const
{
    return mgmtObject;
}

Manageable::status_t Connection::ManagementMethod(uint32_t methodId, Args&, string&)
{
    Manageable::status_t status = Manageable::STATUS_UNKNOWN_METHOD;

    QPID_LOG(debug, "Connection::ManagementMethod [id=" << methodId << "]");

    switch (methodId)
    {
    case _qmf::Connection::METHOD_CLOSE :
        mgmtClosing = true;
        if (mgmtObject != 0) mgmtObject->set_closing(1);
        out->activateOutput();
        status = Manageable::STATUS_OK;
        break;
    }

    return status;
}

void Connection::setSecureConnection(SecureConnection* s)
{
    adapter.setSecureConnection(s);
}

struct ConnectionHeartbeatTask : public sys::TimerTask {
    sys::Timer& timer;
    Connection& connection;
    ConnectionHeartbeatTask(uint16_t hb, sys::Timer& t, Connection& c) :
        TimerTask(Duration(hb*TIME_SEC), "ConnectionHeartbeat"),
        timer(t),
        connection(c)
    {}

    void fire() {
        // Setup next firing
        setupNextFire();
        timer.add(this);

        // Send Heartbeat
        connection.sendHeartbeat();
    }
};

class LinkHeartbeatTask : public qpid::sys::TimerTask {
    sys::Timer& timer;
    Connection& connection;
    bool heartbeatSeen;

    void fire() {
        if (!heartbeatSeen) {
            QPID_LOG(error, "Federation link connection " << connection.getMgmtId() << " missed 2 heartbeats - closing connection");
            connection.abort();
        } else {
            heartbeatSeen = false;
            // Setup next firing
            setupNextFire();
            timer.add(this);
        }
    }

public:
    LinkHeartbeatTask(sys::Timer& t, qpid::sys::Duration period, Connection& c) :
        TimerTask(period, "LinkHeartbeatTask"), timer(t), connection(c), heartbeatSeen(false) {}

    void heartbeatReceived() { heartbeatSeen = true; }
};


void Connection::abort()
{
    // Make sure that we don't try to send a heartbeat as we're
    // aborting the connection
    if (heartbeatTimer)
        heartbeatTimer->cancel();

    out->abort();
}

void Connection::setHeartbeatInterval(uint16_t heartbeat)
{
    setHeartbeat(heartbeat);
    if (heartbeat > 0) {
        if (!heartbeatTimer) {
            heartbeatTimer = new ConnectionHeartbeatTask(heartbeat, timer, *this);
            timer.add(heartbeatTimer);
        }
        if (!timeoutTimer) {
            timeoutTimer = new ConnectionTimeoutTask(heartbeat, timer, *this);
            timer.add(timeoutTimer);
        }
    }
    out->connectionEstablished();
}

void Connection::startLinkHeartbeatTimeoutTask() {
    if (!linkHeartbeatTimer && heartbeat > 0) {
        linkHeartbeatTimer = new LinkHeartbeatTask(timer, 2 * heartbeat * TIME_SEC, *this);
        timer.add(linkHeartbeatTimer);
    }
    out->connectionEstablished();
}

void Connection::restartTimeout()
{
    if (timeoutTimer)
        timeoutTimer->touch();

    if (linkHeartbeatTimer) {
        static_cast<LinkHeartbeatTask*>(linkHeartbeatTimer.get())->heartbeatReceived();
    }
}

bool Connection::isOpen() { return adapter.isOpen(); }

}}}
