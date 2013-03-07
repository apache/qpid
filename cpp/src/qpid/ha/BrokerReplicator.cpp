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
#include "BrokerReplicator.h"
#include "HaBroker.h"
#include "QueueReplicator.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/Connection.h"
#include "qpid/broker/ConnectionObserver.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/QueueSettings.h"
#include "qpid/broker/Link.h"
#include "qpid/broker/amqp_0_10/MessageTransfer.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/framing/FieldValue.h"
#include "qpid/log/Statement.h"
#include "qpid/amqp_0_10/Codecs.h"
#include "qpid/broker/SessionHandler.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/framing/MessageTransferBody.h"
#include "qpid/framing/reply_exceptions.h"
#include "qmf/org/apache/qpid/broker/EventBind.h"
#include "qmf/org/apache/qpid/broker/EventUnbind.h"
#include "qmf/org/apache/qpid/broker/EventExchangeDeclare.h"
#include "qmf/org/apache/qpid/broker/EventExchangeDelete.h"
#include "qmf/org/apache/qpid/broker/EventQueueDeclare.h"
#include "qmf/org/apache/qpid/broker/EventQueueDelete.h"
#include "qmf/org/apache/qpid/broker/EventSubscribe.h"
#include "qmf/org/apache/qpid/ha/EventMembersUpdate.h"
#include <boost/bind.hpp>
#include <algorithm>
#include <sstream>
#include <iostream>
#include <assert.h>

namespace qpid {
namespace ha {

using qmf::org::apache::qpid::broker::EventBind;
using qmf::org::apache::qpid::broker::EventUnbind;
using qmf::org::apache::qpid::broker::EventExchangeDeclare;
using qmf::org::apache::qpid::broker::EventExchangeDelete;
using qmf::org::apache::qpid::broker::EventQueueDeclare;
using qmf::org::apache::qpid::broker::EventQueueDelete;
using qmf::org::apache::qpid::broker::EventSubscribe;
using qmf::org::apache::qpid::ha::EventMembersUpdate;
using qpid::broker::amqp_0_10::MessageTransfer;
using namespace framing;
using namespace std;
using std::ostream;
using types::Variant;
using namespace broker;

namespace {

const string QPID_CONFIGURATION_REPLICATOR("qpid.broker-replicator");

const string CLASS_NAME("_class_name");
const string EVENT("_event");
const string OBJECT_NAME("_object_name");
const string PACKAGE_NAME("_package_name");
const string QUERY_RESPONSE("_query_response");
const string VALUES("_values");
const string SCHEMA_ID("_schema_id");
const string WHAT("_what");

const string ALTEX("altEx");
const string ALTEXCHANGE("altExchange");
const string ARGS("args");
const string ARGUMENTS("arguments");
const string AUTODEL("autoDel");
const string AUTODELETE("autoDelete");
const string BIND("bind");
const string BINDING("binding");
const string BINDING_KEY("bindingKey");
const string CREATED("created");
const string DISP("disp");
const string DEST("dest");
const string DURABLE("durable");
const string EXCHANGE("exchange");
const string EXCL("excl");
const string EXCLUSIVE("exclusive");
const string EXNAME("exName");
const string EXTYPE("exType");
const string HA_BROKER("habroker");
const string KEY("key");
const string NAME("name");
const string PARTIAL("partial");
const string QNAME("qName");
const string QUEUE("queue");
const string TYPE("type");
const string UNBIND("unbind");
const string CONSUMER_COUNT("consumerCount");

const string AGENT_EVENT_BROKER("agent.ind.event.org_apache_qpid_broker.#");
const string AGENT_EVENT_HA("agent.ind.event.org_apache_qpid_ha.#");
const string QMF2("qmf2");
const string QMF_CONTENT("qmf.content");
const string QMF_DEFAULT_TOPIC("qmf.default.topic");
const string QMF_OPCODE("qmf.opcode");

const string OBJECT("OBJECT");
const string ORG_APACHE_QPID_BROKER("org.apache.qpid.broker");
const string ORG_APACHE_QPID_HA("org.apache.qpid.ha");
const string QMF_DEFAULT_DIRECT("qmf.default.direct");
const string _QUERY_REQUEST("_query_request");
const string BROKER("broker");
const string MEMBERS("members");
const string AUTO_DELETE_TIMEOUT("qpid.auto_delete_timeout");

void sendQuery(const string& packageName, const string& className, const string& queueName,
               SessionHandler& sessionHandler)
{
    framing::AMQP_ServerProxy peer(sessionHandler.out);
    Variant::Map request;
    request[WHAT] = OBJECT;
    Variant::Map schema;
    schema[CLASS_NAME] = className;
    schema[PACKAGE_NAME] = packageName;
    request[SCHEMA_ID] = schema;

    AMQFrame method((MessageTransferBody(ProtocolVersion(), QMF_DEFAULT_DIRECT, 0, 0)));
    method.setBof(true);
    method.setEof(false);
    method.setBos(true);
    method.setEos(true);
    AMQHeaderBody headerBody;
    MessageProperties* props = headerBody.get<MessageProperties>(true);
    props->setReplyTo(qpid::framing::ReplyTo("", queueName));
    props->setAppId(QMF2);
    props->getApplicationHeaders().setString(QMF_OPCODE, _QUERY_REQUEST);
    headerBody.get<qpid::framing::DeliveryProperties>(true)->setRoutingKey(BROKER);
    headerBody.get<qpid::framing::MessageProperties>(true)->setCorrelationId(className);
    AMQFrame header(headerBody);
    header.setBof(false);
    header.setEof(false);
    header.setBos(true);
    header.setEos(true);
    AMQContentBody data;
    qpid::amqp_0_10::MapCodec::encode(request, data.getData());
    AMQFrame content(data);
    content.setBof(false);
    content.setEof(true);
    content.setBos(true);
    content.setEos(true);
    sessionHandler.out->handle(method);
    sessionHandler.out->handle(header);
    sessionHandler.out->handle(content);
}

// Like Variant::asMap but treat void value as an empty map.
Variant::Map asMapVoid(const Variant& value) {
    if (!value.isVoid()) return value.asMap();
    else return Variant::Map();
}
} // namespace

// Listens for errors on the bridge session.
class BrokerReplicator::ErrorListener : public SessionHandler::ErrorListener {
  public:
    ErrorListener(const std::string& lp, BrokerReplicator& br) :
        logPrefix(lp), brokerReplicator(br) {}

