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
#include "qpid/amqp/CharSequence.h"
#include "qpid/types/Variant.h"
#include "qpid/broker/QueueSettings.h"
#include "qpid/log/Statement.h"

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

//AMQP 0-10 standard parameters:
const std::string DURABLE("durable");
const std::string EXCLUSIVE("exclusive");
const std::string AUTO_DELETE("auto-delete");
const std::string ALTERNATE_EXCHANGE("alternate-exchange");
const std::string EXCHANGE_TYPE("exchange-type");
}

NodeProperties::NodeProperties() : queue(true), durable(false), autoDelete(false), exclusive(false), exchangeType("topic") {}

void NodeProperties::read(pn_data_t* data)
{
    DataReader reader(*this);
    reader.read(data);
}

void NodeProperties::process(const std::string& key, const qpid::types::Variant& value)
{
    QPID_LOG(notice, "Processing node property " << key << " = " << value);
    if (key == SUPPORTED_DIST_MODES) {
        if (value == MOVE) queue = true;
        else if (value == COPY) queue = false;
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

void NodeProperties::onNullValue(const CharSequence& key, const Descriptor*)
{
    process(key.str(), qpid::types::Variant());
}

void NodeProperties::onBooleanValue(const CharSequence& key, bool value, const Descriptor*)
{
    process(key.str(), value);
}

void NodeProperties::onUByteValue(const CharSequence& key, uint8_t value, const Descriptor*)
{
    process(key.str(), value);
}

void NodeProperties::onUShortValue(const CharSequence& key, uint16_t value, const Descriptor*)
{
    process(key.str(), value);
}

void NodeProperties::onUIntValue(const CharSequence& key, uint32_t value, const Descriptor*)
{
    process(key.str(), value);
}

void NodeProperties::onULongValue(const CharSequence& key, uint64_t value, const Descriptor*)
{
    process(key.str(), value);
}

void NodeProperties::onByteValue(const CharSequence& key, int8_t value, const Descriptor*)
{
    process(key.str(), value);
}

void NodeProperties::onShortValue(const CharSequence& key, int16_t value, const Descriptor*)
{
    process(key.str(), value);
}

void NodeProperties::onIntValue(const CharSequence& key, int32_t value, const Descriptor*)
{
    process(key.str(), value);
}

void NodeProperties::onLongValue(const CharSequence& key, int64_t value, const Descriptor*)
{
    process(key.str(), value);
}

void NodeProperties::onFloatValue(const CharSequence& key, float value, const Descriptor*)
{
    process(key.str(), value);
}

void NodeProperties::onDoubleValue(const CharSequence& key, double value, const Descriptor*)
{
    process(key.str(), value);
}

void NodeProperties::onUuidValue(const CharSequence& key, const CharSequence& value, const Descriptor*)
{
    process(key.str(), value.str());
}

void NodeProperties::onTimestampValue(const CharSequence& key, int64_t value, const Descriptor*)
{
    process(key.str(), value);
}

void NodeProperties::onStringValue(const CharSequence& key, const CharSequence& value, const Descriptor*)
{
    process(key.str(), value.str());
}

void NodeProperties::onSymbolValue(const CharSequence& key, const CharSequence& value, const Descriptor*)
{
    process(key.str(), value.str());
}

QueueSettings NodeProperties::getQueueSettings()
{
    QueueSettings settings(durable, autoDelete);
    qpid::types::Variant::Map unused;
    settings.populate(properties, unused);
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
std::string NodeProperties::getExchangeType() const
{
    return exchangeType;
}
std::string NodeProperties::getAlternateExchange() const
{
    return alternateExchange;
}

}}} // namespace qpid::broker::amqp
