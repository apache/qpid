#ifndef _broker_Link_h
#define _broker_Link_h

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

#include <boost/shared_ptr.hpp>
#include <boost/enable_shared_from_this.hpp>
#include "qpid/Url.h"
#include "qpid/broker/BrokerImportExport.h"
#include "qpid/broker/PersistableConfig.h"
#include "qpid/broker/Bridge.h"
#include "qpid/broker/BrokerImportExport.h"
#include "qpid/sys/Mutex.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/management/Manageable.h"
#include "qpid/management/ManagementAgent.h"
#include "qmf/org/apache/qpid/broker/Link.h"
#include <boost/ptr_container/ptr_vector.hpp>

namespace qpid {

namespace sys {
class TimerTask;
}

namespace broker {

class LinkRegistry;
class Broker;
class LinkExchange;
namespace amqp_0_10 {
class Connection;
}

class Link : public PersistableConfig, public management::Manageable,
             public boost::enable_shared_from_this<Link>
{
  private:
    mutable sys::Mutex  lock;
    const std::string   name;
    LinkRegistry*       links;

    // these remain constant across failover - used to identify this link
    const std::string   configuredTransport;
    const std::string   configuredHost;
    const uint16_t      configuredPort;
    // these reflect the current address of remote - will change during failover
    std::string         host;
    uint16_t            port;
    std::string         transport;

    bool durable;

    std::string        authMechanism;
    std::string        username;
    std::string        password;
    mutable uint64_t    persistenceId;
    qmf::org::apache::qpid::broker::Link::shared_ptr mgmtObject;
    Broker* broker;
    int     state;
    uint32_t visitCount;
    uint32_t currentInterval;
    Url      url;       // URL can contain many addresses.
    size_t   reconnectNext; // Index for next re-connect attempt

    typedef std::vector<Bridge::shared_ptr> Bridges;
    Bridges created;   // Bridges pending creation
    Bridges active;    // Bridges active
    Bridges cancellations;    // Bridges pending cancellation
    framing::ChannelId nextFreeChannel;
    RangeSet<framing::ChannelId> freeChannels;
    amqp_0_10::Connection* connection;
    management::ManagementAgent* agent;
    boost::function<void(Link*)> listener;
    boost::intrusive_ptr<sys::TimerTask> timerTask;
    boost::shared_ptr<broker::LinkExchange> failoverExchange;  // subscribed to remote's amq.failover exchange
    bool failover; // Do we subscribe to a failover exchange?
    uint failoverChannel;
    std::string failoverSession;

    static const int STATE_WAITING     = 1;
    static const int STATE_CONNECTING  = 2;
    static const int STATE_OPERATIONAL = 3;
    static const int STATE_FAILED      = 4;
    static const int STATE_CLOSED      = 5;
    static const int STATE_CLOSING     = 6;  // Waiting for outstanding connect to complete first

    static const uint32_t MAX_INTERVAL = 32;

    void setStateLH (int newState);
    void startConnectionLH();        // Start the IO Connection
    void destroy();                  // Cleanup connection before link goes away
    void ioThreadProcessing();       // Called on connection's IO thread by request
    bool tryFailoverLH();            // Called during maintenance visit
    void reconnectLH(const Address&); //called by LinkRegistry

    // connection management (called by LinkRegistry)
    void established(amqp_0_10::Connection*); // Called when connection is created
    void opened();      // Called when connection is open (after create)
    void closed(int, std::string);   // Called when connection goes away
    void notifyConnectionForced(const std::string text);
    void closeConnection(const std::string& reason);

    friend class LinkRegistry; // to call established, opened, closed

  public:
    typedef boost::shared_ptr<Link> shared_ptr;
    typedef boost::function<void(Link*)> DestroyedListener;

    Link(const std::string&       name,
         LinkRegistry* links,
         const std::string&       host,
         uint16_t      port,
         const std::string&       transport,
         DestroyedListener        l,
         bool          durable,
         const std::string&       authMechanism,
         const std::string&       username,
         const std::string&       password,
         Broker*       broker,
         management::Manageable* parent = 0,
         bool failover=true);
    virtual ~Link();

    /** these return the *configured* transport/host/port, which does not change over the
        lifetime of the Link */
    std::string getHost() const { return configuredHost; }
    uint16_t    getPort() const { return configuredPort; }
    std::string getTransport() const { return configuredTransport; }

    /** returns the current address of the remote, which may be different from the
        configured transport/host/port due to failover. Returns true if connection is
        active */
    QPID_BROKER_EXTERN bool getRemoteAddress(qpid::Address& addr) const;

    bool isDurable() { return durable; }
    void maintenanceVisit ();
    QPID_BROKER_EXTERN framing::ChannelId nextChannel();        // allocate channel from link free pool
    QPID_BROKER_EXTERN void returnChannel(framing::ChannelId);  // return channel to link free pool
    void add(Bridge::shared_ptr);
    void cancel(Bridge::shared_ptr);

    QPID_BROKER_EXTERN void setUrl(const Url&); // Set URL for reconnection.

    // Close the link.
    QPID_BROKER_EXTERN void close();

    std::string getAuthMechanism() { return authMechanism; }
    std::string getUsername()      { return username; }
    std::string getPassword()      { return password; }
    Broker* getBroker()       { return broker; }

    bool isConnecting() const { return state == STATE_CONNECTING; }

    // PersistableConfig:
    void     setPersistenceId(uint64_t id) const;
    uint64_t getPersistenceId() const { return persistenceId; }
    uint32_t encodedSize() const;
    void     encode(framing::Buffer& buffer) const;
    const std::string& getName() const;

    static const std::string ENCODED_IDENTIFIER;
    static const std::string ENCODED_IDENTIFIER_V1;
    static Link::shared_ptr decode(LinkRegistry& links, framing::Buffer& buffer);
    static bool isEncodedLink(const std::string& key);

    // Manageable entry points
    management::ManagementObject::shared_ptr GetManagementObject(void) const;
    management::Manageable::status_t ManagementMethod(uint32_t, management::Args&, std::string&);

    // manage the exchange owned by this link
    static const std::string exchangeTypeName;
    static boost::shared_ptr<Exchange> linkExchangeFactory(const std::string& name);

    /** create a name for a link (if none supplied by user config) */
    static std::string createName(const std::string& transport,
                                  const std::string& host,
                                  uint16_t  port);

    /** The current connction for this link. Note returns 0 if the link is not
     * presently connected.
     */
    amqp_0_10::Connection* getConnection() { return connection; }
};
}
}


#endif  /*!_broker_Link.cpp_h*/
