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

#include "qpid/broker/Message.h"
#include "qpid/broker/SelectorExpression.h"
#include "qpid/broker/SelectorValue.h"
#include "qpid/log/Statement.h"
#include "qpid/types/Variant.h"

#include <string>
#include <sstream>
#include "qpid/sys/unordered_map.h"

#include <boost/lexical_cast.hpp>
#include <boost/ptr_container/ptr_vector.hpp>

namespace qpid {
namespace broker {

using std::string;
using qpid::sys::unordered_map;

/**
 * Identifier  (amqp.)  | JMS...       | amqp 1.0 equivalent
 * durable              |              | durable              header section
 * delivery_mode        | DeliveryMode | [durable ? 'PERSISTENT' : 'NON_PERSISTENT'] (computed value)
 * priority             | Priority     | priority             header section
 * delivery_count       |              | delivery-count       header section
 * redelivered          |[Redelivered] | (delivery_count>0)  (computed value)
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

const Value specialValue(const Message& msg, const string& id)
{
    // TODO: Just use a simple if chain for now - improve this later
    if ( id=="delivery_mode" ) {
        return msg.getEncoding().isPersistent() ? PERSISTENT : NON_PERSISTENT;
    } else if ( id=="redelivered" ) {
        return msg.getDeliveryCount()>0 ? true : false;
    } else if ( id=="priority" ) {
        return int64_t(msg.getPriority());
    } else if ( id=="correlation_id" ) {
        return EMPTY; // Needs an indirection in getEncoding().
    } else if ( id=="message_id" ) {
        return EMPTY; // Needs an indirection in getEncoding().
    } else if ( id=="to" ) {
        return EMPTY; // This is good for 0-10, not sure about 1.0
    } else if ( id=="reply_to" ) {
        return EMPTY; // Needs an indirection in getEncoding().
    } else if ( id=="absolute_expiry_time" ) {
        return EMPTY; // Needs an indirection in getEncoding().
    } else if ( id=="creation_time" ) {
        return EMPTY; // Needs an indirection in getEncoding().
    } else if ( id=="jms_type" ) {
        return EMPTY;
    } else return Value();
}

class MessageSelectorEnv : public SelectorEnv {
    const Message& msg;
    mutable boost::ptr_vector<string> returnedStrings;
    mutable unordered_map<string, Value> returnedValues;

    const Value& value(const string&) const;

public:
    MessageSelectorEnv(const Message&);
};

MessageSelectorEnv::MessageSelectorEnv(const Message& m) :
msg(m)
{
}

const Value& MessageSelectorEnv::value(const string& identifier) const
{
    if (returnedValues.find(identifier)==returnedValues.end()) {
        Value v;

        // Check for amqp prefix and strip it if present
        if (identifier.substr(0, 5) == "amqp.") {
            v = specialValue(msg, identifier.substr(5));
        } else {
            // Just return property as string
            //v = &msg.getPropertyAsString(identifier);
            qpid::types::Variant var = msg.getProperty(identifier);
            switch (var.getType()) {
            case types::VAR_VOID:
                v = Value(); break;
            case types::VAR_STRING: {
                string& s = var.getString();
                returnedStrings.push_back(new string(s));
                v = returnedStrings[returnedStrings.size()-1];
                break;
            }
            case types::VAR_UINT64:
                // TODO: Need to take care of values too high to be int64_t
            case types::VAR_UINT32:
            case types::VAR_UINT16:
            case types::VAR_UINT8:
            case types::VAR_INT64:
            case types::VAR_INT32:
            case types::VAR_INT16:
            case types::VAR_INT8:
                v = var.asInt64(); break;
            case types::VAR_FLOAT:
            case types::VAR_DOUBLE:
                v = var.asDouble(); break;
            case types::VAR_BOOL:
                v = var.asBool(); break;
            default:
                v = Value(); break;
            }
        }
        QPID_LOG(debug, "Selector identifier: " << identifier << "->" << v);
        returnedValues[identifier] = v;
    }
    return returnedValues[identifier];
}

Selector::Selector(const string& e) :
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

Selector::~Selector()
{
}

bool Selector::eval(const SelectorEnv& env)
{
    return parse->eval(env);
}

bool Selector::filter(const Message& msg)
{
    return eval(MessageSelectorEnv(msg));
}

namespace {
const boost::shared_ptr<Selector> NULL_SELECTOR = boost::shared_ptr<Selector>();
}

boost::shared_ptr<Selector> returnSelector(const string& e)
{
    if (e.empty()) return NULL_SELECTOR;
    return boost::shared_ptr<Selector>(new Selector(e));
}

}}
