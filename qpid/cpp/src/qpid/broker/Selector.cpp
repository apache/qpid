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

#include "qpid/broker/Selector.h"

#include "qpid/amqp/CharSequence.h"
#include "qpid/amqp/MapHandler.h"
#include "qpid/amqp/MessageId.h"
#include "qpid/broker/Message.h"
#include "qpid/broker/SelectorExpression.h"
#include "qpid/broker/SelectorValue.h"
#include "qpid/log/Statement.h"
#include "qpid/types/Variant.h"

#include <map>
#include <stdexcept>
#include <string>
#include <sstream>
#include "qpid/sys/unordered_map.h"

#include <boost/lexical_cast.hpp>
#include <boost/ptr_container/ptr_vector.hpp>

namespace qpid {
namespace broker {

using std::string;
using qpid::sys::unordered_map;
using qpid::amqp::CharSequence;
using qpid::amqp::MapHandler;
using qpid::amqp::MessageId;

/**
 * Identifier  (amqp.)  | JMS...       | amqp 1.0 equivalent
 * durable              |              | durable              header section
 * delivery_mode        | DeliveryMode | [durable ? 'PERSISTENT' : 'NON_PERSISTENT'] (computed value)
 * priority             | Priority     | priority             header section
 * delivery_count       |              | delivery-count       header section
 * redelivered          |[Redelivered] | (delivery_count>0)  (computed value)
 * subject              | Type         | subject              properties section
 * correlation_id       | CorrelationID| correlation-id       properties section
 * to                   |[Destination] | to                   properties section
 * absolute_expiry_time |[Expiration]  | absolute-expiry-time properties section
 * message_id           | MessageID    | message-id           properties section
 * reply_to             |[ReplyTo]     | reply-to             properties section
 * creation_time        | Timestamp    | creation-time        properties section
 * jms_type             | Type         | jms-type             message-annotations section
 */
const string EMPTY;
const string PERSISTENT("PERSISTENT");
const string NON_PERSISTENT("NON_PERSISTENT");

namespace {
   typedef std::map<std::string, std::string> Aliases;
   Aliases define_aliases()
   {
       Aliases aliases;
       aliases["JMSType"] = "subject";
       aliases["JMSCorrelationID"] = "correlation_id";
       aliases["JMSMessageID"] = "message_id";
       aliases["JMSDeliveryMode"] = "delivery_mode";
       aliases["JMSRedelivered"] = "redelivered";
       aliases["JMSPriority"] = "priority";
       aliases["JMSDestination"] = "to";
       aliases["JMSReplyTo"] = "reply_to";
       aliases["JMSTimestamp"] = "creation_time";
       aliases["JMSExpiration"] = "absolute_expiry_time";
       return aliases;
   }
   const Aliases aliases = define_aliases();
}

class MessageSelectorEnv : public SelectorEnv {
    const Message& msg;
    mutable boost::ptr_vector<string> returnedStrings;
    mutable unordered_map<string, Value> returnedValues;
    mutable bool valuesLookedup;

    const Value& value(const string&) const;
    const Value specialValue(const string&) const;

public:
    MessageSelectorEnv(const Message&);
};

MessageSelectorEnv::MessageSelectorEnv(const Message& m) :
    msg(m),
    valuesLookedup(false)
{}

const Value MessageSelectorEnv::specialValue(const string& id) const
{
    Value v;
    // TODO: Just use a simple if chain for now - improve this later
    if ( id=="delivery_mode" ) {
        v = msg.getEncoding().isPersistent() ? PERSISTENT : NON_PERSISTENT;
    } else if ( id=="subject" ) {
        std::string s = msg.getSubject();
        if (!s.empty()) {
            returnedStrings.push_back(new string(s));
            v = returnedStrings[returnedStrings.size()-1];
        }
    } else if ( id=="redelivered" ) {
        // Although redelivered is defined to be true delivery-count>0 if it is 0 now
        // it will be 1 by the time the message is delivered
        v = msg.getDeliveryCount()>=0 ? true : false;
    } else if ( id=="priority" ) {
        v = int64_t(msg.getPriority());
    } else if ( id=="correlation_id" ) {
        MessageId cId = msg.getEncoding().getCorrelationId();
        if (cId) {
            returnedStrings.push_back(new string(cId.str()));
            v = returnedStrings[returnedStrings.size()-1];
        }
    } else if ( id=="message_id" ) {
        MessageId mId = msg.getEncoding().getMessageId();
        if (mId) {
            returnedStrings.push_back(new string(mId.str()));
            v = returnedStrings[returnedStrings.size()-1];
        }
    } else if ( id=="to" ) {
        std::string s = msg.getTo();
        if (!s.empty()) {
            returnedStrings.push_back(new string(s));
            v = returnedStrings[returnedStrings.size()-1];
        }
    } else if ( id=="reply_to" ) {
        std::string s = msg.getReplyTo();
        if (!s.empty()) {
            returnedStrings.push_back(new string(s));
            v = returnedStrings[returnedStrings.size()-1];
        }
    } else if ( id=="absolute_expiry_time" ) {
        qpid::sys::AbsTime expiry = msg.getExpiration();
        // Java property has value of 0 for no expiry
        v = (expiry==qpid::sys::FAR_FUTURE) ? 0
            : qpid::sys::Duration(qpid::sys::AbsTime::epoch(), expiry) / qpid::sys::TIME_MSEC;
    } else if ( id=="creation_time" ) {
        // Use the time put on queue (if it is enabled) as 0-10 has no standard way to get message
        // creation time and we're not paying attention to the 1.0 creation time yet.
        v = int64_t(msg.getTimestamp() * 1000); // getTimestamp() returns time in seconds we need milliseconds
    } else if ( id=="jms_type" ) {
        // Currently we can't distinguish between an empty JMSType and no JMSType
        // We'll assume for now that setting an empty JMSType doesn't make a lot of sense
        const string jmsType = msg.getAnnotation("jms-type").asString();
        if ( !jmsType.empty() ) {
            returnedStrings.push_back(new string(jmsType));
            v = returnedStrings[returnedStrings.size()-1];
        }
    } else {
        v = Value();
    }
    return v;
}

struct ValueHandler : public broker::MapHandler {
    unordered_map<string, Value>& values;
    boost::ptr_vector<string>& strings;