    void connectionException(framing::connection::CloseCode, const std::string& msg) {
        QPID_LOG(error, logPrefix << "Connection error: " << msg);
    }
    void channelException(framing::session::DetachCode, const std::string& msg) {
        QPID_LOG(error, logPrefix << "Channel error: " << msg);
    }
    void executionException(framing::execution::ErrorCode, const std::string& msg) {
        QPID_LOG(error, logPrefix << "Execution error: " << msg);
    }
    void detach() {
        QPID_LOG(debug, logPrefix << "Session detached.");
    }

  private:
    std::string logPrefix;
    BrokerReplicator& brokerReplicator;
};

class BrokerReplicator::ConnectionObserver : public broker::ConnectionObserver
{
  public:
    ConnectionObserver(BrokerReplicator& br) : brokerReplicator(br) {}
    virtual void connection(Connection&) {}
    virtual void opened(Connection&) {}

    virtual void closed(Connection& c) {
        if (brokerReplicator.link && &c == brokerReplicator.connection)
            brokerReplicator.disconnected();
    }
    virtual void forced(Connection& c, const std::string& /*message*/) { closed(c); }
  private:
    BrokerReplicator& brokerReplicator;
};

/** Keep track of queues or exchanges during the update process to solve 2
 * problems.
 *
 * 1. Once all responses are processed, remove any queues/exchanges
 * that were not mentioned as they no longer exist on the primary.
 *
 * 2. During the update if we see an event for an object we should
 * ignore any subsequent responses for that object as they are out
 * of date.
 */
class BrokerReplicator::UpdateTracker {
  public:
    typedef std::set<std::string> Names;
    typedef boost::function<void (const std::string&)> CleanFn;

    UpdateTracker(const std::string& type_, // "queue" or "exchange"
                  CleanFn f, const ReplicationTest& rt)
        : type(type_), cleanFn(f), repTest(rt) {}

    /** Destructor cleans up remaining initial queues. */
    ~UpdateTracker() {
        // Don't throw in a destructor.
        try {
            for_each(initial.begin(), initial.end(),
                     boost::bind(&UpdateTracker::clean, this, _1));
        }
        catch (const std::exception& e) {
            QPID_LOG(error, "Error in cleanup of lost objects: " << e.what());
        }
    }

    /** Add an exchange name */
    void addExchange(Exchange::shared_ptr ex)  {
        if (repTest.getLevel(*ex))
            initial.insert(ex->getName());
    }

    /** Add a queue name. */
    void addQueue(Queue::shared_ptr q) {
        if (repTest.getLevel(*q))
            initial.insert(q->getName());
    }

    /** Received an event for name */
    void event(const std::string& name) {
        initial.erase(name); // no longer a candidate for deleting
        events.insert(name); // we have seen an event for this name
    }

    /** Received a response for name.
     *@return true if this response should be processed, false if we have
     *already seen an event for this object.
     */
    bool response(const std::string& name) {
        initial.erase(name); // no longer a candidate for deleting
        return events.find(name) == events.end(); // true if no event seen yet.
    }

