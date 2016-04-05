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

#include "qpid/client/ConnectionImpl.h"

#include "qpid/client/LoadPlugins.h"
#include "qpid/client/Connector.h"
#include "qpid/client/ConnectionSettings.h"
#include "qpid/client/SessionImpl.h"

#include "qpid/log/Statement.h"
#include "qpid/Url.h"
#include "qpid/framing/enum.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/sys/Poller.h"
#include "qpid/sys/SystemInfo.h"
#include "qpid/Options.h"

#include <boost/bind.hpp>
#include <boost/format.hpp>
#include <boost/lexical_cast.hpp>
#include <boost/shared_ptr.hpp>

#include <limits>
#include <vector>

#include "config.h"

namespace qpid {
namespace client {

using namespace qpid::framing;
using namespace qpid::framing::connection;
using namespace qpid::sys;
using namespace qpid::framing::connection;//for connection error codes

namespace {
// Maybe should amalgamate the singletons into a single client singleton

// Get timer singleton
Timer& theTimer() {
    static Mutex timerInitLock;
    ScopedLock<Mutex> l(timerInitLock);

    static qpid::sys::Timer t;
    return t;
}

struct IOThreadOptions : public qpid::Options {
    int maxIOThreads;

    IOThreadOptions(int c) :
        Options("IO threading options"),
        maxIOThreads(c)
    {
        addOptions()
            ("max-iothreads", optValue(maxIOThreads, "N"), "Maximum number of io threads to use");
    }
};

// IO threads
class IOThread {
    int maxIOThreads;
    int ioThreads;
    int connections;
    Mutex threadLock;
    std::vector<Thread> t;
    Poller::shared_ptr poller_;

public:
    void add() {
        ScopedLock<Mutex> l(threadLock);
        ++connections;
        if (!poller_)
            poller_.reset(new Poller);
        if (ioThreads < connections && ioThreads < maxIOThreads) {
            QPID_LOG(debug, "Created IO thread: " << ioThreads);
            ++ioThreads;
            t.push_back( Thread(poller_.get()) );
        }
    }

    void sub() {
        ScopedLock<Mutex> l(threadLock);
        --connections;
    }

    Poller::shared_ptr poller() const {
        assert(poller_);
        return poller_;
    }

    // Here is where the maximum number of threads is set
    IOThread(int c) :
        ioThreads(0),
        connections(0)
    {
        CommonOptions common("", "", QPIDC_CONF_FILE);
        IOThreadOptions options(c);
        common.parse(0, 0, common.clientConfig, true);
        options.parse(0, 0, common.clientConfig, true);
        maxIOThreads = (options.maxIOThreads != -1) ?
            options.maxIOThreads : 1;
    }

    // We can't destroy threads one-by-one as the only
    // control we have is to shutdown the whole lot
    // and we can't do that before we're unloaded as we can't
    // restart the Poller after shutting it down
    ~IOThread() {
        if (SystemInfo::threadSafeShutdown()) {
            std::vector<Thread> threads;
            {
                ScopedLock<Mutex> l(threadLock);
                if (poller_)
                    poller_->shutdown();
                t.swap(threads);
            }
            for (std::vector<Thread>::iterator i = threads.begin(); i != threads.end(); ++i) {
                i->join();
            }
        }
    }
};

IOThread& theIO() {
    static IOThread io(SystemInfo::concurrency());
    return io;
}

class HeartbeatTask : public TimerTask {
    ConnectionImpl& timeout;

