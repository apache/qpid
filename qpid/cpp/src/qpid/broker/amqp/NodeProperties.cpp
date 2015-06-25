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
#include "qpid/broker/amqp/NodeProperties.h"
#include "qpid/broker/amqp/DataReader.h"
#include "qpid/broker/Exchange.h"
#include "qpid/broker/Queue.h"
#include "qpid/broker/QueueSettings.h"
#include "qpid/amqp/CharSequence.h"
#include "qpid/amqp/Descriptor.h"
#include "qpid/amqp/descriptors.h"
#include "qpid/amqp_0_10/Codecs.h"
#include "qpid/types/encodings.h"
#include "qpid/types/Variant.h"
#include "qpid/broker/QueueSettings.h"
#include "qpid/log/Statement.h"
extern "C" {
#include <proton/engine.h>
}

using qpid::amqp::CharSequence;
using qpid::amqp::Descriptor;

namespace qpid {
namespace broker {
namespace amqp {
namespace {
//distribution modes:
const std::string MOVE("move");
const std::string COPY("copy");
const std::string SUPPORTED_DIST_MODES("supported-dist-modes");
const std::string LIFETIME_POLICY("lifetime-policy");

//AMQP 0-10 standard parameters:
const std::string DURABLE("durable");
const std::string EXCLUSIVE("exclusive");
const std::string AUTO_DELETE("auto-delete");
const std::string ALTERNATE_EXCHANGE("alternate-exchange");
const std::string EXCHANGE_TYPE("exchange-type");
const std::string EMPTY;

pn_bytes_t convert(const std::string& s)
{
    pn_bytes_t result;
    result.start = const_cast<char*>(s.data());
    result.size = s.size();
    return result;
}

bool getLifetimePolicy(const Descriptor& d, QueueSettings::LifetimePolicy& policy)
{
    if (d.match(qpid::amqp::lifetime_policy::DELETE_ON_CLOSE_SYMBOL, qpid::amqp::lifetime_policy::DELETE_ON_CLOSE_CODE)) {
        policy = QueueSettings::DELETE_ON_CLOSE;
        return true;
    } else if (d.match(qpid::amqp::lifetime_policy::DELETE_ON_NO_LINKS_SYMBOL, qpid::amqp::lifetime_policy::DELETE_ON_NO_LINKS_CODE)) {
        policy = QueueSettings::DELETE_IF_UNUSED;
        return true;
    } else if (d.match(qpid::amqp::lifetime_policy::DELETE_ON_NO_MESSAGES_SYMBOL, qpid::amqp::lifetime_policy::DELETE_ON_NO_MESSAGES_CODE)) {
        policy = QueueSettings::DELETE_IF_EMPTY;
        return true;
    } else if (d.match(qpid::amqp::lifetime_policy::DELETE_ON_NO_LINKS_OR_MESSAGES_SYMBOL, qpid::amqp::lifetime_policy::DELETE_ON_NO_LINKS_OR_MESSAGES_CODE)) {
        policy = QueueSettings::DELETE_IF_UNUSED_AND_EMPTY;
        return true;
    } else {
        return false;
    }
}

bool getLifetimeDescriptorSymbol(QueueSettings::LifetimePolicy policy, pn_bytes_t& out)
{
    switch (policy) {
      case QueueSettings::DELETE_ON_CLOSE:
        out = convert(qpid::amqp::lifetime_policy::DELETE_ON_CLOSE_SYMBOL);
        return true;
      case QueueSettings::DELETE_IF_UNUSED:
        out = convert(qpid::amqp::lifetime_policy::DELETE_ON_NO_LINKS_SYMBOL);
        return true;
      case QueueSettings::DELETE_IF_EMPTY:
        out = convert(qpid::amqp::lifetime_policy::DELETE_ON_NO_MESSAGES_SYMBOL);
        return true;
      case QueueSettings::DELETE_IF_UNUSED_AND_EMPTY:
        out = convert(qpid::amqp::lifetime_policy::DELETE_ON_NO_LINKS_OR_MESSAGES_SYMBOL);
        return true;
      default:
        return false;
    }
}

}

NodeProperties::NodeProperties(bool isDynamic) : received(false), queue(true), durable(false), autoDelete(false), exclusive(false),
                                                 dynamic(isDynamic), exchangeType("topic"), lifetime(QueueSettings::DELETE_IF_UNUSED) {}

void NodeProperties::read(pn_data_t* data)
{
    DataReader reader(*this);
    reader.read(data);

}

bool NodeProperties::wasSpecified(const std::string& key) const
{
    return specified.find(key) != specified.end();
}

void NodeProperties::write(pn_data_t* data, boost::shared_ptr<Queue> node)
{
    if (received) {
        pn_data_put_map(data);
        pn_data_enter(data);
        pn_data_put_symbol(data, convert(SUPPORTED_DIST_MODES));
        pn_data_put_string(data, convert(MOVE));//TODO: should really add COPY as well, since queues can be browsed
        pn_bytes_t symbol;
        if ((wasSpecified(AUTO_DELETE) || wasSpecified(LIFETIME_POLICY)) && node->isAutoDelete() && getLifetimeDescriptorSymbol(node->getSettings().lifetime, symbol)) {
            pn_data_put_symbol(data, convert(LIFETIME_POLICY));
            pn_data_put_described(data);
            pn_data_enter(data);
            pn_data_put_symbol(data, symbol);
            pn_data_put_list(data);
            pn_data_exit(data);
        }
        if (wasSpecified(DURABLE) && node->isDurable()) {
            pn_data_put_symbol(data, convert(DURABLE));
            pn_data_put_bool(data, true);
        }
        if (wasSpecified(EXCLUSIVE) && node->hasExclusiveOwner()) {
            pn_data_put_symbol(data, convert(EXCLUSIVE));
            pn_data_put_bool(data, true);
        }
        if (!alternateExchange.empty() && node->getAlternateExchange()) {
            pn_data_put_symbol(data, convert(ALTERNATE_EXCHANGE));
            pn_data_put_string(data, convert(node->getAlternateExchange()->getName()));
        }

        qpid::types::Variant::Map actual = node->getSettings().asMap();
        qpid::types::Variant::Map unrecognised;
        QueueSettings dummy;
        dummy.populate(actual, unrecognised);
        for (qpid::types::Variant::Map::const_iterator i = unrecognised.begin(); i != unrecognised.end(); ++i) {
            actual.erase(i->first);
        }
        for (qpid::types::Variant::Map::const_iterator i = properties.begin(); i != properties.end(); ++i) {
            qpid::types::Variant::Map::const_iterator j = actual.find(i->first);
            if (j != actual.end()) {
                pn_data_put_symbol(data, convert(j->first));
                pn_data_put_string(data, convert(j->second.asString()));
            }
        }

        pn_data_exit(data);
    }
}
namespace {
const std::string QPID_MSG_SEQUENCE("qpid.msg_sequence");
const std::string QPID_IVE("qpid.ive");
}
void NodeProperties::write(pn_data_t* data, boost::shared_ptr<Exchange> node)
{
    if (received) {
        pn_data_put_map(data);
        pn_data_enter(data);
        pn_data_put_symbol(data, convert(SUPPORTED_DIST_MODES));
        pn_data_put_string(data, convert(COPY));
        if (wasSpecified(DURABLE) && node->isDurable()) {
            pn_data_put_symbol(data, convert(DURABLE));
            pn_data_put_bool(data, true);
        }
        if (!exchangeType.empty()) {
            pn_data_put_symbol(data, convert(EXCHANGE_TYPE));
            pn_data_put_string(data, convert(node->getType()));
        }
        if (!alternateExchange.empty() && node->getAlternate()) {
            pn_data_put_symbol(data, convert(ALTERNATE_EXCHANGE));
            pn_data_put_string(data, convert(node->getAlternate()->getName()));
        }
        if (wasSpecified(AUTO_DELETE)) {
            pn_data_put_symbol(data, convert(AUTO_DELETE));
            pn_data_put_bool(data, node->isAutoDelete());
        }

        for (qpid::types::Variant::Map::const_iterator i = properties.begin(); i != properties.end(); ++i) {
            if ((i->first == QPID_MSG_SEQUENCE || i->first == QPID_IVE) && node->getArgs().isSet(i->first)) {
                pn_data_put_symbol(data, convert(i->first));
                pn_data_put_bool(data, true);
            }
        }

        pn_data_exit(data);
    }
}


void NodeProperties::process(const std::string& key, const qpid::types::Variant& value, const Descriptor* d)
{
    received = true;
    QPID_LOG(debug, "Processing node property " << key << " = " << value);
    specified.insert(key);
    if (key == SUPPORTED_DIST_MODES) {
        if (value == MOVE) queue = true;
        else if (value == COPY) queue = false;
    } else if (key == LIFETIME_POLICY) {
        if (d) {
            if (getLifetimePolicy(*d, lifetime)) {
                autoDelete = true;
            } else {
                QPID_LOG(warning, "Unrecognised lifetime policy: " << *d);
            }
        }
    } else if (key == DURABLE) {
        durable = value;
    } else if (key == EXCLUSIVE) {
        exclusive = value;
    } else if (key == AUTO_DELETE) {
        autoDelete = value;
    } else if (key == ALTERNATE_EXCHANGE) {
        alternateExchange = value.asString();
    } else if (key == EXCHANGE_TYPE) {
        exchangeType = value.asString();
    } else {
        properties[key] = value;
    }
}

bool NodeProperties::onStartListValue(const qpid::amqp::CharSequence& key, uint32_t count, const qpid::amqp::Descriptor* d)
{
    QPID_LOG(debug, "NodeProperties::onStartListValue(" << std::string(key.data, key.size) << ", " << count << ", " << d);
    process(key.str(), qpid::types::Variant(), d);
    return true;
}

void NodeProperties::onNullValue(const CharSequence& key, const Descriptor* d)
{
    process(key.str(), qpid::types::Variant(), d);
}

void NodeProperties::onBooleanValue(const CharSequence& key, bool value, const Descriptor* d)
{
    process(key.str(), value, d);
}

void NodeProperties::onUByteValue(const CharSequence& key, uint8_t value, const Descriptor* d)
{
    process(key.str(), value, d);
}

void NodeProperties::onUShortValue(const CharSequence& key, uint16_t value, const Descriptor* d)
{
    process(key.str(), value, d);
}

void NodeProperties::onUIntValue(const CharSequence& key, uint32_t value, const Descriptor* d)
{
    process(key.str(), value, d);
}

void NodeProperties::onULongValue(const CharSequence& key, uint64_t value, const Descriptor* d)
{
    process(key.str(), value, d);
}

void NodeProperties::onByteValue(const CharSequence& key, int8_t value, const Descriptor* d)
{
    process(key.str(), value, d);
}

void NodeProperties::onShortValue(const CharSequence& key, int16_t value, const Descriptor* d)
{
    process(key.str(), value, d);
}

void NodeProperties::onIntValue(const CharSequence& key, int32_t value, const Descriptor* d)
{
    process(key.str(), value, d);
}

void NodeProperties::onLongValue(const CharSequence& key, int64_t value, const Descriptor* d)
{
    process(key.str(), value, d);
}

void NodeProperties::onFloatValue(const CharSequence& key, float value, const Descriptor* d)
{
    process(key.str(), value, d);
}

void NodeProperties::onDoubleValue(const CharSequence& key, double value, const Descriptor* d)
{
    process(key.str(), value, d);
}

void NodeProperties::onUuidValue(const CharSequence& key, const CharSequence& value, const Descriptor* d)
{
    process(key.str(), value.str(), d);
}

void NodeProperties::onTimestampValue(const CharSequence& key, int64_t value, const Descriptor* d)
{
    process(key.str(), value, d);
}

namespace {
qpid::types::Variant utf8(const std::string& s)
{
    qpid::types::Variant v(s);
    v.setEncoding(qpid::types::encodings::UTF8);
    return v;
}
}

void NodeProperties::onStringValue(const CharSequence& key, const CharSequence& value, const Descriptor* d)
{
    process(key.str(), utf8(value.str()), d);
}

void NodeProperties::onSymbolValue(const CharSequence& key, const CharSequence& value, const Descriptor* d)
{
    process(key.str(), utf8(value.str()), d);
}

QueueSettings NodeProperties::getQueueSettings()
{
    //assume autodelete for dynamic nodes unless explicitly requested
    //otherwise or unless durability is requested
    QueueSettings settings(durable, autoDelete || (dynamic && !wasSpecified(AUTO_DELETE) && !durable));
    qpid::types::Variant::Map unused;
    settings.populate(properties, unused);
    settings.lifetime = lifetime;
    qpid::amqp_0_10::translate(unused, settings.storeSettings);
    return settings;
}

bool NodeProperties::isQueue() const
{
    return queue;
}
bool NodeProperties::isDurable() const
{
    return durable;
}
bool NodeProperties::isExclusive() const
{
    return exclusive;
}
bool NodeProperties::isAutodelete() const
{
    return autoDelete;
}
std::string NodeProperties::getExchangeType() const
{
    return exchangeType;
}
std::string NodeProperties::getSpecifiedExchangeType() const
{
    return wasSpecified(EXCHANGE_TYPE) ? exchangeType : EMPTY;
}
std::string NodeProperties::getAlternateExchange() const
{
    return alternateExchange;
}

bool NodeProperties::trackControllingLink() const
{
    return lifetime == QueueSettings::DELETE_ON_CLOSE || lifetime == QueueSettings::DELETE_IF_EMPTY;
}

const qpid::types::Variant::Map& NodeProperties::getProperties() const
{
    return properties;
}

}}} // namespace qpid::broker::amqp
