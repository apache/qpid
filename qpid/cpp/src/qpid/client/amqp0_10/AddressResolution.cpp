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
#include "qpid/client/amqp0_10/AddressResolution.h"
#include "qpid/amqp_0_10/Codecs.h"
#include "qpid/client/amqp0_10/MessageSource.h"
#include "qpid/client/amqp0_10/MessageSink.h"
#include "qpid/client/amqp0_10/OutgoingMessage.h"
#include "qpid/messaging/Address.h"
#include "qpid/messaging/AddressImpl.h"
#include "qpid/messaging/Message.h"
#include "qpid/types/Variant.h"
#include "qpid/messaging/exceptions.h"
#include "qpid/log/Statement.h"
#include "qpid/framing/enum.h"
#include "qpid/framing/ExchangeBoundResult.h"
#include "qpid/framing/ExchangeQueryResult.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/framing/FieldValue.h"
#include "qpid/framing/QueueQueryResult.h"
#include "qpid/framing/ReplyTo.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/framing/Uuid.h"
#include <boost/assign.hpp>
#include <boost/format.hpp>

namespace qpid {
namespace client {
namespace amqp0_10 {

using qpid::Exception;
using qpid::messaging::Address;
using qpid::messaging::AddressError;
using qpid::messaging::MalformedAddress;
using qpid::messaging::ResolutionError;
using qpid::messaging::NotFound;
using qpid::messaging::AssertionFailed;
using qpid::framing::ExchangeBoundResult;
using qpid::framing::ExchangeQueryResult;
using qpid::framing::FieldTable;
using qpid::framing::FieldValue;
using qpid::framing::QueueQueryResult;
using qpid::framing::ReplyTo;
using qpid::framing::Uuid;
using namespace qpid::types;
using namespace qpid::framing::message;
using namespace qpid::amqp_0_10;
using namespace boost::assign;

class Verifier
{
  public:
    Verifier();
    void verify(const Address& address) const;
  private:
    Variant::Map defined;
    void verify(const Variant::Map& allowed, const Variant::Map& actual) const;
};

namespace{
const Variant EMPTY_VARIANT;
const FieldTable EMPTY_FIELD_TABLE;
const Variant::List EMPTY_LIST;
const std::string EMPTY_STRING;

//policy types
const std::string CREATE("create");
const std::string ASSERT("assert");
const std::string DELETE("delete");

//option names
const std::string NODE("node");
const std::string LINK("link");
const std::string MODE("mode");
const std::string RELIABILITY("reliability");
const std::string TIMEOUT("timeout");
const std::string NAME("name");
const std::string DURABLE("durable");
const std::string X_DECLARE("x-declare");
const std::string X_SUBSCRIBE("x-subscribe");
const std::string X_BINDINGS("x-bindings");
const std::string SELECTOR("selector");
const std::string APACHE_SELECTOR("x-apache-selector");
const std::string QPID_FILTER("qpid.filter");
const std::string EXCHANGE("exchange");
const std::string QUEUE("queue");
const std::string KEY("key");
const std::string ARGUMENTS("arguments");
const std::string ALTERNATE_EXCHANGE("alternate-exchange");
const std::string TYPE("type");
const std::string EXCLUSIVE("exclusive");
const std::string AUTO_DELETE("auto-delete");

//policy values
const std::string ALWAYS("always");
const std::string NEVER("never");
const std::string RECEIVER("receiver");
const std::string SENDER("sender");

//address types
const std::string QUEUE_ADDRESS("queue");
const std::string TOPIC_ADDRESS("topic");

//reliability options:
const std::string UNRELIABLE("unreliable");
const std::string AT_MOST_ONCE("at-most-once");
const std::string AT_LEAST_ONCE("at-least-once");
const std::string EXACTLY_ONCE("exactly-once");

//receiver modes:
const std::string BROWSE("browse");
const std::string CONSUME("consume");

//0-10 exchange types:
const std::string TOPIC_EXCHANGE("topic");
const std::string FANOUT_EXCHANGE("fanout");
const std::string DIRECT_EXCHANGE("direct");
const std::string HEADERS_EXCHANGE("headers");
const std::string XML_EXCHANGE("xml");
const std::string WILDCARD_ANY("#");

//exchange prefixes:
const std::string PREFIX_AMQ("amq.");
const std::string PREFIX_QPID("qpid.");

const Verifier verifier;

bool areEquivalent(const FieldValue& a, const FieldValue& b)
{
    return ((a == b) || (a.convertsTo<int64_t>() && b.convertsTo<int64_t>() && a.get<int64_t>() == b.get<int64_t>()));
}
}

struct Binding
{
    Binding(const Variant::Map&);
    Binding(const std::string& exchange, const std::string& queue, const std::string& key);