  private:
    void clean(const std::string& name) {
        QPID_LOG(info, "Backup: Deleted " << type << " " << name <<
                 ": no longer exists on primary");
        cleanFn(name);
    }

    std::string type;
    Names initial, events;
    CleanFn cleanFn;
    ReplicationTest repTest;
};

BrokerReplicator::BrokerReplicator(HaBroker& hb, const boost::shared_ptr<Link>& l)
    : Exchange(QPID_CONFIGURATION_REPLICATOR),
      logPrefix("Backup: "), replicationTest(NONE),
      haBroker(hb), broker(hb.getBroker()),
      exchanges(broker.getExchanges()), queues(broker.getQueues()),
      link(l),
      initialized(false),
      alternates(hb.getBroker().getExchanges()),
      connection(0)
{
    connectionObserver.reset(new ConnectionObserver(*this));
    broker.getConnectionObservers().add(connectionObserver);
    framing::FieldTable args = getArgs();
    args.setString(QPID_REPLICATE, printable(NONE).str());
    setArgs(args);

    dispatch[EventQueueDeclare::getFullName()] = &BrokerReplicator::doEventQueueDeclare;
    dispatch[EventQueueDelete::getFullName()] = &BrokerReplicator::doEventQueueDelete;
    dispatch[EventExchangeDeclare::getFullName()] = &BrokerReplicator::doEventExchangeDeclare;
    dispatch[EventExchangeDelete::getFullName()] = &BrokerReplicator::doEventExchangeDelete;
    dispatch[EventBind::getFullName()] = &BrokerReplicator::doEventBind;
    dispatch[EventUnbind::getFullName()] = &BrokerReplicator::doEventUnbind;
    dispatch[EventMembersUpdate::getFullName()] = &BrokerReplicator::doEventMembersUpdate;
    dispatch[EventSubscribe::getFullName()] = &BrokerReplicator::doEventSubscribe;
}

void BrokerReplicator::initialize() {
    // Can't do this in the constructor because we need a shared_ptr to this.
    types::Uuid uuid(true);
    const std::string name(QPID_CONFIGURATION_REPLICATOR + ".bridge." + uuid.str());
    std::pair<Bridge::shared_ptr, bool> result = broker.getLinks().declare(
        name,               // name for bridge
        *link,              // parent
        false,              // durable
        QPID_CONFIGURATION_REPLICATOR, // src
        QPID_CONFIGURATION_REPLICATOR, // dest
        "",                 // key
        false,              // isQueue
        false,              // isLocal
        "",                 // id/tag
        "",                 // excludes
        false,              // dynamic
        0,                  // sync?
        // shared_ptr keeps this in memory until outstanding connected
        // calls are run.
        boost::bind(&BrokerReplicator::connected, shared_from_this(), _1, _2)
    );
    assert(result.second);
    result.first->setErrorListener(
        boost::shared_ptr<ErrorListener>(new ErrorListener(logPrefix, *this)));
}

BrokerReplicator::~BrokerReplicator() { shutdown(); }

namespace {
void collectQueueReplicators(
    const boost::shared_ptr<Exchange> ex, set<boost::shared_ptr<QueueReplicator> >& collect)
{
    boost::shared_ptr<QueueReplicator> qr(boost::dynamic_pointer_cast<QueueReplicator>(ex));
    if (qr) collect.insert(qr);
}
} // namespace

void BrokerReplicator::shutdown() {
    // NOTE: this is called in a QMF dispatch thread, not the Link's connection
    // thread.  It's OK to be unlocked because it doesn't use any mutable state,
    // it only calls thread safe functions objects belonging to the Broker.

    // Unregister with broker objects:
    if (connectionObserver) {
        broker.getConnectionObservers().remove(connectionObserver);
        connectionObserver.reset();
    }
    broker.getExchanges().destroy(getName());
}

// This is called in the connection IO thread when the bridge is started.
void BrokerReplicator::connected(Bridge& bridge, SessionHandler& sessionHandler) {
    // Use the credentials of the outgoing Link connection for creating queues,
    // exchanges etc. We know link->getConnection() is non-zero because we are
    // being called in the connections thread context.
    //
    connection = link->getConnection();
    assert(connection);
    userId = link->getConnection()->getUserId();
    remoteHost = link->getConnection()->getUrl();

    link->getRemoteAddress(primary);
    string queueName = bridge.getQueueName();

    QPID_LOG(info, logPrefix << (initialized ? "Failing over" : "Connecting")
             << " to primary " << primary
             << " status:" << printable(haBroker.getStatus()));
    initialized = true;

    exchangeTracker.reset(
        new UpdateTracker("exchange",
                          boost::bind(&BrokerReplicator::deleteExchange, this, _1),
                          replicationTest));
    exchanges.eachExchange(
        boost::bind(&UpdateTracker::addExchange, exchangeTracker.get(), _1));

    queueTracker.reset(
        new UpdateTracker("queue",
                          boost::bind(&BrokerReplicator::deleteQueue, this, _1, true),
                          replicationTest));
    queues.eachQueue(boost::bind(&UpdateTracker::addQueue, queueTracker.get(), _1));

    framing::AMQP_ServerProxy peer(sessionHandler.out);
    const qmf::org::apache::qpid::broker::ArgsLinkBridge& args(bridge.getArgs());

    //declare and bind an event queue
    FieldTable declareArgs;
    declareArgs.setString(QPID_REPLICATE, printable(NONE).str());
    peer.getQueue().declare(queueName, "", false, false, true, true, declareArgs);
    peer.getExchange().bind(queueName, QMF_DEFAULT_TOPIC, AGENT_EVENT_BROKER, FieldTable());
    peer.getExchange().bind(queueName, QMF_DEFAULT_TOPIC, AGENT_EVENT_HA, FieldTable());
    //subscribe to the queue
    FieldTable arguments;
    arguments.setInt(QueueReplicator::QPID_SYNC_FREQUENCY, 1); // FIXME aconway 2012-05-22: optimize?
    peer.getMessage().subscribe(
        queueName, args.i_dest, 1/*accept-none*/, 0/*pre-acquired*/,
        false/*exclusive*/, "", 0, arguments);
    peer.getMessage().setFlowMode(args.i_dest, 1); // Window
    peer.getMessage().flow(args.i_dest, 0, haBroker.getSettings().getFlowMessages());
    peer.getMessage().flow(args.i_dest, 1, haBroker.getSettings().getFlowBytes());

    // Issue a query request for queues, exchanges, bindings and the habroker
    // using event queue as the reply-to address
    sendQuery(ORG_APACHE_QPID_HA, HA_BROKER, queueName, sessionHandler);
    sendQuery(ORG_APACHE_QPID_BROKER, QUEUE, queueName, sessionHandler);
    sendQuery(ORG_APACHE_QPID_BROKER, EXCHANGE, queueName, sessionHandler);
    sendQuery(ORG_APACHE_QPID_BROKER, BINDING, queueName, sessionHandler);
}

void BrokerReplicator::route(Deliverable& msg) {
    // We transition from JOINING->CATCHUP on the first message received from the primary.
    // Until now we couldn't be sure if we had a good connection to the primary.
    if (haBroker.getStatus() == JOINING) {
        haBroker.getMembership().setStatus(CATCHUP);
        QPID_LOG(notice, logPrefix << "Connected to primary " << primary);
    }
    Variant::List list;
    try {
        if (!MessageTransfer::isQMFv2(msg.getMessage()))
            throw Exception("Unexpected message, not QMF2 event or query response.");
        // decode as list
        string content = msg.getMessage().getContent();
        qpid::amqp_0_10::ListCodec::decode(content, list);

        if (msg.getMessage().getPropertyAsString(QMF_CONTENT) == EVENT) {
            for (Variant::List::iterator i = list.begin(); i != list.end(); ++i) {
                Variant::Map& map = i->asMap();
                QPID_LOG(trace, "Broker replicator event: " << map);
                Variant::Map& schema = map[SCHEMA_ID].asMap();
                Variant::Map& values = map[VALUES].asMap();
                EventKey key(schema[PACKAGE_NAME], schema[CLASS_NAME]);
                EventDispatchMap::iterator j = dispatch.find(key);
                if (j != dispatch.end()) (this->*(j->second))(values);
            }
        } else if (msg.getMessage().getPropertyAsString(QMF_OPCODE) == QUERY_RESPONSE) {
            for (Variant::List::iterator i = list.begin(); i != list.end(); ++i) {
                Variant::Map& map = i->asMap();
                QPID_LOG(trace, "Broker replicator response: " << map);
                string type = map[SCHEMA_ID].asMap()[CLASS_NAME].asString();
                Variant::Map& values = map[VALUES].asMap();
                framing::FieldTable args;
                qpid::amqp_0_10::translate(asMapVoid(values[ARGUMENTS]), args);
                if      (type == QUEUE) doResponseQueue(values);
                else if (type == EXCHANGE) doResponseExchange(values);
                else if (type == BINDING) doResponseBind(values);
                else if (type == HA_BROKER) doResponseHaBroker(values);
            }
            if (MessageTransfer::isLastQMFResponse(msg.getMessage(), EXCHANGE)) {
                QPID_LOG(debug, logPrefix << "All exchange responses received.")
                exchangeTracker.reset(); // Clean up exchanges that no longer exist in the primary
                alternates.clear();
            }
            if (MessageTransfer::isLastQMFResponse(msg.getMessage(), QUEUE)) {
                QPID_LOG(debug, logPrefix << "All queue responses received.");
                queueTracker.reset(); // Clean up queues that no longer exist in the primary
            }
        }
    } catch (const std::exception& e) {
;
        haBroker.shutdown(
            QPID_MSG(logPrefix << "Configuration replication failed: "
                     << e.what() << ": while handling: " << list));
        throw;
    }
}


void BrokerReplicator::doEventQueueDeclare(Variant::Map& values) {
    Variant::Map argsMap = asMapVoid(values[ARGS]);
    if (values[DISP] == CREATED && replicationTest.getLevel(argsMap)) {
        string name = values[QNAME].asString();
        QueueSettings settings(values[DURABLE].asBool(), values[AUTODEL].asBool());
        QPID_LOG(debug, logPrefix << "Queue declare event: " << name);
        if (queueTracker.get()) queueTracker->event(name);
        framing::FieldTable args;
        qpid::amqp_0_10::translate(argsMap, args);
        // If we already have a queue with this name, replace it.
        // The queue was definitely created on the primary.
        if (queues.find(name)) {
            QPID_LOG(warning, logPrefix << "Declare event, replacing exsiting queue: "
                     << name);
            deleteQueue(name);
        }
        replicateQueue(name, values[DURABLE].asBool(), values[AUTODEL].asBool(), args,
                       values[ALTEX].asString());
    }
}

boost::shared_ptr<QueueReplicator> BrokerReplicator::findQueueReplicator(
    const std::string& qname)
{
    string rname = QueueReplicator::replicatorName(qname);
    boost::shared_ptr<broker::Exchange> ex = exchanges.find(rname);
    return boost::dynamic_pointer_cast<QueueReplicator>(ex);
}

void BrokerReplicator::doEventQueueDelete(Variant::Map& values) {
    // The remote queue has already been deleted so replicator
    // sessions may be closed by a "queue deleted" exception.
    string name = values[QNAME].asString();
    boost::shared_ptr<Queue> queue = queues.find(name);
    if (queue && replicationTest.getLevel(*queue)) {
        QPID_LOG(debug, logPrefix << "Queue delete event: " << name);
        if (queueTracker.get()) queueTracker->event(name);
        deleteQueue(name);
    }
}

void BrokerReplicator::doEventExchangeDeclare(Variant::Map& values) {
    Variant::Map argsMap(asMapVoid(values[ARGS]));
    if (values[DISP] == CREATED && replicationTest.getLevel(argsMap)) {
        string name = values[EXNAME].asString();
        QPID_LOG(debug, logPrefix << "Exchange declare event: " << name);
        if (exchangeTracker.get()) exchangeTracker->event(name);
        framing::FieldTable args;
        qpid::amqp_0_10::translate(argsMap, args);
        // If we already have a exchange with this name, replace it.
        // The exchange was definitely created on the primary.
        if (exchanges.find(name)) {
            deleteExchange(name);
            QPID_LOG(warning, logPrefix << "Declare event, replacing existing exchange: "
                     << name);
        }
        CreateExchangeResult result = createExchange(
            name, values[EXTYPE].asString(), values[DURABLE].asBool(), args,
            values[ALTEX].asString());
        assert(result.second);
    }
}

void BrokerReplicator::doEventExchangeDelete(Variant::Map& values) {
    string name = values[EXNAME].asString();
    boost::shared_ptr<Exchange> exchange = exchanges.find(name);
    if (!exchange) {
        QPID_LOG(warning, logPrefix << "Exchange delete event, not found: " << name);
    } else if (!replicationTest.getLevel(*exchange)) {
        QPID_LOG(warning, logPrefix << "Exchange delete event, not replicated: " << name);
    } else {
        QPID_LOG(debug, logPrefix << "Exchange delete event:" << name);
        if (exchangeTracker.get()) exchangeTracker->event(name);
        deleteExchange(name);
    }
}

void BrokerReplicator::doEventBind(Variant::Map& values) {
    boost::shared_ptr<Exchange> exchange =
        exchanges.find(values[EXNAME].asString());
    boost::shared_ptr<Queue> queue =
        queues.find(values[QNAME].asString());
    framing::FieldTable args;
    qpid::amqp_0_10::translate(asMapVoid(values[ARGS]), args);
    // We only replicate binds for a replicated queue to replicated exchange
    // that both exist locally. Respect the replication level set in the
    // bind arguments, but replicate by default.
    if (exchange && replicationTest.getLevel(*exchange) &&
        queue && replicationTest.getLevel(*queue) &&
        ReplicationTest(ALL).getLevel(args))
    {
        string key = values[KEY].asString();
        QPID_LOG(debug, logPrefix << "Bind event: exchange=" << exchange->getName()
                 << " queue=" << queue->getName()
                 << " key=" << key
                 << " args=" << args);
        queue->bind(exchange, key, args);
    }
}

void BrokerReplicator::doEventUnbind(Variant::Map& values) {
    boost::shared_ptr<Exchange> exchange =
        exchanges.find(values[EXNAME].asString());
    boost::shared_ptr<Queue> queue =
        queues.find(values[QNAME].asString());
    // We only replicate unbinds for a replicated queue to replicated
    // exchange that both exist locally.
    if (exchange && replicationTest.getLevel(*exchange) &&
        queue && replicationTest.getLevel(*queue))
    {
        string key = values[KEY].asString();
        QPID_LOG(debug, logPrefix << "Unbind event: exchange=" << exchange->getName()
                 << " queue=" << queue->getName()
                 << " key=" << key);
        exchange->unbind(queue, key, 0);
    }
}

void BrokerReplicator::doEventMembersUpdate(Variant::Map& values) {
    Variant::List members = values[MEMBERS].asList();
    setMembership(members);
}

void BrokerReplicator::doEventSubscribe(Variant::Map& values) {
    // Ignore queue replicator subscriptions.
    if (QueueReplicator::isReplicatorName(values[DEST].asString())) return;
    boost::shared_ptr<QueueReplicator> qr = findQueueReplicator(values[QNAME]);
    if (qr) {
        qr->setSubscribed();
        QPID_LOG(debug, logPrefix << "Subscribe event: " << values[QNAME]);
    }
}

namespace {

// Get the alternate exchange from the exchange field of a queue or exchange response.
static const string EXCHANGE_KEY_PREFIX("org.apache.qpid.broker:exchange:");

string getAltExchange(const types::Variant& var) {
    if (!var.isVoid()) {
        management::ObjectId oid(var);
        string key = oid.getV2Key();
        if (key.find(EXCHANGE_KEY_PREFIX) != 0) throw Exception("Invalid exchange reference: "+key);
        return key.substr(EXCHANGE_KEY_PREFIX.size());
    }
    else return string();
}

Variant getHaUuid(const Variant::Map& map) {
    Variant::Map::const_iterator i = map.find(QPID_HA_UUID);
    return i == map.end() ? Variant() : i->second;
}

} // namespace


void BrokerReplicator::doResponseQueue(Variant::Map& values) {
    Variant::Map argsMap(asMapVoid(values[ARGUMENTS]));
    if (!replicationTest.getLevel(argsMap)) return;
    string name(values[NAME].asString());
    if (!queueTracker.get())
        throw Exception(QPID_MSG("Unexpected queue response: " << values));
    if (!queueTracker->response(name)) return; // Response is out-of-date
    QPID_LOG(debug, logPrefix << "Queue response: " << name);
    boost::shared_ptr<Queue> queue = queues.find(name);
    if (queue) { // Already exists
        bool uuidOk = (getHaUuid(queue->getSettings().original) == getHaUuid(argsMap));
        if (!uuidOk) QPID_LOG(debug, logPrefix << "UUID mismatch for queue: " << name);
        if (uuidOk && findQueueReplicator(name)) return; // already replicated, UUID OK.
        QPID_LOG(debug, logPrefix << "Queue response replacing queue:  " << name);
        deleteQueue(name);
    }
    framing::FieldTable args;
    qpid::amqp_0_10::translate(argsMap, args);
    boost::shared_ptr<QueueReplicator> qr = replicateQueue(
        name, values[DURABLE].asBool(), values[AUTODELETE].asBool(), args,
        getAltExchange(values[ALTEXCHANGE]));
    if (qr) {
        Variant::Map::const_iterator i = values.find(CONSUMER_COUNT);
        if (i != values.end() && isIntegerType(i->second.getType())) {
            if (i->second.asInt64()) qr->setSubscribed();
        }
    }
}

void BrokerReplicator::doResponseExchange(Variant::Map& values) {
    Variant::Map argsMap(asMapVoid(values[ARGUMENTS]));
    if (!replicationTest.getLevel(argsMap)) return;
    string name = values[NAME].asString();
    if (!exchangeTracker.get())
        throw Exception(QPID_MSG("Unexpected exchange response: " << values));
    if (!exchangeTracker->response(name)) return; // Response is out of date.
    QPID_LOG(debug, logPrefix << "Exchange response: " << name);
    framing::FieldTable args;
    qpid::amqp_0_10::translate(argsMap, args);
    // If we see an exchange with the same name as one we have, but a different UUID,
    // then replace the one we have.
    boost::shared_ptr<Exchange> exchange = exchanges.find(name);
    if (exchange &&
        exchange->getArgs().getAsString(QPID_HA_UUID) != args.getAsString(QPID_HA_UUID))
    {
        QPID_LOG(warning, logPrefix << "UUID mismatch, replacing exchange: "
                 << name);
        deleteExchange(name);
    }
    CreateExchangeResult result = createExchange(
        name, values[TYPE].asString(), values[DURABLE].asBool(), args,
        getAltExchange(values[ALTEXCHANGE]));
}

namespace {
const std::string QUEUE_REF_PREFIX("org.apache.qpid.broker:queue:");
const std::string EXCHANGE_REF_PREFIX("org.apache.qpid.broker:exchange:");

std::string getRefName(const std::string& prefix, const Variant& ref) {
    Variant::Map map(ref.asMap());
    Variant::Map::const_iterator i = map.find(OBJECT_NAME);
    if (i == map.end())
        throw Exception(QPID_MSG("Replicator: invalid object reference: " << ref));
    const std::string name = i->second.asString();
    if (name.compare(0, prefix.size(), prefix) != 0)
        throw Exception(QPID_MSG("Replicator: unexpected reference prefix: " << name));
    std::string ret = name.substr(prefix.size());
    return ret;
}

const std::string EXCHANGE_REF("exchangeRef");
const std::string QUEUE_REF("queueRef");

} // namespace

void BrokerReplicator::doResponseBind(Variant::Map& values) {
    std::string exName = getRefName(EXCHANGE_REF_PREFIX, values[EXCHANGE_REF]);
    std::string qName = getRefName(QUEUE_REF_PREFIX, values[QUEUE_REF]);
    boost::shared_ptr<Exchange> exchange = exchanges.find(exName);
    boost::shared_ptr<Queue> queue = queues.find(qName);

    framing::FieldTable args;
    qpid::amqp_0_10::translate(asMapVoid(values[ARGUMENTS]), args);

    // Automatically replicate binding if queue and exchange exist and are replicated.
    // Respect replicate setting in binding args but default to replicated.
    if (exchange && replicationTest.getLevel(*exchange) &&
        queue && replicationTest.getLevel(*queue) &&
        ReplicationTest(ALL).getLevel(args))
    {
        string key = values[BINDING_KEY].asString();
        QPID_LOG(debug, logPrefix << "Bind response: exchange:" << exName
                 << " queue:" << qName
                 << " key:" << key
                 << " args:" << args);
        queue->bind(exchange, key, args);
    }
}

namespace {
const string REPLICATE_DEFAULT="replicateDefault";
}

// Received the ha-broker configuration object for the primary broker.
void BrokerReplicator::doResponseHaBroker(Variant::Map& values) {
    try {
        QPID_LOG(trace, logPrefix << "HA Broker response: " << values);
        ReplicateLevel mine = haBroker.getSettings().replicateDefault.get();
        ReplicateLevel primary = replicationTest.getLevel(values[REPLICATE_DEFAULT].asString());
        if (mine != primary)
            throw Exception(QPID_MSG("Replicate default on backup (" << mine
                                     << ") does not match primary (" <<  primary << ")"));
        setMembership(values[MEMBERS].asList());
    } catch (const std::exception& e) {
        haBroker.shutdown(
            QPID_MSG(logPrefix << "Invalid HA Broker response: " << e.what()
                     << ": " << values));

        throw;
    }
}

boost::shared_ptr<QueueReplicator> BrokerReplicator::startQueueReplicator(
    const boost::shared_ptr<Queue>& queue)
{
    if (replicationTest.getLevel(*queue) == ALL) {
        boost::shared_ptr<QueueReplicator> qr(
            new QueueReplicator(haBroker, queue, link));
        if (!exchanges.registerExchange(qr))
            throw Exception(QPID_MSG("Duplicate queue replicator " << qr->getName()));
        qr->activate();
        return qr;
    }
    return boost::shared_ptr<QueueReplicator>();
}

void BrokerReplicator::deleteQueue(const std::string& name, bool purge) {
    Queue::shared_ptr queue = queues.find(name);
    if (queue) {
        // Purge before deleting to ensure that we don't reroute any
        // messages. Any reroutes will be done at the primary and
        // replicated as normal.
        if (purge) queue->purge(0, boost::shared_ptr<Exchange>());
        broker.deleteQueue(name, userId, remoteHost);
        QPID_LOG(debug, logPrefix << "Queue deleted: " << name);
    }
}

void BrokerReplicator::deleteExchange(const std::string& name) {
    try {
        boost::shared_ptr<broker::Exchange> exchange = exchanges.find(name);
        if (!exchange) {
            QPID_LOG(warning, logPrefix << "Cannot delete exchange, not found: " << name);
            return;
        }
        if (exchange->inUseAsAlternate()) {
            QPID_LOG(warning, "Cannot delete exchange, in use as alternate: " << name);
            return;
        }
        broker.deleteExchange(name, userId, remoteHost);
        QPID_LOG(debug, logPrefix << "Exchange deleted: " << name);
    } catch (const framing::NotFoundException&) {
        QPID_LOG(debug, logPrefix << "Exchange not found for deletion: " << name);
    }
}

boost::shared_ptr<QueueReplicator> BrokerReplicator::replicateQueue(
    const std::string& name,
    bool durable,
    bool autodelete,
    const qpid::framing::FieldTable& arguments,
    const std::string& alternateExchange)
{
    QueueSettings settings(durable, autodelete);
    settings.populate(arguments, settings.storeSettings);
    CreateQueueResult result =
        broker.createQueue(
            name,
            settings,
            0,// no owner regardless of exclusivity on primary
            string(), // Set alternate exchange below
            userId,
            remoteHost);
    boost::shared_ptr<QueueReplicator> qr;
    if (!findQueueReplicator(name)) qr = startQueueReplicator(result.first);
    if (result.second && !alternateExchange.empty()) {
        alternates.setAlternate(
            alternateExchange, boost::bind(&Queue::setAlternateExchange, result.first, _1));
    }
    return qr;
}

BrokerReplicator::CreateExchangeResult BrokerReplicator::createExchange(
    const std::string& name,
    const std::string& type,
    bool durable,
    const qpid::framing::FieldTable& args,
    const std::string& alternateExchange)
{
    CreateExchangeResult result =
        broker.createExchange(
            name,
            type,
            durable,
            string(),  // Set alternate exchange below
            args,
            userId,
            remoteHost);
    alternates.addExchange(result.first);
    if (!alternateExchange.empty()) {
        alternates.setAlternate(
            alternateExchange, boost::bind(&Exchange::setAlternate, result.first, _1));
    }
    return result;
}

bool BrokerReplicator::bind(boost::shared_ptr<Queue>, const string&, const framing::FieldTable*) { return false; }
bool BrokerReplicator::unbind(boost::shared_ptr<Queue>, const string&, const framing::FieldTable*) { return false; }
bool BrokerReplicator::isBound(boost::shared_ptr<Queue>, const string* const, const framing::FieldTable* const) { return false; }

string BrokerReplicator::getType() const { return QPID_CONFIGURATION_REPLICATOR; }

void BrokerReplicator::autoDeleteCheck(boost::shared_ptr<Exchange> ex) {
    boost::shared_ptr<QueueReplicator> qr(boost::dynamic_pointer_cast<QueueReplicator>(ex));
    if (!qr) return;
    assert(qr);
    if (qr->getQueue()->isAutoDelete() && qr->isSubscribed()) {
        if (qr->getQueue()->getSettings().autoDeleteDelay) {
            // Start the auto-delete timer
            Queue::tryAutoDelete(broker, qr->getQueue(), remoteHost, userId);
        }
        else {
            // Delete immediately. Don't purge, the primary is gone so we need
            // to reroute the deleted messages.
            deleteQueue(qr->getQueue()->getName(), false);
        }
    }
}

// Callback function for accumulating exchange candidates
namespace {
	void exchangeAccumulatorCallback(vector<boost::shared_ptr<Exchange> >& c, const Exchange::shared_ptr& i) {
		c.push_back(i);
	}
}

void BrokerReplicator::disconnected() {
    QPID_LOG(info, logPrefix << "Disconnected from " << primary);
    connection = 0;
    // Clean up auto-delete queues
    vector<boost::shared_ptr<Exchange> > collect;
    // Make a copy so we can work outside the ExchangeRegistry lock
    exchanges.eachExchange(
        boost::bind(&exchangeAccumulatorCallback, boost::ref(collect), _1));
    for_each(collect.begin(), collect.end(),
             boost::bind(&BrokerReplicator::autoDeleteCheck, this, _1));
}

void BrokerReplicator::setMembership(const Variant::List& brokers) {
    Membership& membership(haBroker.getMembership());
    membership.assign(brokers);
    // Check if the primary has signalled a change in my status:
    // from CATCHUP to READY when we are caught up.
    // from READY TO CATCHUP if we are timed out during fail-over.
    BrokerInfo info;
    if (membership.get(membership.getSelf(), info)) {
        BrokerStatus oldStatus = haBroker.getStatus();
        BrokerStatus newStatus = info.getStatus();
        if (oldStatus == CATCHUP && newStatus == READY) {
            QPID_LOG(info, logPrefix << logPrefix << "Caught-up and ready");
            haBroker.getMembership().setStatus(READY);
        }
        else if (oldStatus == READY && newStatus == CATCHUP) {
            QPID_LOG(info, logPrefix << logPrefix << "No longer ready, catching up");
            haBroker.getMembership().setStatus(CATCHUP);
        }
    }
}

}} // namespace broker
