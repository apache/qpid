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
#include "qpid/client/amqp0_10/Codecs.h"
#include "qpid/client/amqp0_10/MessageSource.h"
#include "qpid/client/amqp0_10/MessageSink.h"
#include "qpid/messaging/Address.h"
#include "qpid/messaging/Filter.h"
#include "qpid/messaging/Message.h"
#include "qpid/Exception.h"
#include "qpid/log/Statement.h"
#include "qpid/framing/enum.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/framing/ReplyTo.h"
#include "qpid/framing/reply_exceptions.h"

namespace qpid {
namespace client {
namespace amqp0_10 {

using qpid::Exception;
using qpid::messaging::Address;
using qpid::messaging::Filter;
using qpid::messaging::Variant;
using qpid::framing::FieldTable;
using qpid::framing::ReplyTo;
using namespace qpid::framing::message;


namespace{
const Variant EMPTY_VARIANT;
const FieldTable EMPTY_FIELD_TABLE;
const std::string EMPTY_STRING;

//option names
const std::string BROWSE("browse");
const std::string EXCLUSIVE("exclusive");
const std::string MODE("mode");
const std::string NAME("name");
const std::string UNACKNOWLEDGED("unacknowledged");

const std::string QUEUE_ADDRESS("queue");
const std::string TOPIC_ADDRESS("topic");
const std::string TOPIC_ADDRESS_AND_SUBJECT("topic+");
const std::string DIVIDER("/");

const std::string SIMPLE_SUBSCRIPTION("simple");
const std::string RELIABLE_SUBSCRIPTION("reliable");
const std::string DURABLE_SUBSCRIPTION("durable");
}

class QueueSource : public MessageSource
{
  public:
    QueueSource(const std::string& name, AcceptMode=ACCEPT_MODE_EXPLICIT, AcquireMode=ACQUIRE_MODE_PRE_ACQUIRED, 
                bool exclusive = false, const FieldTable& options = EMPTY_FIELD_TABLE);
    void subscribe(qpid::client::AsyncSession& session, const std::string& destination);
    void cancel(qpid::client::AsyncSession& session, const std::string& destination);
  private:
    const std::string name;
    const AcceptMode acceptMode;
    const AcquireMode acquireMode;
    const bool exclusive;
    const FieldTable options;
};

class Subscription : public MessageSource
{
  public:
    enum SubscriptionMode {SIMPLE, RELIABLE, DURABLE};

    Subscription(const std::string& name, SubscriptionMode mode = SIMPLE,
                 const FieldTable& queueOptions = EMPTY_FIELD_TABLE, const FieldTable& subscriptionOptions = EMPTY_FIELD_TABLE);
    void add(const std::string& exchange, const std::string& key, const FieldTable& options = EMPTY_FIELD_TABLE);
    void subscribe(qpid::client::AsyncSession& session, const std::string& destination);
    void cancel(qpid::client::AsyncSession& session, const std::string& destination);

    static SubscriptionMode getMode(const std::string& mode);
  private:
    struct Binding
    {
        Binding(const std::string& exchange, const std::string& key, const FieldTable& options = EMPTY_FIELD_TABLE);

        std::string exchange;
        std::string key;
        FieldTable options;
    };

    typedef std::vector<Binding> Bindings;

