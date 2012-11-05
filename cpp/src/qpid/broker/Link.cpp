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

#include "qpid/broker/Link.h"
#include "qpid/broker/LinkRegistry.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/Connection.h"
#include "qpid/sys/Timer.h"
#include "qmf/org/apache/qpid/broker/EventBrokerLinkUp.h"
#include "qmf/org/apache/qpid/broker/EventBrokerLinkDown.h"
#include "boost/bind.hpp"
#include "qpid/log/Statement.h"
#include "qpid/framing/enum.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/framing/amqp_types.h"
#include "qpid/broker/AclModule.h"
#include "qpid/broker/Exchange.h"
#include "qpid/UrlArray.h"

namespace qpid {
namespace broker {

using framing::Buffer;
using framing::FieldTable;
using framing::UnauthorizedAccessException;
using framing::connection::CLOSE_CODE_CONNECTION_FORCED;
using management::ManagementAgent;
using management::ManagementObject;
using management::Manageable;
using management::Args;
using sys::Mutex;
using std::stringstream;
using std::string;
namespace _qmf = ::qmf::org::apache::qpid::broker;


namespace {
    const std::string FAILOVER_EXCHANGE("amq.failover");
    const std::string FAILOVER_HEADER_KEY("amq.failover");
}


struct LinkTimerTask : public sys::TimerTask {
    LinkTimerTask(Link& l, sys::Timer& t)
        : TimerTask(int64_t(l.getBroker()->getOptions().linkMaintenanceInterval*
                            sys::TIME_SEC),
                    "Link retry timer"),
          link(l), timer(t) {}

    void fire() {
        link.maintenanceVisit();
        setupNextFire();
        timer.add(this);
    }

    Link& link;
    sys::Timer& timer;
};



/** LinkExchange is used by the link to subscribe to the remote broker's amq.failover exchange.
 */
class LinkExchange : public broker::Exchange
{
public:
    LinkExchange(const std::string& name) : Exchange(name), link(0) {}
    ~LinkExchange() {};
    std::string getType() const { return Link::exchangeTypeName; }

    // Exchange methods - set up to prevent binding/unbinding etc from clients!
    bool bind(boost::shared_ptr<broker::Queue>, const std::string&, const framing::FieldTable*) { return false; }
    bool unbind(boost::shared_ptr<broker::Queue>, const std::string&, const framing::FieldTable*) { return false; }
    bool isBound(boost::shared_ptr<broker::Queue>, const std::string* const, const framing::FieldTable* const) {return false;}

    // Process messages sent from the remote's amq.failover exchange by extracting the failover URLs
    // and saving them should the Link need to reconnect.
    void route(broker::Deliverable& /*msg*/)
    {
        if (!link) return;
        const framing::FieldTable* headers = 0;//TODO: msg.getMessage().getApplicationHeaders();
        framing::Array addresses;
        if (headers && headers->getArray(FAILOVER_HEADER_KEY, addresses)) {
            // convert the Array of addresses to a single Url container for used with setUrl():
            std::vector<Url> urlVec;
            Url urls;
            urlVec = urlArrayToVector(addresses);
            for(size_t i = 0; i < urlVec.size(); ++i)
                urls.insert(urls.end(), urlVec[i].begin(), urlVec[i].end());
            QPID_LOG(debug, "Remote broker has provided these failover addresses= " << urls);
            link->setUrl(urls);
        }
    }