    std::string exchange;
    std::string queue;
    std::string key;
    FieldTable arguments;
};

struct Bindings : std::vector<Binding>
{
    void add(const Variant::List& bindings);
    void setDefaultExchange(const std::string&);
    void setDefaultQueue(const std::string&);
    void bind(qpid::client::AsyncSession& session);
    void unbind(qpid::client::AsyncSession& session);
    void check(qpid::client::AsyncSession& session);
};

class Node
{
  protected:
    enum CheckMode {FOR_RECEIVER, FOR_SENDER};

    Node(const Address& address);

    const std::string name;
    Variant createPolicy;
    Variant assertPolicy;
    Variant deletePolicy;
    Bindings nodeBindings;
    Bindings linkBindings;

    static bool enabled(const Variant& policy, CheckMode mode);
    static bool createEnabled(const Address& address, CheckMode mode);
    static void convert(const Variant& option, FieldTable& arguments);
    static std::vector<std::string> RECEIVER_MODES;
    static std::vector<std::string> SENDER_MODES;
};


class Queue : protected Node
{
  public:
    Queue(const Address& address);
  protected:
    void checkCreate(qpid::client::AsyncSession&, CheckMode);
    void checkAssert(qpid::client::AsyncSession&, CheckMode);
    void checkDelete(qpid::client::AsyncSession&, CheckMode);
  private:
    const bool durable;
    bool autoDelete;
    bool exclusive;
    const std::string alternateExchange;
    FieldTable arguments;
};

class Exchange : protected Node
{
  public:
    Exchange(const Address& address);
  protected:
    void checkCreate(qpid::client::AsyncSession&, CheckMode);
    void checkAssert(qpid::client::AsyncSession&, CheckMode);
    void checkDelete(qpid::client::AsyncSession&, CheckMode);
    bool isReservedName();

  protected:
    const std::string specifiedType;
  private:
    const bool durable;
    bool autoDelete;
    const std::string alternateExchange;
    FieldTable arguments;
};

class QueueSource : public Queue, public MessageSource
{
  public:
    QueueSource(const Address& address);
    void subscribe(qpid::client::AsyncSession& session, const std::string& destination);
    void cancel(qpid::client::AsyncSession& session, const std::string& destination);
  private:
    const AcquireMode acquireMode;
    const AcceptMode acceptMode;
    bool exclusive;
    FieldTable options;
};

class Subscription : public Exchange, public MessageSource
{
  public:
    Subscription(const Address&, const std::string& actualType);
    void subscribe(qpid::client::AsyncSession& session, const std::string& destination);
    void cancel(qpid::client::AsyncSession& session, const std::string& destination);
  private:
    const std::string queue;
    const bool durable;
    const bool reliable;
    const std::string actualType;
    const bool exclusiveQueue;
    const bool autoDeleteQueue;
    const bool exclusiveSubscription;
    const std::string alternateExchange;
    FieldTable queueOptions;
    FieldTable subscriptionOptions;
    Bindings bindings;

    void bindSubject(const std::string& subject);
    void bindAll();
    void add(const std::string& exchange, const std::string& key);
    static std::string getSubscriptionName(const std::string& base, const std::string& name);
};

class ExchangeSink : public Exchange, public MessageSink
{
  public:
    ExchangeSink(const Address& name);
    void declare(qpid::client::AsyncSession& session, const std::string& name);
    void send(qpid::client::AsyncSession& session, const std::string& name, OutgoingMessage& message);
    void cancel(qpid::client::AsyncSession& session, const std::string& name);
  private:
};

class QueueSink : public Queue, public MessageSink
{
  public:
    QueueSink(const Address& name);
    void declare(qpid::client::AsyncSession& session, const std::string& name);
    void send(qpid::client::AsyncSession& session, const std::string& name, OutgoingMessage& message);
    void cancel(qpid::client::AsyncSession& session, const std::string& name);
  private:
};
bool isQueue(qpid::client::Session session, const qpid::messaging::Address& address);
bool isTopic(qpid::client::Session session, const qpid::messaging::Address& address);

bool in(const Variant& value, const std::vector<std::string>& choices)
{
    if (!value.isVoid()) {
        for (std::vector<std::string>::const_iterator i = choices.begin(); i != choices.end(); ++i) {
            if (value.asString() == *i) return true;
        }
    }
    return false;
}

const Variant& getOption(const Variant::Map& options, const std::string& name)
{
    Variant::Map::const_iterator j = options.find(name);
    if (j == options.end()) {
        return EMPTY_VARIANT;
    } else {
        return j->second;
    }
}

const Variant& getOption(const Address& address, const std::string& name)
{
    return getOption(address.getOptions(), name);
}

bool getReceiverPolicy(const Address& address, const std::string& key)
{
    return in(getOption(address, key), list_of<std::string>(ALWAYS)(RECEIVER));
}

bool getSenderPolicy(const Address& address, const std::string& key)
{
    return in(getOption(address, key), list_of<std::string>(ALWAYS)(SENDER));
}

struct Opt
{
    Opt(const Address& address);
    Opt(const Variant::Map& base);
    Opt& operator/(const std::string& name);
    operator bool() const;
    operator std::string() const;
    std::string str() const;
    bool asBool(bool defaultValue) const;
    const Variant::List& asList() const;
    void collect(qpid::framing::FieldTable& args) const;
    bool hasKey(const std::string&) const;