    void fire() {
        // If we ever get here then we have timed out
        QPID_LOG(debug, "Traffic timeout");
        timeout.timeout();
    }

public:
    HeartbeatTask(Duration p, ConnectionImpl& t) :
        TimerTask(p,"Heartbeat"),
        timeout(t)
    {}
};

}

void ConnectionImpl::init() {
    // Ensure that the plugin modules have been loaded
    // This will make sure that any plugin protocols are available
    theModuleLoader();

    // Ensure the IO threads exist:
    // This needs to be called in the Connection constructor
    // so that they will still exist at last connection destruction
    (void) theIO();
}

boost::shared_ptr<ConnectionImpl> ConnectionImpl::create(framing::ProtocolVersion version, const ConnectionSettings& settings)
{
    boost::shared_ptr<ConnectionImpl> instance(new ConnectionImpl(version, settings), boost::bind(&ConnectionImpl::release, _1));
    return instance;
}

ConnectionImpl::ConnectionImpl(framing::ProtocolVersion v, const ConnectionSettings& settings)
    : Bounds(settings.maxFrameSize * settings.bounds),
      handler(settings, v, *this),
      version(v),
      nextChannel(1),
      shutdownComplete(false),
      released(false)
{
    handler.in = boost::bind(&ConnectionImpl::incoming, this, _1);
    handler.out = boost::bind(&Connector::handle, boost::ref(connector), _1);
    handler.onClose = boost::bind(&ConnectionImpl::closed, this,
                                  CLOSE_CODE_NORMAL, std::string());
    //only set error handler once  open
    handler.onError = boost::bind(&ConnectionImpl::closed, this, _1, _2);
    handler.getSecuritySettings = boost::bind(&Connector::getSecuritySettings, boost::ref(connector));
}

const uint16_t ConnectionImpl::NEXT_CHANNEL = std::numeric_limits<uint16_t>::max();

ConnectionImpl::~ConnectionImpl() {
    if (heartbeatTask) heartbeatTask->cancel();
    theIO().sub();
}

void ConnectionImpl::addSession(const boost::shared_ptr<SessionImpl>& session, uint16_t channel)
{
    Mutex::ScopedLock l(lock);
    for (uint16_t i = 0; i < NEXT_CHANNEL; i++) { //will at most search through channels once
        uint16_t c = channel == NEXT_CHANNEL ? nextChannel++ : channel;
        boost::weak_ptr<SessionImpl>& s = sessions[c];
        boost::shared_ptr<SessionImpl> ss = s.lock();
        if (!ss) {
            //channel is free, we can assign it to this session
            session->setChannel(c);
            s = session;
            return;
        } else if (channel != NEXT_CHANNEL) {
            //channel is taken and was requested explicitly so don't look for another
            throw SessionBusyException(QPID_MSG("Channel " << ss->getChannel() << " attached to " << ss->getId()));
        } //else channel is busy, but we can keep looking for a free one
    }
    // If we get here, we didn't find any available channel.
    throw ResourceLimitExceededException("There are no channels available");
}

void ConnectionImpl::handle(framing::AMQFrame& frame)
{
    handler.outgoing(frame);
}

void ConnectionImpl::incoming(framing::AMQFrame& frame)
{
    boost::shared_ptr<SessionImpl> s;
    {
        Mutex::ScopedLock l(lock);
        s = sessions[frame.getChannel()].lock();
    }
    if (!s) {
        QPID_LOG(info, *this << " dropping frame received on invalid channel: " << frame);
    } else {
        s->in(frame);
    }
}

bool ConnectionImpl::isOpen() const 
{
    return handler.isOpen();
}

void ConnectionImpl::open()
{
    const std::string& protocol = handler.protocol;
    const std::string& host = handler.host;
    int port = handler.port;

    theIO().add();
    connector.reset(Connector::create(protocol, theIO().poller(), version, handler, this));
    connector->setInputHandler(&handler);
    connector->setShutdownHandler(this);
    try {
        std::string p = boost::lexical_cast<std::string>(port);
        connector->connect(host, p);

    } catch (const std::exception& e) {
        QPID_LOG(debug, "Failed to connect to " << protocol << ":" << host << ":" << port << " " << e.what());
        connector.reset();
        throw TransportFailure(e.what());
    }
    connector->init();

    // Enable heartbeat if requested
    uint16_t heartbeat = static_cast<ConnectionSettings&>(handler).heartbeat;
    if (heartbeat) {
        // Set connection timeout to be 2x heart beat interval and setup timer
        heartbeatTask = new HeartbeatTask(heartbeat * 2 * TIME_SEC, *this);
        handler.setRcvTimeoutTask(heartbeatTask);
        theTimer().add(heartbeatTask);
    }

    // If the connect fails then the connector is cleaned up either when we try to connect again
    // - in that case in connector.reset() above;
    // - or when we are deleted
    try {
        handler.waitForOpen();
        QPID_LOG(info, *this << " connected to " << protocol << ":" << host << ":" << port);
    } catch (const Exception&) {
        connector->checkVersion(version);
        throw;
    }

    // If the SASL layer has provided an "operational" userId for the connection,
    // put it in the negotiated settings.
    const std::string& userId(handler.getUserId());
    if (!userId.empty())
        handler.username = userId;

    //enable security layer if one has been negotiated:
    std::auto_ptr<SecurityLayer> securityLayer = handler.getSecurityLayer();
    if (securityLayer.get()) {
        QPID_LOG(debug, *this << " activating security layer");
        connector->activateSecurityLayer(securityLayer);
    } else {
        QPID_LOG(debug, *this << " no security layer in place");
    }
}

void ConnectionImpl::timeout()
{
    connector->abort();
}

void ConnectionImpl::close()
{
    if (heartbeatTask) 
        heartbeatTask->cancel();
    // close() must be idempotent and no-throw as it will often be called in destructors.
    if (handler.isOpen()) {
        try {
            handler.close();
            closed(CLOSE_CODE_NORMAL, "Closed by client");
        } catch (...) {}
    }
    assert(!handler.isOpen());
}


template <class F> void ConnectionImpl::closeInternal(const F& f) {
    if (heartbeatTask) {
        heartbeatTask->cancel();
    }
    {
        Mutex::ScopedUnlock u(lock);
        connector->close();
    }
    //notifying sessions of failure can result in those session being
    //deleted which in turn results in a call to erase(); this can
    //even happen on this thread, when 's' goes out of scope
    //below. Using a copy prevents the map being modified as we
    //iterate through.
    SessionMap copy;
    sessions.swap(copy);
    for (SessionMap::iterator i = copy.begin(); i != copy.end(); ++i) {
        boost::shared_ptr<SessionImpl> s = i->second.lock();
        if (s) f(s);
    }
}

void ConnectionImpl::closed(uint16_t code, const std::string& text) { 
    Mutex::ScopedLock l(lock);
    setException(new ConnectionException(ConnectionHandler::convert(code), text));
    closeInternal(boost::bind(&SessionImpl::connectionClosed, _1, code, text));
}

void ConnectionImpl::shutdown() {
    if (!handler.isClosed()) {
        failedConnection();
    }
    bool canDelete;
    {
        Mutex::ScopedLock l(lock);
        //association with IO thread is now ended
        shutdownComplete = true;
        //If we have already been released, we can now delete ourselves
        canDelete = released;
    }
    if (canDelete) delete this;
}

void ConnectionImpl::release() {
    bool isActive;
    {
        Mutex::ScopedLock l(lock);
        isActive = connector && !shutdownComplete;
    }
    //If we are still active - i.e. associated with an IO thread -
    //then we cannot delete ourselves yet, but must wait for the
    //shutdown callback which we can trigger by calling
    //connector.close()
    if (isActive) {
        connector->close();
        bool canDelete;
        {
            Mutex::ScopedLock l(lock);
            released = true;
            canDelete = shutdownComplete;
        }
        if (canDelete) delete this;
    } else { 
        delete this;
    }
}

static const std::string CONN_CLOSED("Connection closed");

void ConnectionImpl::failedConnection() {
    if ( failureCallback )
      failureCallback();

    if (handler.isClosed()) return;

    bool isClosing = handler.isClosing();
    bool isOpen = handler.isOpen();

    std::ostringstream msg;
    msg << *this << " closed";

    // FIXME aconway 2008-06-06: exception use, amqp0-10 does not seem to have
    // an appropriate close-code. connection-forced is not right.
    handler.fail(msg.str());//ensure connection is marked as failed before notifying sessions

    // At this point if the object isn't open and isn't closing it must have failed to open
    // so we can't do the rest of the cleanup
    if (!isClosing && !isOpen) return;

    Mutex::ScopedLock l(lock);
    closeInternal(boost::bind(&SessionImpl::connectionBroke, _1, msg.str()));
    setException(new TransportFailure(msg.str()));
}

void ConnectionImpl::erase(uint16_t ch) {
    Mutex::ScopedLock l(lock);
    sessions.erase(ch);
}

const ConnectionSettings& ConnectionImpl::getNegotiatedSettings()
{
    return handler;
}

std::vector<qpid::Url> ConnectionImpl::getInitialBrokers() {
    return handler.knownBrokersUrls;
}

boost::shared_ptr<SessionImpl>  ConnectionImpl::newSession(const std::string& name, uint32_t timeout, uint16_t channel) {
    boost::shared_ptr<SessionImpl> simpl(new SessionImpl(name, shared_from_this()));
    addSession(simpl, channel);
    simpl->open(timeout);
    return simpl;
}

std::ostream& operator<<(std::ostream& o, const ConnectionImpl& c) {
    if (c.connector)
        return o << "Connection " << c.connector->getIdentifier();
    else
        return o << "Connection <not connected>";
}

void shutdown() {
    theIO().poller()->shutdown();
}

}} // namespace qpid::client
