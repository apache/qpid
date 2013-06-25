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
#include "MapBuilder.h"
#include <assert.h>

namespace qpid {
namespace amqp {
namespace {
const std::string BINARY("binary");
const std::string UTF8("utf8");
const std::string ASCII("ascii");
}

qpid::types::Variant::Map MapBuilder::getMap()
{
    return map;
}
const qpid::types::Variant::Map MapBuilder::getMap() const
{
    return map;
}

void MapBuilder::onNullValue(const CharSequence& key, const Descriptor*)
{
    map[std::string(key.data, key.size)] = qpid::types::Variant();
}
void MapBuilder::onBooleanValue(const CharSequence& key, bool value, const Descriptor*)
{
    map[std::string(key.data, key.size)] = value;
}
void MapBuilder::onUByteValue(const CharSequence& key, uint8_t value, const Descriptor*)
{
    map[std::string(key.data, key.size)] = value;
}

void MapBuilder::onUShortValue(const CharSequence& key, uint16_t value, const Descriptor*)
{
    map[std::string(key.data, key.size)] = value;
}

void MapBuilder::onUIntValue(const CharSequence& key, uint32_t value, const Descriptor*)
{
    map[std::string(key.data, key.size)] = value;
}

void MapBuilder::onULongValue(const CharSequence& key, uint64_t value, const Descriptor*)
{
    map[std::string(key.data, key.size)] = value;
}

void MapBuilder::onByteValue(const CharSequence& key, int8_t value, const Descriptor*)
{
    map[std::string(key.data, key.size)] = value;
}

void MapBuilder::onShortValue(const CharSequence& key, int16_t value, const Descriptor*)
{
    map[std::string(key.data, key.size)] = value;
}

void MapBuilder::onIntValue(const CharSequence& key, int32_t value, const Descriptor*)
{
    map[std::string(key.data, key.size)] = value;
}

void MapBuilder::onLongValue(const CharSequence& key, int64_t value, const Descriptor*)
{
    map[std::string(key.data, key.size)] = value;
}

void MapBuilder::onFloatValue(const CharSequence& key, float value, const Descriptor*)
{
    map[std::string(key.data, key.size)] = value;
}

void MapBuilder::onDoubleValue(const CharSequence& key, double value, const Descriptor*)
{
    map[std::string(key.data, key.size)] = value;
}

void MapBuilder::onUuidValue(const CharSequence& key, const CharSequence& value, const Descriptor*)
{
    assert(value.size == 16);
    map[std::string(key.data, key.size)] = qpid::types::Uuid(value.data);
}

void MapBuilder::onTimestampValue(const CharSequence& key, int64_t value, const Descriptor*)
{
    map[std::string(key.data, key.size)] = value;
}

void MapBuilder::onBinaryValue(const CharSequence& key, const CharSequence& value, const Descriptor*)
{
    qpid::types::Variant& v = map[std::string(key.data, key.size)];
    v = std::string(value.data, value.size);
    v.setEncoding(BINARY);
}

void MapBuilder::onStringValue(const CharSequence& key, const CharSequence& value, const Descriptor*)
{
    qpid::types::Variant& v = map[std::string(key.data, key.size)];
    v = std::string(value.data, value.size);
    v.setEncoding(UTF8);
}

void MapBuilder::onSymbolValue(const CharSequence& key, const CharSequence& value, const Descriptor*)
{
    qpid::types::Variant& v = map[std::string(key.data, key.size)];
    v = std::string(value.data, value.size);
    v.setEncoding(ASCII);
}
}} // namespace qpid::amqp