    const std::string name;
    const bool autoDelete;
    const bool durable;
    const FieldTable queueOptions;
    const FieldTable subscriptionOptions;
    Bindings bindings;
    std::string queue;
};

class Exchange : public MessageSink
{
  public:
    Exchange(const std::string& name, const std::string& defaultSubject = EMPTY_STRING, 
             bool passive = true, const std::string& type = EMPTY_STRING, bool durable = false, 
             const FieldTable& options = EMPTY_FIELD_TABLE);
    void declare(qpid::client::AsyncSession& session, const std::string& name);
    void send(qpid::client::AsyncSession& session, const std::string& name, qpid::messaging::Message& message);
    void cancel(qpid::client::AsyncSession& session, const std::string& name);
  private:
    const std::string name;
    const std::string defaultSubject;
    const bool passive;
    const std::string type;
    const bool durable;
    const FieldTable options;
};

class QueueSink : public MessageSink
{
  public:
    QueueSink(const std::string& name, bool passive=true, bool exclusive=false, 
              bool autoDelete=false, bool durable=false, const FieldTable& options = EMPTY_FIELD_TABLE);
    void declare(qpid::client::AsyncSession& session, const std::string& name);
    void send(qpid::client::AsyncSession& session, const std::string& name, qpid::messaging::Message& message);
    void cancel(qpid::client::AsyncSession& session, const std::string& name);
  private:
    const std::string name;
    const bool passive;
    const bool exclusive;
    const bool autoDelete;
    const bool durable;
    const FieldTable options;
};


bool isQueue(qpid::client::Session session, const qpid::messaging::Address& address);
bool isTopic(qpid::client::Session session, const qpid::messaging::Address& address, std::string& subject);

const Variant& getOption(const std::string& key, const Variant::Map& options)
{
    Variant::Map::const_iterator i = options.find(key);
    if (i == options.end()) return EMPTY_VARIANT;
    else return i->second;
}

std::auto_ptr<MessageSource> AddressResolution::resolveSource(qpid::client::Session session,
                                                              const Address& address, 
                                                              const Filter* filter,
                                                              const Variant::Map& options)
{
    //TODO: handle case where there exists a queue and an exchange of
    //the same name (hence an unqualified address is ambiguous)
    
    //TODO: make sure specified address type gives sane error message
    //if it does npt match the configuration on server

    if (isQueue(session, address)) {
        //TODO: support auto-created queue as source, if requested by specific option

        AcceptMode accept = getOption(UNACKNOWLEDGED, options).asBool() ? ACCEPT_MODE_NONE : ACCEPT_MODE_EXPLICIT;
        AcquireMode acquire = getOption(BROWSE, options).asBool() ? ACQUIRE_MODE_NOT_ACQUIRED : ACQUIRE_MODE_PRE_ACQUIRED;
        bool exclusive = getOption(EXCLUSIVE, options).asBool();
        FieldTable arguments;
        //TODO: extract subscribe arguments from options (e.g. either
        //filter out already processed keys and send the rest, or have
        //a nested map)

        std::auto_ptr<MessageSource> source = 
            std::auto_ptr<MessageSource>(new QueueSource(address.value, accept, acquire, exclusive, arguments));
        return source;
    } else {
        //TODO: extract queue options (e.g. no-local) and subscription options (e.g. less important)
        std::auto_ptr<Subscription> bindings = 
            std::auto_ptr<Subscription>(new Subscription(getOption(NAME, options).asString(), 
                                                         Subscription::getMode(getOption(MODE, options).asString())));

        qpid::framing::ExchangeQueryResult result = session.exchangeQuery(address.value);
        if (result.getNotFound()) {
            throw qpid::framing::NotFoundException(QPID_MSG("Address not known: " << address));
        } else if (result.getType() == "topic") {
            if (filter) {
                if (filter->type != Filter::WILDCARD) {
                    throw qpid::framing::NotImplementedException(
                        QPID_MSG("Filters of type " << filter->type << " not supported by address " << address));
                    
                }
                for (std::vector<std::string>::const_iterator i = filter->patterns.begin(); i != filter->patterns.end(); i++) {
                    bindings->add(address.value, *i, qpid::framing::FieldTable());
                }
            } else {
                //default is to receive all messages
                bindings->add(address.value, "*", qpid::framing::FieldTable());
            }
        } else if (result.getType() == "fanout") {
            if (filter) {
                throw qpid::framing::NotImplementedException(QPID_MSG("Filters are not supported by address " << address));                
            }            
            bindings->add(address.value, address.value, qpid::framing::FieldTable());
        } else if (result.getType() == "direct") {
            //TODO: ????
        } else {
            //TODO: xml and headers exchanges
            throw qpid::framing::NotImplementedException(QPID_MSG("Address type not recognised for " << address));                
        }
        std::auto_ptr<MessageSource> source = std::auto_ptr<MessageSource>(bindings.release());
        return source;
    }
}


std::auto_ptr<MessageSink> AddressResolution::resolveSink(qpid::client::Session session,
                                                          const qpid::messaging::Address& address, 
                                                          const qpid::messaging::Variant::Map& /*options*/)
{
    std::auto_ptr<MessageSink> sink;
    if (isQueue(session, address)) {
        //TODO: support for auto-created queues as sink
        sink = std::auto_ptr<MessageSink>(new QueueSink(address.value));
    } else {
        std::string subject;
        if (isTopic(session, address, subject)) {
            //TODO: support for auto-created exchanges as sink
            sink = std::auto_ptr<MessageSink>(new Exchange(address.value, subject));
        } else {
            if (address.type.empty()) {
                throw qpid::framing::NotFoundException(QPID_MSG("Address not known: " << address));
            } else {
                throw qpid::framing::NotImplementedException(QPID_MSG("Address type not recognised: " << address.type));
            }
        }
    }
    return sink;
}

QueueSource::QueueSource(const std::string& _name, AcceptMode _acceptMode, AcquireMode _acquireMode, bool _exclusive, const FieldTable& _options) :
    name(_name), acceptMode(_acceptMode), acquireMode(_acquireMode), exclusive(_exclusive), options(_options) {}

void QueueSource::subscribe(qpid::client::AsyncSession& session, const std::string& destination)
{
    session.messageSubscribe(arg::queue=name, 
                             arg::destination=destination,
                             arg::acceptMode=acceptMode,
                             arg::acquireMode=acquireMode,
                             arg::exclusive=exclusive,
                             arg::arguments=options);
}

void QueueSource::cancel(qpid::client::AsyncSession& session, const std::string& destination)
{
    session.messageCancel(destination);
}

Subscription::Subscription(const std::string& _name, SubscriptionMode mode, const FieldTable& qOptions, const FieldTable& sOptions)
    : name(_name), autoDelete(mode == SIMPLE), durable(mode == DURABLE), 
      queueOptions(qOptions), subscriptionOptions(sOptions) {}

void Subscription::add(const std::string& exchange, const std::string& key, const FieldTable& options)
{
    bindings.push_back(Binding(exchange, key, options));
}

void Subscription::subscribe(qpid::client::AsyncSession& session, const std::string& destination)
{
    if (name.empty()) {
        //TODO: use same scheme as JMS client for subscription queue name generation?
        queue = session.getId().getName() + destination;
    } else {
        queue = name;
    }
    session.queueDeclare(arg::queue=queue, arg::exclusive=true, 
                         arg::autoDelete=autoDelete, arg::durable=durable, arg::arguments=queueOptions);
    for (Bindings::const_iterator i = bindings.begin(); i != bindings.end(); ++i) {
        session.exchangeBind(arg::queue=queue, arg::exchange=i->exchange, arg::bindingKey=i->key, arg::arguments=i->options);
    }
    AcceptMode accept = autoDelete ? ACCEPT_MODE_NONE : ACCEPT_MODE_EXPLICIT;
    session.messageSubscribe(arg::queue=queue, arg::destination=destination, 
                             arg::exclusive=true, arg::acceptMode=accept, arg::arguments=subscriptionOptions);
}

void Subscription::cancel(qpid::client::AsyncSession& session, const std::string& destination)
{
    session.messageCancel(destination);
    session.queueDelete(arg::queue=queue);
}

Subscription::Binding::Binding(const std::string& e, const std::string& k, const FieldTable& o):
    exchange(e), key(k), options(o) {}

Subscription::SubscriptionMode Subscription::getMode(const std::string& s)
{
    if (s.empty() || s == SIMPLE_SUBSCRIPTION) return SIMPLE;
    else if (s == RELIABLE_SUBSCRIPTION) return RELIABLE;
    else if (s == DURABLE_SUBSCRIPTION) return DURABLE;
    else throw Exception(QPID_MSG("Unrecognised subscription mode: " << s)); 
}

void convert(qpid::messaging::Message& from, qpid::client::Message& to);

Exchange::Exchange(const std::string& _name, const std::string& _defaultSubject, 
         bool _passive, const std::string& _type, bool _durable, const FieldTable& _options) : 
    name(_name), defaultSubject(_defaultSubject), passive(_passive), type(_type), durable(_durable), options(_options) {}

void Exchange::declare(qpid::client::AsyncSession& session, const std::string&)
{
    //TODO: should this really by synchronous? want to get error if not valid...
    if (passive) {
        sync(session).exchangeDeclare(arg::exchange=name, arg::passive=true);
    } else {
        sync(session).exchangeDeclare(arg::exchange=name, arg::type=type, arg::durable=durable, arg::arguments=options);
    }
}

void Exchange::send(qpid::client::AsyncSession& session, const std::string&, qpid::messaging::Message& m)
{
    qpid::client::Message message;
    convert(m, message);
    if (message.getDeliveryProperties().getRoutingKey().empty() && !defaultSubject.empty()) {
        message.getDeliveryProperties().setRoutingKey(defaultSubject);
    }
    session.messageTransfer(arg::destination=name, arg::content=message);
}

void Exchange::cancel(qpid::client::AsyncSession&, const std::string&) {}

QueueSink::QueueSink(const std::string& _name, bool _passive, bool _exclusive, 
                     bool _autoDelete, bool _durable, const FieldTable& _options) :
    name(_name), passive(_passive), exclusive(_exclusive), 
    autoDelete(_autoDelete), durable(_durable), options(_options) {}

void QueueSink::declare(qpid::client::AsyncSession& session, const std::string&)
{
    //TODO: should this really by synchronous?
    if (passive) {
        sync(session).queueDeclare(arg::queue=name, arg::passive=true);
    } else {
        sync(session).queueDeclare(arg::queue=name, arg::exclusive=exclusive, arg::durable=durable, 
                                   arg::autoDelete=autoDelete, arg::arguments=options);
    }
}
void QueueSink::send(qpid::client::AsyncSession& session, const std::string&, qpid::messaging::Message& m)
{
    qpid::client::Message message;
    convert(m, message);
    message.getDeliveryProperties().setRoutingKey(name);
    session.messageTransfer(arg::content=message);
}

void QueueSink::cancel(qpid::client::AsyncSession&, const std::string&) {}

template <class T> void encode(qpid::messaging::Message& from)
{
    T codec;
    from.encode(codec);
    from.setContentType(T::contentType);
}

void translate(const Variant::Map& from, FieldTable& to);//implementation in Codecs.cpp

void convert(qpid::messaging::Message& from, qpid::client::Message& to)
{
    //TODO: need to avoid copying as much as possible
    if (from.getContent().isList()) encode<ListCodec>(from);
    if (from.getContent().isMap())  encode<MapCodec>(from);
    to.setData(from.getBytes());
    to.getDeliveryProperties().setRoutingKey(from.getSubject());
    //TODO: set other delivery properties
    to.getMessageProperties().setContentType(from.getContentType());
    const Address& address = from.getReplyTo();
    if (!address.value.empty()) {
        to.getMessageProperties().setReplyTo(AddressResolution::convert(address));
    }
    translate(from.getHeaders(), to.getMessageProperties().getApplicationHeaders());
    //TODO: set other message properties
}

Address AddressResolution::convert(const qpid::framing::ReplyTo& rt)
{
    if (rt.getExchange().empty()) {
        if (rt.getRoutingKey().empty()) {
            return Address();//empty address
        } else {
            return Address(rt.getRoutingKey(), QUEUE_ADDRESS);
        }
    } else {
        if (rt.getRoutingKey().empty()) {
            return Address(rt.getExchange(), TOPIC_ADDRESS);
        } else {
            return Address(rt.getExchange() + DIVIDER + rt.getRoutingKey(), TOPIC_ADDRESS_AND_SUBJECT);
        }
    }    
}

qpid::framing::ReplyTo AddressResolution::convert(const Address& address)
{
    if (address.type == QUEUE_ADDRESS || address.type.empty()) {
        return ReplyTo(EMPTY_STRING, address.value);
    } else if (address.type == TOPIC_ADDRESS) {
        return ReplyTo(address.value, EMPTY_STRING);
    } else if (address.type == TOPIC_ADDRESS_AND_SUBJECT) {
        //need to split the value
        string::size_type i = address.value.find(DIVIDER);
        if (i != string::npos) {
            std::string exchange = address.value.substr(0, i);
            std::string routingKey;
            if (i+1 < address.value.size()) {
                routingKey = address.value.substr(i+1);
            } 
            return ReplyTo(exchange, routingKey);
        } else {
            return ReplyTo(address.value, EMPTY_STRING);
        }
    } else {
        QPID_LOG(notice, "Unrecognised type for reply-to: " << address.type);
        //treat as queue
        return ReplyTo(EMPTY_STRING, address.value);
    }
}

bool isQueue(qpid::client::Session session, const qpid::messaging::Address& address) 
{
    return address.type == QUEUE_ADDRESS || 
        (address.type.empty() && session.queueQuery(address.value).getQueue() == address.value);
}

bool isTopic(qpid::client::Session session, const qpid::messaging::Address& address, std::string& subject)
{
    if (address.type.empty()) {
        return !session.exchangeQuery(address.value).getNotFound();
    } else if (address.type == TOPIC_ADDRESS) {
        return true;
    } else if (address.type == TOPIC_ADDRESS_AND_SUBJECT) {
        string::size_type i = address.value.find(DIVIDER);
        if (i != string::npos) {
            std::string exchange = address.value.substr(0, i);
            if (i+1 < address.value.size()) {
                subject = address.value.substr(i+1);
            } 
        }
        return true;
    } else {
        return false;
    }
}


}}} // namespace qpid::client::amqp0_10