    void setLink(Link *_link)
    {
        assert(!link);
        link = _link;
    }

private:
    Link *link;
};


boost::shared_ptr<Exchange> Link::linkExchangeFactory( const std::string& _name )
{
    return Exchange::shared_ptr(new LinkExchange(_name));
}

Link::Link(const string&  _name,
           LinkRegistry*  _links,
           const string&        _host,
           uint16_t       _port,
           const string&        _transport,
           DestroyedListener    l,
           bool           _durable,
           const string&        _authMechanism,
           const string&        _username,
           const string&        _password,
           Broker*        _broker,
           Manageable*    parent,
           bool failover_)
    : name(_name), links(_links),
      configuredTransport(_transport), configuredHost(_host), configuredPort(_port),
      host(_host), port(_port), transport(_transport),
      durable(_durable),
      authMechanism(_authMechanism), username(_username), password(_password),
      persistenceId(0), broker(_broker), state(0),
      visitCount(0),
      currentInterval(1),
      closing(false),
      reconnectNext(0),         // Index of next address for reconnecting in url.
      nextFreeChannel(1),
      freeChannels(1, framing::CHANNEL_MAX),
      connection(0),
      agent(0),
      listener(l),
      timerTask(new LinkTimerTask(*this, broker->getTimer())),
      failover(failover_),
      failoverChannel(0)
{
    if (parent != 0 && broker != 0)
    {
        agent = broker->getManagementAgent();
        if (agent != 0)
        {
            mgmtObject = _qmf::Link::shared_ptr(new _qmf::Link(agent, this, parent, name, durable));
            mgmtObject->set_host(host);
            mgmtObject->set_port(port);
            mgmtObject->set_transport(transport);
            agent->addObject(mgmtObject, 0, durable);
        }
    }
    if (links->isPassive()) {
        setStateLH(STATE_PASSIVE);
    } else {
        setStateLH(STATE_WAITING);
        startConnectionLH();
    }
    broker->getTimer().add(timerTask);

    if (failover) {
        stringstream exchangeName;
        exchangeName << "qpid.link." << name;
        std::pair<Exchange::shared_ptr, bool> rc =
            broker->getExchanges().declare(exchangeName.str(), exchangeTypeName);
        failoverExchange = boost::static_pointer_cast<LinkExchange>(rc.first);
        assert(failoverExchange);
        failoverExchange->setLink(this);
    }
}

Link::~Link ()
{
    if (state == STATE_OPERATIONAL && connection != 0) {
        closeConnection("closed by management");
    }

    if (mgmtObject != 0)
        mgmtObject->resourceDestroy ();

    if (failover)
        broker->getExchanges().destroy(failoverExchange->getName());
}

void Link::setStateLH (int newState)
{
    if (newState == state)
        return;

    state = newState;

    if (hideManagement())
        return;

    switch (state)
    {
    case STATE_WAITING     : mgmtObject->set_state("Waiting");     break;
    case STATE_CONNECTING  : mgmtObject->set_state("Connecting");  break;
    case STATE_OPERATIONAL : mgmtObject->set_state("Operational"); break;
    case STATE_FAILED      : mgmtObject->set_state("Failed");      break;
    case STATE_CLOSED      : mgmtObject->set_state("Closed");      break;
    case STATE_PASSIVE     : mgmtObject->set_state("Passive");      break;
    }
}

void Link::startConnectionLH ()
{
    assert(state == STATE_WAITING);
    try {
        // Set the state before calling connect.  It is possible that connect
        // will fail synchronously and call Link::closed before returning.
        setStateLH(STATE_CONNECTING);
        broker->connect (host, boost::lexical_cast<std::string>(port), transport,
                         boost::bind (&Link::closed, this, _1, _2));
        QPID_LOG (info, "Inter-broker link connecting to " << host << ":" << port);
    } catch(const std::exception& e) {
        QPID_LOG(error, "Link connection to " << host << ":" << port << " failed: "
                 << e.what());
        setStateLH(STATE_WAITING);
        if (!hideManagement())
            mgmtObject->set_lastError (e.what());
    }
}

void Link::established(Connection* c)
{
    if (state == STATE_PASSIVE) return;
    stringstream addr;
    addr << host << ":" << port;
    QPID_LOG (info, "Inter-broker link established to " << addr.str());

    if (!hideManagement() && agent)
        agent->raiseEvent(_qmf::EventBrokerLinkUp(addr.str()));
    bool isClosing = false;
    {
        Mutex::ScopedLock mutex(lock);
        setStateLH(STATE_OPERATIONAL);
        currentInterval = 1;
        visitCount      = 0;
        connection = c;
        isClosing = closing;
    }
    if (isClosing)
        destroy();
    else // Process any IO tasks bridges added before established.
        c->requestIOProcessing (boost::bind(&Link::ioThreadProcessing, this));
}


void Link::setUrl(const Url& u) {
    QPID_LOG(info, "Setting remote broker failover addresses for link '" << getName() << "' to these urls: " << u);
    Mutex::ScopedLock mutex(lock);
    url = u;
    reconnectNext = 0;
}


namespace {
class DetachedCallback : public SessionHandler::ErrorListener {
  public:
    DetachedCallback(const Link& link) : name(link.getName()) {}
    void connectionException(framing::connection::CloseCode, const std::string&) {}
    void channelException(framing::session::DetachCode, const std::string&) {}
    void executionException(framing::execution::ErrorCode, const std::string&) {}
    void detach() {}
  private:
    const std::string name;
};
}

void Link::opened() {
    Mutex::ScopedLock mutex(lock);
    if (!connection) return;

    if (!hideManagement() && connection->GetManagementObject()) {
        mgmtObject->set_connectionRef(connection->GetManagementObject()->getObjectId());
    }

    // Get default URL from known-hosts if not already set
    if (url.empty()) {
        const std::vector<Url>& known = connection->getKnownHosts();
        // Flatten vector of URLs into a single URL listing all addresses.
        url.clear();
        for(size_t i = 0; i < known.size(); ++i)
            url.insert(url.end(), known[i].begin(), known[i].end());
        reconnectNext = 0;
        QPID_LOG(debug, "Known hosts for peer of inter-broker link: " << url);
    }

    if (failover) {
        //
        // attempt to subscribe to failover exchange for updates from remote
        //

        const std::string queueName = "qpid.link." + framing::Uuid(true).str();
        failoverChannel = nextChannel();

        SessionHandler& sessionHandler = connection->getChannel(failoverChannel);
        sessionHandler.setErrorListener(
            boost::shared_ptr<SessionHandler::ErrorListener>(new DetachedCallback(*this)));
        failoverSession = queueName;
        sessionHandler.attachAs(failoverSession);

        framing::AMQP_ServerProxy remoteBroker(sessionHandler.out);

        remoteBroker.getQueue().declare(queueName,
                                        "",         // alt-exchange
                                        false,      // passive
                                        false,      // durable
                                        true,       // exclusive
                                        true,       // auto-delete
                                        FieldTable());
        remoteBroker.getExchange().bind(queueName,
                                        FAILOVER_EXCHANGE,
                                        "",     // no key
                                        FieldTable());
        remoteBroker.getMessage().subscribe(queueName,
                                            failoverExchange->getName(),
                                            1,           // implied-accept mode
                                            0,           // pre-acquire mode
                                            false,       // exclusive
                                            "",          // resume-id
                                            0,           // resume-ttl
                                            FieldTable());
        remoteBroker.getMessage().flow(failoverExchange->getName(), 0, 0xFFFFFFFF);
        remoteBroker.getMessage().flow(failoverExchange->getName(), 1, 0xFFFFFFFF);
    }
}

void Link::closed(int, std::string text)
{
    Mutex::ScopedLock mutex(lock);
    QPID_LOG (info, "Inter-broker link disconnected from " << host << ":" << port << " " << text);

    connection = 0;

    if (!hideManagement()) {
        mgmtObject->set_connectionRef(qpid::management::ObjectId());
        if (state == STATE_OPERATIONAL && agent) {
            stringstream addr;
            addr << host << ":" << port;
            agent->raiseEvent(_qmf::EventBrokerLinkDown(addr.str()));
        }
    }

    for (Bridges::iterator i = active.begin(); i != active.end(); i++) {
        (*i)->closed();
        created.push_back(*i);
    }
    active.clear();

    if (state != STATE_FAILED && state != STATE_PASSIVE)
    {
        setStateLH(STATE_WAITING);
        if (!hideManagement())
            mgmtObject->set_lastError (text);
    }
}

// Called in connection IO thread, cleans up the connection before destroying Link
void Link::destroy ()
{
    Bridges toDelete;

    timerTask->cancel();    // call prior to locking so maintenance visit can finish
    {
        Mutex::ScopedLock mutex(lock);

        QPID_LOG (info, "Inter-broker link to " << configuredHost << ":" << configuredPort << " removed by management");
        closeConnection("closed by management");
        setStateLH(STATE_CLOSED);

        // Move the bridges to be deleted into a local vector so there is no
        // corruption of the iterator caused by bridge deletion.
        for (Bridges::iterator i = active.begin(); i != active.end(); i++) {
            (*i)->closed();
            toDelete.push_back(*i);
        }
        active.clear();

        for (Bridges::iterator i = created.begin(); i != created.end(); i++)
            toDelete.push_back(*i);
        created.clear();
    }

    // Now delete all bridges on this link (don't hold the lock for this).
    for (Bridges::iterator i = toDelete.begin(); i != toDelete.end(); i++)
        (*i)->close();
    toDelete.clear();
    listener(this); // notify LinkRegistry that this Link has been destroyed
}

void Link::add(Bridge::shared_ptr bridge)
{
    Mutex::ScopedLock mutex(lock);
    created.push_back (bridge);
    if (connection)
        connection->requestIOProcessing (boost::bind(&Link::ioThreadProcessing, this));

}

void Link::cancel(Bridge::shared_ptr bridge)
{
    bool needIOProcessing = false;
    {
        Mutex::ScopedLock mutex(lock);

        for (Bridges::iterator i = created.begin(); i != created.end(); i++) {
            if ((*i).get() == bridge.get()) {
                created.erase(i);
                break;
            }
        }
        for (Bridges::iterator i = active.begin(); i != active.end(); i++) {
            if ((*i).get() == bridge.get()) {
                cancellations.push_back(bridge);
                bridge->closed();
                active.erase(i);
                break;
            }
        }
        needIOProcessing = !cancellations.empty();
    }
    if (needIOProcessing && connection)
        connection->requestIOProcessing (boost::bind(&Link::ioThreadProcessing, this));
}

void Link::ioThreadProcessing()
{
    Mutex::ScopedLock mutex(lock);

    if (state != STATE_OPERATIONAL || closing)
        return;

    // check for bridge session errors and recover
    if (!active.empty()) {
        Bridges::iterator removed = std::remove_if(
            active.begin(), active.end(), boost::bind(&Bridge::isDetached, _1));
        for (Bridges::iterator i = removed; i != active.end(); ++i) {
            Bridge::shared_ptr  bridge = *i;
            bridge->closed();
            bridge->cancel(*connection);
            created.push_back(bridge);
        }
        active.erase(removed, active.end());
    }

    //process any pending creates and/or cancellations (do
    //cancellations first in case any of the creates represent
    //recreation of cancelled subscriptions
    if (!cancellations.empty()) {
        for (Bridges::iterator i = cancellations.begin(); i != cancellations.end(); ++i) {
            (*i)->cancel(*connection);
        }
        cancellations.clear();
    }
    if (!created.empty()) {
        for (Bridges::iterator i = created.begin(); i != created.end(); ++i) {
            active.push_back(*i);
            (*i)->create(*connection);
        }
        created.clear();
    }
}

void Link::maintenanceVisit ()
{
    Mutex::ScopedLock mutex(lock);
    if (closing) return;
    if (state == STATE_WAITING)
    {
        visitCount++;
        if (visitCount >= currentInterval)
        {
            visitCount = 0;
            //switch host and port to next in url list if possible
            if (!tryFailoverLH()) {
                currentInterval *= 2;
                if (currentInterval > MAX_INTERVAL)
                    currentInterval = MAX_INTERVAL;
                startConnectionLH();
            }
        }
    }
    else if (state == STATE_OPERATIONAL &&
             (!active.empty() || !created.empty() || !cancellations.empty()) &&
             connection && connection->isOpen())
        connection->requestIOProcessing (boost::bind(&Link::ioThreadProcessing, this));
}

void Link::reconnectLH(const Address& a)
{
    host = a.host;
    port = a.port;
    transport = a.protocol;

    if (!hideManagement()) {
        stringstream errorString;
        errorString << "Failing over to " << a;
        mgmtObject->set_lastError(errorString.str());
        mgmtObject->set_host(host);
        mgmtObject->set_port(port);
        mgmtObject->set_transport(transport);
    }
    startConnectionLH();
}

bool Link::tryFailoverLH() {
    assert(state == STATE_WAITING);
    if (reconnectNext >= url.size()) reconnectNext = 0;
    if (url.empty()) return false;
    Address next = url[reconnectNext++];
    if (next.host != host || next.port != port || next.protocol != transport) {
        QPID_LOG(notice, "Inter-broker link '" << name << "' failing over to " << next);
        reconnectLH(next);
        return true;
    }
    return false;
}

// Management updates for a link are inconsistent in a cluster, so they are
// suppressed.
bool Link::hideManagement() const {
    return !mgmtObject || ( broker && broker->isInCluster());
}

// Allocate channel from link free pool
framing::ChannelId Link::nextChannel()
{
    Mutex::ScopedLock mutex(lock);
    if (!freeChannels.empty()) {
        // A free channel exists.
        for (framing::ChannelId i = 1; i <= framing::CHANNEL_MAX; i++)
        {
            // extract proposed free channel
            framing::ChannelId c = nextFreeChannel;
            // calculate next free channel
            if (framing::CHANNEL_MAX == nextFreeChannel)
                nextFreeChannel = 1;
            else
                nextFreeChannel += 1;
            // if proposed channel is free, use it
            if (freeChannels.contains(c))
            {
                freeChannels -= c;
                QPID_LOG(debug, "Link " << name << " allocates channel: " << c);
                return c;
            }
        }
        assert (false);
    }

    throw Exception(Msg() << "Link " << name << " channel pool is empty");
}

// Return channel to link free pool
void Link::returnChannel(framing::ChannelId c)
{
    Mutex::ScopedLock mutex(lock);
    QPID_LOG(debug, "Link " << name << " frees channel: " << c);
    freeChannels += c;
}

void Link::notifyConnectionForced(const string text)
{
    Mutex::ScopedLock mutex(lock);
    setStateLH(STATE_FAILED);
    if (!hideManagement())
        mgmtObject->set_lastError(text);
}

void Link::setPersistenceId(uint64_t id) const
{
    persistenceId = id;
}

const string& Link::getName() const
{
    return name;
}

const std::string Link::ENCODED_IDENTIFIER("link.v2");
const std::string Link::ENCODED_IDENTIFIER_V1("link");

bool Link::isEncodedLink(const std::string& key)
{
    return key == ENCODED_IDENTIFIER || key == ENCODED_IDENTIFIER_V1;
}

Link::shared_ptr Link::decode(LinkRegistry& links, Buffer& buffer)
{
    string kind;
    buffer.getShortString(kind);

    string   host;
    uint16_t port;
    string   transport;
    string   authMechanism;
    string   username;
    string   password;
    string   name;

    if (kind == ENCODED_IDENTIFIER) {
        // newer version provides a link name.
        buffer.getShortString(name);
    }
    buffer.getShortString(host);
    port = buffer.getShort();
    buffer.getShortString(transport);
    bool durable(buffer.getOctet());
    buffer.getShortString(authMechanism);
    buffer.getShortString(username);
    buffer.getShortString(password);

    if (kind == ENCODED_IDENTIFIER_V1) {
        /** previous versions identified the Link by host:port, there was no name
         * assigned.  So create a name for the new Link.
         */
        name = createName(transport, host, port);
    }

    return links.declare(name, host, port, transport, durable, authMechanism,
                         username, password).first;
}

void Link::encode(Buffer& buffer) const
{
    buffer.putShortString(ENCODED_IDENTIFIER);
    buffer.putShortString(name);
    buffer.putShortString(configuredHost);
    buffer.putShort(configuredPort);
    buffer.putShortString(configuredTransport);
    buffer.putOctet(durable ? 1 : 0);
    buffer.putShortString(authMechanism);
    buffer.putShortString(username);
    buffer.putShortString(password);
}

uint32_t Link::encodedSize() const
{
    return ENCODED_IDENTIFIER.size() + 1 // +1 byte length
        + name.size() + 1
        + configuredHost.size() + 1 // short-string (host)
        + 2                // port
        + configuredTransport.size() + 1 // short-string(transport)
        + 1                // durable
        + authMechanism.size() + 1
        + username.size() + 1
        + password.size() + 1;
}

ManagementObject::shared_ptr Link::GetManagementObject (void) const
{
    return mgmtObject;
}

void Link::close() {
    QPID_LOG(debug, "Link::close(), link=" << name );
    Mutex::ScopedLock mutex(lock);
    if (!closing) {
        closing = true;
        if (state != STATE_CONNECTING && connection) {
            //connection can only be closed on the connections own IO processing thread
            connection->requestIOProcessing(boost::bind(&Link::destroy, this));
        }
    }
}


Manageable::status_t Link::ManagementMethod (uint32_t op, Args& args, string& text)
{
    switch (op)
    {
    case _qmf::Link::METHOD_CLOSE :
        close();
        return Manageable::STATUS_OK;

    case _qmf::Link::METHOD_BRIDGE :
        /* TBD: deprecate this interface in favor of the Broker::create() method.  The
         * Broker::create() method allows the user to assign a name to the bridge.
         */
        QPID_LOG(info, "The Link::bridge() method will be removed in a future release of QPID."
                 " Please use the Broker::create() method with type='bridge' instead.");
        _qmf::ArgsLinkBridge& iargs = (_qmf::ArgsLinkBridge&) args;
        QPID_LOG(debug, "Link::bridge() request received; src=" << iargs.i_src <<
                 "; dest=" << iargs.i_dest << "; key=" << iargs.i_key);

        // Does a bridge already exist that has the src/dest/key?  If so, re-use the
        // existing bridge - this behavior is backward compatible with previous releases.
        Bridge::shared_ptr bridge = links->getBridge(*this, iargs.i_src, iargs.i_dest, iargs.i_key);
        if (!bridge) {
            // need to create a new bridge on this link.
            std::pair<Bridge::shared_ptr, bool> rc =
              links->declare( Bridge::createName(name, iargs.i_src, iargs.i_dest, iargs.i_key),
                              *this, iargs.i_durable,
                              iargs.i_src, iargs.i_dest, iargs.i_key, iargs.i_srcIsQueue,
                              iargs.i_srcIsLocal, iargs.i_tag, iargs.i_excludes,
                              iargs.i_dynamic, iargs.i_sync);
            if (!rc.first) {
                text = "invalid parameters";
                return Manageable::STATUS_PARAMETER_INVALID;
            }
        }
        return Manageable::STATUS_OK;
    }

    return Manageable::STATUS_UNKNOWN_METHOD;
}

void Link::setPassive(bool passive)
{
    Mutex::ScopedLock mutex(lock);
    if (passive) {
        setStateLH(STATE_PASSIVE);
    } else {
        if (state == STATE_PASSIVE) {
            setStateLH(STATE_WAITING);
        } else {
            QPID_LOG(warning, "Ignoring attempt to activate non-passive link "
                     << host << ":" << port);
        }
    }
}


/** utility to clean up connection resources correctly */
void Link::closeConnection( const std::string& reason)
{
    if (connection != 0) {
        // cancel our subscription to the failover exchange
        if (failover) {
            SessionHandler& sessionHandler = connection->getChannel(failoverChannel);
            if (sessionHandler.getSession()) {
                framing::AMQP_ServerProxy remoteBroker(sessionHandler.out);
                remoteBroker.getMessage().cancel(failoverExchange->getName());
                remoteBroker.getSession().detach(failoverSession);
            }
        }
        connection->close(CLOSE_CODE_CONNECTION_FORCED, reason);
        connection = 0;
    }
}

/** returns the current remote's address, and connection state */
bool Link::getRemoteAddress(qpid::Address& addr) const
{
    addr.protocol = transport;
    addr.host = host;
    addr.port = port;

    return state == STATE_OPERATIONAL;
}


// FieldTable keys for internal state data
namespace {
    const std::string FAILOVER_ADDRESSES("failover-addresses");
    const std::string FAILOVER_INDEX("failover-index");
}

void Link::getState(framing::FieldTable& state) const
{
    state.clear();
    Mutex::ScopedLock mutex(lock);
    if (!url.empty()) {
        state.setString(FAILOVER_ADDRESSES, url.str());
        state.setInt(FAILOVER_INDEX, reconnectNext);
    }
}

void Link::setState(const framing::FieldTable& state)
{
    Mutex::ScopedLock mutex(lock);
    if (state.isSet(FAILOVER_ADDRESSES)) {
        Url failovers(state.getAsString(FAILOVER_ADDRESSES));
        setUrl(failovers);
    }
    if (state.isSet(FAILOVER_INDEX)) {
        reconnectNext = state.getAsInt(FAILOVER_INDEX);
    }
}

std::string Link::createName(const std::string& transport,
                             const std::string& host,
                             uint16_t  port)
{
    stringstream linkName;
    linkName << QPID_NAME_PREFIX << transport << std::string(":")
             << host << std::string(":") << port;
    return linkName.str();
}


bool Link::pendingConnection(const std::string& _host, uint16_t _port) const
{
    Mutex::ScopedLock mutex(lock);
    return (isConnecting() && _port == port && _host == host);
}


const std::string Link::exchangeTypeName("qpid.LinkExchange");

}} // namespace qpid::broker