    const Variant::Map* options;
    const Variant* value;
};

Opt::Opt(const Address& address) : options(&(address.getOptions())), value(0) {}
Opt::Opt(const Variant::Map& base) : options(&base), value(0) {}
Opt& Opt::operator/(const std::string& name)
{
    if (options) {
        Variant::Map::const_iterator j = options->find(name);
        if (j == options->end()) {
            value = 0;
            options = 0;
        } else {
            value = &(j->second);
            if (value->getType() == VAR_MAP) options = &(value->asMap());
            else options = 0;
        }
    }
    return *this;
}


Opt::operator bool() const
{
    return value && !value->isVoid() && value->asBool();
}

Opt::operator std::string() const
{
    return str();
}

bool Opt::asBool(bool defaultValue) const
{
    if (value) return value->asBool();
    else return defaultValue;
}

std::string Opt::str() const
{
    if (value) return value->asString();
    else return EMPTY_STRING;
}

const Variant::List& Opt::asList() const
{
    if (value) return value->asList();
    else return EMPTY_LIST;
}

void Opt::collect(qpid::framing::FieldTable& args) const
{
    if (value) {
        translate(value->asMap(), args);
    }
}
bool Opt::hasKey(const std::string& key) const
{
    if (value) {
        Variant::Map::const_iterator i = value->asMap().find(key);
        return i != value->asMap().end();
    } else {
        return false;
    }
}

bool AddressResolution::is_unreliable(const Address& address)
{

    return in((Opt(address)/LINK/RELIABILITY).str(),
              list_of<std::string>(UNRELIABLE)(AT_MOST_ONCE));
}

bool AddressResolution::is_reliable(const Address& address)
{
    return in((Opt(address)/LINK/RELIABILITY).str(),
              list_of<std::string>(AT_LEAST_ONCE)(EXACTLY_ONCE));
}

std::string checkAddressType(qpid::client::Session session, const Address& address)
{
    verifier.verify(address);
    if (address.getName().empty()) {
        throw MalformedAddress("Name cannot be null");
    }
    std::string type = (Opt(address)/NODE/TYPE).str();
    if (type.empty()) {
        ExchangeBoundResult result = session.exchangeBound(arg::exchange=address.getName(), arg::queue=address.getName());
        if (result.getQueueNotFound() && result.getExchangeNotFound()) {
            //neither a queue nor an exchange exists with that name; treat it as a queue
            type = QUEUE_ADDRESS;
        } else if (result.getExchangeNotFound()) {
            //name refers to a queue
            type = QUEUE_ADDRESS;
        } else if (result.getQueueNotFound()) {
            //name refers to an exchange
            type = TOPIC_ADDRESS;
        } else {
            //both a queue and exchange exist for that name
            throw ResolutionError("Ambiguous address, please specify queue or topic as node type");
        }
    }
    return type;
}

std::auto_ptr<MessageSource> AddressResolution::resolveSource(qpid::client::Session session,
                                                              const Address& address)
{
    std::string type = checkAddressType(session, address);
    if (type == TOPIC_ADDRESS) {
        std::string exchangeType = sync(session).exchangeQuery(address.getName()).getType();
        std::auto_ptr<MessageSource> source(new Subscription(address, exchangeType));
        QPID_LOG(debug, "treating source address as topic: " << address);
        return source;
    } else if (type == QUEUE_ADDRESS) {
        std::auto_ptr<MessageSource> source(new QueueSource(address));
        QPID_LOG(debug, "treating source address as queue: " << address);
        return source;
    } else {
        throw ResolutionError("Unrecognised type: " + type);
    }
}


std::auto_ptr<MessageSink> AddressResolution::resolveSink(qpid::client::Session session,
                                                          const qpid::messaging::Address& address)
{
    std::string type = checkAddressType(session, address);
    if (type == TOPIC_ADDRESS) {
        std::auto_ptr<MessageSink> sink(new ExchangeSink(address));
        QPID_LOG(debug, "treating target address as topic: " << address);
        return sink;
    } else if (type == QUEUE_ADDRESS) {
        std::auto_ptr<MessageSink> sink(new QueueSink(address));
        QPID_LOG(debug, "treating target address as queue: " << address);
        return sink;
    } else {
        throw ResolutionError("Unrecognised type: " + type);
    }
}

bool isBrowse(const Address& address)
{
    const Variant& mode = getOption(address, MODE);
    if (!mode.isVoid()) {
        std::string value = mode.asString();
        if (value == BROWSE) return true;
        else if (value != CONSUME) throw ResolutionError("Invalid mode");
    }
    return false;
}

QueueSource::QueueSource(const Address& address) :
    Queue(address),
    acquireMode(isBrowse(address) ? ACQUIRE_MODE_NOT_ACQUIRED : ACQUIRE_MODE_PRE_ACQUIRED),
    //since this client does not provide any means by which an
    //unacquired message can be acquired, there is no value in an
    //explicit accept
    acceptMode(acquireMode == ACQUIRE_MODE_NOT_ACQUIRED || AddressResolution::is_unreliable(address) ? ACCEPT_MODE_NONE : ACCEPT_MODE_EXPLICIT),
    exclusive(false)
{
    exclusive = Opt(address)/LINK/X_SUBSCRIBE/EXCLUSIVE;
    (Opt(address)/LINK/X_SUBSCRIBE/ARGUMENTS).collect(options);
    std::string selector = Opt(address)/LINK/SELECTOR;
    if (!selector.empty()) options.setString(APACHE_SELECTOR, selector);
}

void QueueSource::subscribe(qpid::client::AsyncSession& session, const std::string& destination)
{
    checkCreate(session, FOR_RECEIVER);
    checkAssert(session, FOR_RECEIVER);
    linkBindings.bind(session);
    session.messageSubscribe(arg::queue=name,
                             arg::destination=destination,
                             arg::acceptMode=acceptMode,
                             arg::acquireMode=acquireMode,
                             arg::exclusive=exclusive,
                             arg::arguments=options);
}

void QueueSource::cancel(qpid::client::AsyncSession& session, const std::string& destination)
{
    linkBindings.unbind(session);
    session.messageCancel(destination);
    checkDelete(session, FOR_RECEIVER);
}

std::string Subscription::getSubscriptionName(const std::string& base, const std::string& name)
{
    if (name.empty()) {
        return (boost::format("%1%_%2%") % base % Uuid(true).str()).str();
    } else {
        return name;
    }
}

Subscription::Subscription(const Address& address, const std::string& type)
    : Exchange(address),
      queue(getSubscriptionName(name, (Opt(address)/LINK/NAME).str())),
      durable(Opt(address)/LINK/DURABLE),
      //if the link is durable, then assume it is also reliable unless explicitly stated otherwise
      //if not assume it is unreliable unless explicitly stated otherwise
      reliable(durable ? !AddressResolution::is_unreliable(address) : AddressResolution::is_reliable(address)),
      actualType(type.empty() ? (specifiedType.empty() ? TOPIC_EXCHANGE : specifiedType) : type),
      exclusiveQueue((Opt(address)/LINK/X_DECLARE/EXCLUSIVE).asBool(true)),
      autoDeleteQueue((Opt(address)/LINK/X_DECLARE/AUTO_DELETE).asBool(!(durable || reliable))),
      exclusiveSubscription((Opt(address)/LINK/X_SUBSCRIBE/EXCLUSIVE).asBool(exclusiveQueue)),
      alternateExchange((Opt(address)/LINK/X_DECLARE/ALTERNATE_EXCHANGE).str())
{

    if ((Opt(address)/LINK).hasKey(TIMEOUT)) {
        const Variant* timeout = (Opt(address)/LINK/TIMEOUT).value;
        if (timeout->asUint32()) queueOptions.setInt("qpid.auto_delete_timeout", timeout->asUint32());
    } else if (durable && !AddressResolution::is_reliable(address) && !(Opt(address)/LINK/X_DECLARE).hasKey(AUTO_DELETE)) {
        //if durable, not explicitly reliable, and auto-delete not
        //explicitly set, then set a non-zero default for the
        //autodelete timeout
        queueOptions.setInt("qpid.auto_delete_timeout", 2*60);
    }
    (Opt(address)/LINK/X_DECLARE/ARGUMENTS).collect(queueOptions);
    (Opt(address)/LINK/X_SUBSCRIBE/ARGUMENTS).collect(subscriptionOptions);
    std::string selector = Opt(address)/LINK/SELECTOR;
    if (!selector.empty()) queueOptions.setString(QPID_FILTER, selector);

    if (!address.getSubject().empty()) bindSubject(address.getSubject());
    else if (linkBindings.empty()) bindAll();
}

void Subscription::bindSubject(const std::string& subject)
{
    if (actualType == HEADERS_EXCHANGE) {
        Binding b(name, queue, subject);
        b.arguments.setString("qpid.subject", subject);
        b.arguments.setString("x-match", "all");
        bindings.push_back(b);
    } else if (actualType == XML_EXCHANGE) {
        Binding b(name, queue, subject);
        std::string query = (boost::format("declare variable $qpid.subject external; $qpid.subject = '%1%'")
                                           % subject).str();
        b.arguments.setString("xquery", query);
        bindings.push_back(b);
    } else {
        //Note: the fanout exchange doesn't support any filtering, so
        //the subject is ignored in that case
        add(name, subject);
    }
}

void Subscription::bindAll()
{
    if (actualType == TOPIC_EXCHANGE) {
        add(name, WILDCARD_ANY);
    } else if (actualType == FANOUT_EXCHANGE) {
        add(name, queue);
    } else if (actualType == HEADERS_EXCHANGE) {
        Binding b(name, queue, "match-all");
        b.arguments.setString("x-match", "all");
        bindings.push_back(b);
    } else if (actualType == XML_EXCHANGE) {
        Binding b(name, queue, EMPTY_STRING);
        b.arguments.setString("xquery", "true()");
        bindings.push_back(b);
    } else {
        add(name, EMPTY_STRING);
    }
}

void Subscription::add(const std::string& exchange, const std::string& key)
{
    bindings.push_back(Binding(exchange, queue, key));
}

void Subscription::subscribe(qpid::client::AsyncSession& session, const std::string& destination)
{
    //create exchange if required and specified by policy:
    checkCreate(session, FOR_RECEIVER);
    checkAssert(session, FOR_RECEIVER);

    //create subscription queue:
    session.queueDeclare(arg::queue=queue, arg::exclusive=exclusiveQueue,
                         arg::autoDelete=autoDeleteQueue, arg::durable=durable,
                         arg::alternateExchange=alternateExchange,
                         arg::arguments=queueOptions);
    //'default' binding:
    bindings.bind(session);
    //any explicit bindings:
    linkBindings.setDefaultQueue(queue);
    linkBindings.bind(session);
    //subscribe to subscription queue:
    AcceptMode accept = reliable ? ACCEPT_MODE_EXPLICIT : ACCEPT_MODE_NONE;
    session.messageSubscribe(arg::queue=queue, arg::destination=destination,
                             arg::exclusive=exclusiveSubscription, arg::acceptMode=accept, arg::arguments=subscriptionOptions);
}

void Subscription::cancel(qpid::client::AsyncSession& session, const std::string& destination)
{
    linkBindings.unbind(session);
    session.messageCancel(destination);
    if (exclusiveQueue) session.queueDelete(arg::queue=queue, arg::ifUnused=true);
    checkDelete(session, FOR_RECEIVER);
}

ExchangeSink::ExchangeSink(const Address& address) : Exchange(address) {}

void ExchangeSink::declare(qpid::client::AsyncSession& session, const std::string&)
{
    checkCreate(session, FOR_SENDER);
    checkAssert(session, FOR_SENDER);
    linkBindings.bind(session);
}

void ExchangeSink::send(qpid::client::AsyncSession& session, const std::string&, OutgoingMessage& m)
{
    m.send(session, name, m.getSubject());
}

void ExchangeSink::cancel(qpid::client::AsyncSession& session, const std::string&)
{
    linkBindings.unbind(session);
    checkDelete(session, FOR_SENDER);
}

QueueSink::QueueSink(const Address& address) : Queue(address) {}

void QueueSink::declare(qpid::client::AsyncSession& session, const std::string&)
{
    checkCreate(session, FOR_SENDER);
    checkAssert(session, FOR_SENDER);
    linkBindings.bind(session);
}
void QueueSink::send(qpid::client::AsyncSession& session, const std::string&, OutgoingMessage& m)
{
    m.send(session, name);
}

void QueueSink::cancel(qpid::client::AsyncSession& session, const std::string&)
{
    linkBindings.unbind(session);
    checkDelete(session, FOR_SENDER);
}

Address AddressResolution::convert(const qpid::framing::ReplyTo& rt)
{
    Address address;
    if (rt.getExchange().empty()) {//if default exchange, treat as queue
        if (!rt.getRoutingKey().empty()) {
            address.setName(rt.getRoutingKey());
            address.setType(QUEUE_ADDRESS);
        }
    } else {
        address.setName(rt.getExchange());
        address.setSubject(rt.getRoutingKey());
        address.setType(TOPIC_ADDRESS);
    }
    return address;
}

qpid::framing::ReplyTo AddressResolution::convert(const Address& address)
{
    if (address.getType() == QUEUE_ADDRESS || address.getType().empty()) {
        return ReplyTo(EMPTY_STRING, address.getName());
    } else if (address.getType() == TOPIC_ADDRESS) {
        return ReplyTo(address.getName(), address.getSubject());
    } else {
        QPID_LOG(notice, "Unrecognised type for reply-to: " << address.getType());
        return ReplyTo(EMPTY_STRING, address.getName());//treat as queue
    }
}

bool isQueue(qpid::client::Session session, const qpid::messaging::Address& address)
{
    return address.getType() == QUEUE_ADDRESS ||
        (address.getType().empty() && session.queueQuery(address.getName()).getQueue() == address.getName());
}

bool isTopic(qpid::client::Session session, const qpid::messaging::Address& address)
{
    if (address.getType().empty()) {
        return !session.exchangeQuery(address.getName()).getNotFound();
    } else if (address.getType() == TOPIC_ADDRESS) {
        return true;
    } else {
        return false;
    }
}

Node::Node(const Address& address) : name(address.getName()),
                                     createPolicy(getOption(address, CREATE)),
                                     assertPolicy(getOption(address, ASSERT)),
                                     deletePolicy(getOption(address, DELETE))
{
    nodeBindings.add((Opt(address)/NODE/X_BINDINGS).asList());
    linkBindings.add((Opt(address)/LINK/X_BINDINGS).asList());
}

Queue::Queue(const Address& a) : Node(a),
                                 durable(Opt(a)/NODE/DURABLE),
                                 autoDelete(Opt(a)/NODE/X_DECLARE/AUTO_DELETE),
                                 exclusive(Opt(a)/NODE/X_DECLARE/EXCLUSIVE),
                                 alternateExchange((Opt(a)/NODE/X_DECLARE/ALTERNATE_EXCHANGE).str())
{
    (Opt(a)/NODE/X_DECLARE/ARGUMENTS).collect(arguments);
    nodeBindings.setDefaultQueue(name);
    linkBindings.setDefaultQueue(name);
    if (qpid::messaging::AddressImpl::isTemporary(a) && createPolicy.isVoid()) {
        createPolicy = "always";
        Opt specified = Opt(a)/NODE/X_DECLARE;
        if (!specified.hasKey(AUTO_DELETE)) autoDelete = true;
        if (!specified.hasKey(EXCLUSIVE)) exclusive = true;
    }
}

void Queue::checkCreate(qpid::client::AsyncSession& session, CheckMode mode)
{
    if (enabled(createPolicy, mode)) {
        QPID_LOG(debug, "Auto-creating queue '" << name << "'");
        try {
            session.queueDeclare(arg::queue=name,
                                 arg::durable=durable,
                                 arg::autoDelete=autoDelete,
                                 arg::exclusive=exclusive,
                                 arg::alternateExchange=alternateExchange,
                                 arg::arguments=arguments);
            nodeBindings.bind(session);
            session.sync();
        } catch (const qpid::framing::ResourceLockedException& e) {
            throw ResolutionError((boost::format("Creation failed for queue %1%; %2%") % name % e.what()).str());
        } catch (const qpid::framing::NotAllowedException& e) {
            throw ResolutionError((boost::format("Creation failed for queue %1%; %2%") % name % e.what()).str());
        } catch (const qpid::framing::NotFoundException& e) {//may be thrown when creating bindings
            throw ResolutionError((boost::format("Creation failed for queue %1%; %2%") % name % e.what()).str());
        }
    } else {
        try {
            sync(session).queueDeclare(arg::queue=name, arg::passive=true);
        } catch (const qpid::framing::NotFoundException& /*e*/) {
            throw NotFound((boost::format("Queue %1% does not exist") % name).str());
        }
    }
}

void Queue::checkDelete(qpid::client::AsyncSession& session, CheckMode mode)
{
    //Note: queue-delete will cause a session exception if the queue
    //does not exist, the query here prevents obvious cases of this
    //but there is a race whenever two deletions are made concurrently
    //so careful use of the delete policy is recommended at present
    if (enabled(deletePolicy, mode) && sync(session).queueQuery(name).getQueue() == name) {
        QPID_LOG(debug, "Auto-deleting queue '" << name << "'");
        sync(session).queueDelete(arg::queue=name);
    }
}

void Queue::checkAssert(qpid::client::AsyncSession& session, CheckMode mode)
{
    if (enabled(assertPolicy, mode)) {
        QueueQueryResult result = sync(session).queueQuery(name);
        if (result.getQueue() != name) {
            throw NotFound((boost::format("Queue not found: %1%") % name).str());
        } else {
            if (durable && !result.getDurable()) {
                throw AssertionFailed((boost::format("Queue not durable: %1%") % name).str());
            }
            if (autoDelete && !result.getAutoDelete()) {
                throw AssertionFailed((boost::format("Queue not set to auto-delete: %1%") % name).str());
            }
            if (exclusive && !result.getExclusive()) {
                throw AssertionFailed((boost::format("Queue not exclusive: %1%") % name).str());
            }
            if (!alternateExchange.empty() && result.getAlternateExchange() != alternateExchange) {
                throw AssertionFailed((boost::format("Alternate exchange does not match for %1%, expected %2%, got %3%")
                                      % name % alternateExchange % result.getAlternateExchange()).str());
            }
            for (FieldTable::ValueMap::const_iterator i = arguments.begin(); i != arguments.end(); ++i) {
                FieldTable::ValuePtr v = result.getArguments().get(i->first);
                if (!v) {
                    throw AssertionFailed((boost::format("Option %1% not set for %2%") % i->first % name).str());
                } else if (!areEquivalent(*i->second, *v)) {
                    throw AssertionFailed((boost::format("Option %1% does not match for %2%, expected %3%, got %4%")
                                          % i->first % name % *(i->second) % *v).str());
                }
            }
            nodeBindings.check(session);
        }
    }
}

Exchange::Exchange(const Address& a) : Node(a),
                                       specifiedType((Opt(a)/NODE/X_DECLARE/TYPE).str()),
                                       durable(Opt(a)/NODE/DURABLE),
                                       autoDelete(Opt(a)/NODE/X_DECLARE/AUTO_DELETE),
                                       alternateExchange((Opt(a)/NODE/X_DECLARE/ALTERNATE_EXCHANGE).str())
{
    (Opt(a)/NODE/X_DECLARE/ARGUMENTS).collect(arguments);
    nodeBindings.setDefaultExchange(name);
    linkBindings.setDefaultExchange(name);
    if (qpid::messaging::AddressImpl::isTemporary(a) && createPolicy.isVoid()) {
        createPolicy = "always";
        if (!(Opt(a)/NODE/X_DECLARE).hasKey(AUTO_DELETE)) autoDelete = true;
    }
}

bool Exchange::isReservedName()
{
    return name.find(PREFIX_AMQ) != std::string::npos || name.find(PREFIX_QPID) != std::string::npos;
}

void Exchange::checkCreate(qpid::client::AsyncSession& session, CheckMode mode)
{
    if (enabled(createPolicy, mode)) {
        try {
            if (isReservedName()) {
                try {
                    sync(session).exchangeDeclare(arg::exchange=name, arg::passive=true);
                } catch (const qpid::framing::NotFoundException& /*e*/) {
                    throw ResolutionError((boost::format("Cannot create exchange %1%; names beginning with \"amq.\" or \"qpid.\" are reserved.") % name).str());
                }

            } else {
                std::string type = specifiedType;
                if (type.empty()) type = TOPIC_EXCHANGE;
                session.exchangeDeclare(arg::exchange=name,
                                        arg::type=type,
                                        arg::durable=durable,
                                        arg::autoDelete=autoDelete,
                                        arg::alternateExchange=alternateExchange,
                                        arg::arguments=arguments);
            }
            nodeBindings.bind(session);
            session.sync();
        } catch (const qpid::framing::NotAllowedException& e) {
            throw ResolutionError((boost::format("Create failed for exchange %1%; %2%") % name % e.what()).str());
        } catch (const qpid::framing::NotFoundException& e) {//can be caused when creating bindings
            throw ResolutionError((boost::format("Create failed for exchange %1%; %2%") % name % e.what()).str());
        }
    } else {
        try {
            sync(session).exchangeDeclare(arg::exchange=name, arg::passive=true);
        } catch (const qpid::framing::NotFoundException& /*e*/) {
            throw NotFound((boost::format("Exchange %1% does not exist") % name).str());
        }
    }
}

void Exchange::checkDelete(qpid::client::AsyncSession& session, CheckMode mode)
{
    //Note: exchange-delete will cause a session exception if the
    //exchange does not exist, the query here prevents obvious cases
    //of this but there is a race whenever two deletions are made
    //concurrently so careful use of the delete policy is recommended
    //at present
    if (enabled(deletePolicy, mode) && !sync(session).exchangeQuery(name).getNotFound()) {
        sync(session).exchangeDelete(arg::exchange=name);
    }
}

void Exchange::checkAssert(qpid::client::AsyncSession& session, CheckMode mode)
{
    if (enabled(assertPolicy, mode)) {
        ExchangeQueryResult result = sync(session).exchangeQuery(name);
        if (result.getNotFound()) {
            throw NotFound((boost::format("Exchange not found: %1%") % name).str());
        } else {
            if (specifiedType.size() && result.getType() != specifiedType) {
                throw AssertionFailed((boost::format("Exchange %1% is of incorrect type, expected %2% but got %3%")
                                      % name % specifiedType % result.getType()).str());
            }
            if (durable && !result.getDurable()) {
                throw AssertionFailed((boost::format("Exchange not durable: %1%") % name).str());
            }
            //Note: Can't check auto-delete or alternate-exchange via
            //exchange-query-result as these are not returned
            //TODO: could use a passive declare to check alternate-exchange
            for (FieldTable::ValueMap::const_iterator i = arguments.begin(); i != arguments.end(); ++i) {
                FieldTable::ValuePtr v = result.getArguments().get(i->first);
                if (!v) {
                    throw AssertionFailed((boost::format("Option %1% not set for %2%") % i->first % name).str());
                } else if (!areEquivalent(*i->second, *v)) {
                    throw AssertionFailed((boost::format("Option %1% does not match for %2%, expected %3%, got %4%")
                                          % i->first % name % *(i->second) % *v).str());
                }
            }
            nodeBindings.check(session);
        }
    }
}

Binding::Binding(const Variant::Map& b) :
    exchange((Opt(b)/EXCHANGE).str()),
    queue((Opt(b)/QUEUE).str()),
    key((Opt(b)/KEY).str())
{
    (Opt(b)/ARGUMENTS).collect(arguments);
}

Binding::Binding(const std::string& e, const std::string& q, const std::string& k) : exchange(e), queue(q), key(k) {}


void Bindings::add(const Variant::List& list)
{
    for (Variant::List::const_iterator i = list.begin(); i != list.end(); ++i) {
        push_back(Binding(i->asMap()));
    }
}

void Bindings::setDefaultExchange(const std::string& exchange)
{
    for (Bindings::iterator i = begin(); i != end(); ++i) {
        if (i->exchange.empty()) i->exchange = exchange;
    }
}

void Bindings::setDefaultQueue(const std::string& queue)
{
    for (Bindings::iterator i = begin(); i != end(); ++i) {
        if (i->queue.empty()) i->queue = queue;
    }
}

void Bindings::bind(qpid::client::AsyncSession& session)
{
    for (Bindings::const_iterator i = begin(); i != end(); ++i) {
        session.exchangeBind(arg::queue=i->queue,
                             arg::exchange=i->exchange,
                             arg::bindingKey=i->key,
                             arg::arguments=i->arguments);
    }
}

void Bindings::unbind(qpid::client::AsyncSession& session)
{
    for (Bindings::const_iterator i = begin(); i != end(); ++i) {
        session.exchangeUnbind(arg::queue=i->queue,
                               arg::exchange=i->exchange,
                               arg::bindingKey=i->key);
    }
}

void Bindings::check(qpid::client::AsyncSession& session)
{
    for (Bindings::const_iterator i = begin(); i != end(); ++i) {
        ExchangeBoundResult result = sync(session).exchangeBound(arg::queue=i->queue,
                                                                 arg::exchange=i->exchange,
                                                                 arg::bindingKey=i->key);
        if (result.getQueueNotMatched() || result.getKeyNotMatched()) {
            throw AssertionFailed((boost::format("No such binding [exchange=%1%, queue=%2%, key=%3%]")
                                  % i->exchange % i->queue % i->key).str());
        }
    }
}

bool Node::enabled(const Variant& policy, CheckMode mode)
{
    bool result = false;
    switch (mode) {
      case FOR_RECEIVER:
        result = in(policy, RECEIVER_MODES);
        break;
      case FOR_SENDER:
        result = in(policy, SENDER_MODES);
        break;
    }
    return result;
}

bool Node::createEnabled(const Address& address, CheckMode mode)
{
    const Variant& policy = getOption(address, CREATE);
    return enabled(policy, mode);
}

void Node::convert(const Variant& options, FieldTable& arguments)
{
    if (!options.isVoid()) {
        translate(options.asMap(), arguments);
    }
}
std::vector<std::string> Node::RECEIVER_MODES = list_of<std::string>(ALWAYS) (RECEIVER);
std::vector<std::string> Node::SENDER_MODES = list_of<std::string>(ALWAYS) (SENDER);

Verifier::Verifier()
{
    defined[CREATE] = true;
    defined[ASSERT] = true;
    defined[DELETE] = true;
    defined[MODE] = true;
    Variant::Map node;
    node[TYPE] = true;
    node[DURABLE] = true;
    node[X_DECLARE] = true;
    node[X_BINDINGS] = true;
    defined[NODE] = node;
    Variant::Map link;
    link[NAME] = true;
    link[DURABLE] = true;
    link[RELIABILITY] = true;
    link[TIMEOUT] = true;
    link[X_SUBSCRIBE] = true;
    link[X_DECLARE] = true;
    link[X_BINDINGS] = true;
    link[SELECTOR] = true;
    defined[LINK] = link;
}
void Verifier::verify(const Address& address) const
{
    verify(defined, address.getOptions());
}

void Verifier::verify(const Variant::Map& allowed, const Variant::Map& actual) const
{
    for (Variant::Map::const_iterator i = actual.begin(); i != actual.end(); ++i) {
        Variant::Map::const_iterator option = allowed.find(i->first);
        if (option == allowed.end()) {
            throw AddressError((boost::format("Unrecognised option: %1%") % i->first).str());
        } else if (option->second.getType() == qpid::types::VAR_MAP) {
            verify(option->second.asMap(), i->second.asMap());
        }
    }
}

}}} // namespace qpid::client::amqp0_10
