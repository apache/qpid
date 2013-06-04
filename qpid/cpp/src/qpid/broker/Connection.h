#ifndef QPID_BROKER_CONNECTION_H
#define QPID_BROKER_CONNECTION_H

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

#include <memory>
#include <sstream>
#include <vector>
#include <queue>

#include "qpid/broker/BrokerImportExport.h"
#include "qpid/broker/ConnectionHandler.h"
#include "qpid/broker/ConnectionState.h"
#include "qpid/sys/ConnectionInputHandler.h"
#include "qpid/sys/SecuritySettings.h"
#include "qpid/sys/Mutex.h"
#include "qpid/RefCounted.h"
#include "qpid/ptr_map.h"

#include "qmf/org/apache/qpid/broker/Connection.h"

#include <boost/ptr_container/ptr_map.hpp>
#include <boost/bind.hpp>

#include <algorithm>

namespace qpid {
namespace sys {
class Timer;
class TimerTask;
}
namespace broker {

class Broker;
class LinkRegistry;
class Queue;
class SecureConnection;
class SessionHandler;
struct ConnectionTimeoutTask;

class Connection : public sys::ConnectionInputHandler,
                   public ConnectionState,
                   public RefCounted
{
  public:
    /**
     * Listener that can be registered with a Connection to be informed of errors.
     */
    class ErrorListener
    {
      public:
        virtual ~ErrorListener() {}
        virtual void sessionError(uint16_t channel, const std::string&) = 0;
        virtual void connectionError(const std::string&) = 0;
    };

    Connection(sys::ConnectionOutputHandler* out,
               Broker& broker,
               const std::string& mgmtId,
               const qpid::sys::SecuritySettings&,
               bool isLink = false,
               uint64_t objectId = 0);

    ~Connection ();

    /** Get the SessionHandler for channel. Create if it does not already exist */
    SessionHandler& getChannel(framing::ChannelId channel);

    /** Close the connection. Waits for the client to respond with close-ok
     * before actually destroying the connection.
     */
    QPID_BROKER_EXTERN void close(
        framing::connection::CloseCode code, const std::string& text);

    /** Abort the connection. Close abruptly and immediately. */
    QPID_BROKER_EXTERN void abort();

    // ConnectionInputHandler methods
    void received(framing::AMQFrame& frame);
    void idleOut();
    void idleIn();
    bool doOutput();
    void closed();

    void closeChannel(framing::ChannelId channel);

    // Manageable entry points
    management::ManagementObject::shared_ptr GetManagementObject(void) const;
    management::Manageable::status_t
        ManagementMethod (uint32_t methodId, management::Args& args, std::string&);

    void requestIOProcessing (boost::function0<void>);
    void recordFromServer (const framing::AMQFrame& frame);
    void recordFromClient (const framing::AMQFrame& frame);

    // gets for configured federation links
    std::string getAuthMechanism();
    std::string getAuthCredentials();
    std::string getUsername();
    std::string getPassword();
    std::string getHost();
    uint16_t    getPort();

    void notifyConnectionForced(const std::string& text);
    void setUserId(const std::string& uid);

    // credentials for connected client
    const std::string& getUserId() const { return ConnectionState::getUserId(); }
    const std::string& getMgmtId() const { return mgmtId; }
    management::ManagementAgent* getAgent() const { return agent; }
    void setUserProxyAuth(bool b);
    /** Connection does not delete the listener. 0 resets. */
    void setErrorListener(ErrorListener* l) { errorListener=l; }
    ErrorListener* getErrorListener() { return errorListener; }

    void setHeartbeatInterval(uint16_t heartbeat);
    void sendHeartbeat();
    void restartTimeout();

    template <class F> void eachSessionHandler(F f) {
        for (ChannelMap::iterator i = channels.begin(); i != channels.end(); ++i)
            f(*ptr_map_ptr(i));
    }

    void setSecureConnection(SecureConnection* secured);

    const qpid::sys::SecuritySettings& getExternalSecuritySettings() const
    {
        return securitySettings;
    }

    /** @return true if the initial connection negotiation is complete. */
    bool isOpen();

    bool isLink() { return link; }
    void startLinkHeartbeatTimeoutTask();

    void setClientProperties(const framing::FieldTable& cp) { clientProperties = cp; }
    const framing::FieldTable& getClientProperties() const { return clientProperties; }

  private:
    typedef boost::ptr_map<framing::ChannelId, SessionHandler> ChannelMap;
    typedef std::vector<boost::shared_ptr<Queue> >::iterator queue_iterator;

    ChannelMap channels;
    qpid::sys::SecuritySettings securitySettings;
    ConnectionHandler adapter;
    const bool link;
    bool mgmtClosing;
    const std::string mgmtId;
    sys::Mutex ioCallbackLock;
    std::queue<boost::function0<void> > ioCallbacks;
    qmf::org::apache::qpid::broker::Connection::shared_ptr mgmtObject;
    LinkRegistry& links;
    management::ManagementAgent* agent;
    sys::Timer& timer;
    boost::intrusive_ptr<sys::TimerTask> heartbeatTimer, linkHeartbeatTimer;
    boost::intrusive_ptr<ConnectionTimeoutTask> timeoutTimer;
    ErrorListener* errorListener;
    uint64_t objectId;
    framing::FieldTable clientProperties;

    /**
     * Chained ConnectionOutputHandler that allows outgoing frames to be
     * tracked (for updating mgmt stats).
     */
    class OutboundFrameTracker : public sys::ConnectionOutputHandler
    {
      public:
        OutboundFrameTracker(Connection&);
        void close();
        void abort();
        void connectionEstablished();
        void activateOutput();
        void send(framing::AMQFrame&);
        void wrap(sys::ConnectionOutputHandlerPtr&);
      private:
        Connection& con;
        sys::ConnectionOutputHandler* next;
    };
    OutboundFrameTracker outboundTracker;

    void sent(const framing::AMQFrame& f);
    void doIoCallbacks();

  public:

    qmf::org::apache::qpid::broker::Connection::shared_ptr getMgmtObject() { return mgmtObject; }
};

}}

#endif  /*!QPID_BROKER_CONNECTION_H*/