    ValueHandler(unordered_map<string, Value>& v, boost::ptr_vector<string>& s) :
        values(v),
        strings(s)
    {}

    template <typename T>
    void handle(const CharSequence& key, const T& value)
    {
        values[string(key.data, key.size)] = value;
    }

    void handleVoid(const CharSequence&) {}
    void handleBool(const CharSequence& key, bool value) { handle<bool>(key, value); }
    void handleUint8(const CharSequence& key, uint8_t value) { handle<int64_t>(key, value); }
    void handleUint16(const CharSequence& key, uint16_t value) { handle<int64_t>(key, value); }
    void handleUint32(const CharSequence& key, uint32_t value) { handle<int64_t>(key, value); }
    void handleUint64(const CharSequence& key, uint64_t value) {
        if ( value>uint64_t(std::numeric_limits<int64_t>::max()) ) {
            handle<double>(key, value);
        } else {
            handle<int64_t>(key, value);
        }
    }
    void handleInt8(const CharSequence& key, int8_t value) { handle<int64_t>(key, value); }
    void handleInt16(const CharSequence& key, int16_t value) { handle<int64_t>(key, value); }
    void handleInt32(const CharSequence& key, int32_t value) { handle<int64_t>(key, value); }
    void handleInt64(const CharSequence& key, int64_t value) { handle<int64_t>(key, value); }
    void handleFloat(const CharSequence& key, float value) { handle<double>(key, value); }
    void handleDouble(const CharSequence& key, double value) { handle<double>(key, value); }
    void handleString(const CharSequence& key, const CharSequence& value, const CharSequence&) {
        strings.push_back(new string(value.data, value.size));
        handle(key, strings[strings.size()-1]);
    }
};

const Value& MessageSelectorEnv::value(const string& identifier) const
{
    // Check for amqp prefix and strip it if present
    if ( identifier.substr(0, 5) == "amqp." ) {
        if ( returnedValues.count(identifier)==0 ) {
            QPID_LOG(debug, "Selector lookup special identifier: " << identifier);
            returnedValues[identifier] = specialValue(identifier.substr(5));
        }
    } else if (identifier.substr(0, 3) == "JMS") {
        Aliases::const_iterator equivalent = aliases.find(identifier);
        if (equivalent != aliases.end()) {
            QPID_LOG(debug, "Selector lookup JMS identifier: " << identifier << " treated as alias for " << equivalent->second);
            returnedValues[identifier] = specialValue(equivalent->second);
        } else {
            QPID_LOG(info, "Unrecognised JMS identifier in selector: " << identifier);
        }
    } else if (!valuesLookedup) {
        QPID_LOG(debug, "Selector lookup triggered by: " << identifier);
        // Iterate over all the message properties
        ValueHandler handler(returnedValues, returnedStrings);
        msg.getEncoding().processProperties(handler);
        valuesLookedup = true;
        // Anything that wasn't found will have a void value now
    }
    const Value& v = returnedValues[identifier];
    QPID_LOG(debug, "Selector identifier: " << identifier << "->" << v);
    return v;
}

Selector::Selector(const string& e)
try :
    parse(TopExpression::parse(e)),
    expression(e)
{
    bool debugOut;
    QPID_LOG_TEST(debug, debugOut);
    if (debugOut) {
        std::stringstream ss;
        parse->repr(ss);
        QPID_LOG(debug, "Selector parsed[" << e << "] into: " << ss.str());
    }
}
catch (std::range_error& ex) {
    QPID_LOG(debug, "Selector failed[" << e << "] -> " << ex.what());
    throw;
}

Selector::~Selector()
{
}

bool Selector::eval(const SelectorEnv& env)
{
    return parse->eval(env);
}

bool Selector::filter(const Message& msg)
{
    const MessageSelectorEnv env(msg);
    return eval(env);
}

boost::shared_ptr<Selector> returnSelector(const string& e)
{
    return boost::shared_ptr<Selector>(new Selector(e));
}

}}
