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
#include "DataBuilder.h"
#include "CharSequence.h"
#include "qpid/log/Statement.h"
#include "qpid/types/encodings.h"

namespace qpid {
namespace amqp {

void DataBuilder::onNull(const Descriptor*)
{
    handle(qpid::types::Variant());
}
void DataBuilder::onBoolean(bool v, const Descriptor*)
{
    handle(v);
}
void DataBuilder::onUByte(uint8_t v, const Descriptor*)
{
    handle(v);
}
void DataBuilder::onUShort(uint16_t v, const Descriptor*)
{
    handle(v);
}
void DataBuilder::onUInt(uint32_t v, const Descriptor*)
{
    handle(v);
}
void DataBuilder::onULong(uint64_t v, const Descriptor*)
{
    handle(v);
}
void DataBuilder::onByte(int8_t v, const Descriptor*)
{
    handle(v);
}
void DataBuilder::onShort(int16_t v, const Descriptor*)
{
    handle(v);
}
void DataBuilder::onInt(int32_t v, const Descriptor*)
{
    handle(v);
}
void DataBuilder::onLong(int64_t v, const Descriptor*)
{
    handle(v);
}
void DataBuilder::onFloat(float v, const Descriptor*)
{
    handle(v);
}
void DataBuilder::onDouble(double v, const Descriptor*)
{
    handle(v);
}
void DataBuilder::onUuid(const CharSequence& v, const Descriptor*)
{
    if (v.size == qpid::types::Uuid::SIZE) {
        handle(qpid::types::Uuid(v.data));
    }
}
void DataBuilder::onTimestamp(int64_t v, const Descriptor*)
{
    handle(v);
}

void DataBuilder::handle(const qpid::types::Variant& v)
{
    switch (nested.top()->getType()) {
      case qpid::types::VAR_MAP:
        nested.push(&nested.top()->asMap()[v.asString()]);
        break;
      case qpid::types::VAR_LIST:
        nested.top()->asList().push_back(v);
        break;
      default:
        *(nested.top()) = v;
        nested.pop();
        break;
    }
}

void DataBuilder::onBinary(const CharSequence& v, const Descriptor*)
{
    onString(std::string(v.data, v.size), qpid::types::encodings::BINARY);
}
void DataBuilder::onString(const CharSequence& v, const Descriptor*)
{
    onString(std::string(v.data, v.size), qpid::types::encodings::UTF8);
}
void DataBuilder::onSymbol(const CharSequence& v, const Descriptor*)
{
    onString(std::string(v.data, v.size), qpid::types::encodings::ASCII);
}

void DataBuilder::onString(const std::string& value, const std::string& encoding)
{
    switch (nested.top()->getType()) {
      case qpid::types::VAR_MAP:
        nested.push(&nested.top()->asMap()[value]);
        break;
      case qpid::types::VAR_LIST:
        nested.top()->asList().push_back(qpid::types::Variant(value));
        nested.top()->asList().back().setEncoding(encoding);
        break;
      default:
        qpid::types::Variant& v = *(nested.top());
        v = value;
        v.setEncoding(encoding);
        nested.pop();
        break;
    }
}

bool DataBuilder::proceed()
{
    return !nested.empty();
}

bool DataBuilder::nest(const qpid::types::Variant& n)
{
    switch (nested.top()->getType()) {
      case qpid::types::VAR_MAP:
        if (nested.size() > 1 || nested.top()->asMap().size() > 0) {
            QPID_LOG(error, QPID_MSG("Expecting map key; got " << n << " " << *(nested.top())));
        }
        break;
      case qpid::types::VAR_LIST:
        nested.top()->asList().push_back(n);
        nested.push(&nested.top()->asList().back());
        break;
      default:
        qpid::types::Variant& value = *(nested.top());
        value = n;
        nested.pop();
        nested.push(&value);
        break;
    }
    return true;
}

bool DataBuilder::onStartList(uint32_t, const CharSequence&, const CharSequence&, const Descriptor*)
{
    return nest(qpid::types::Variant::List());
}
void DataBuilder::onEndList(uint32_t /*count*/, const Descriptor*)
{
    nested.pop();
}
bool DataBuilder::onStartMap(uint32_t /*count*/, const CharSequence&, const CharSequence&, const Descriptor*)
{
    return nest(qpid::types::Variant::Map());
}
void DataBuilder::onEndMap(uint32_t /*count*/, const Descriptor*)
{
    nested.pop();
}
bool DataBuilder::onStartArray(uint32_t count, const CharSequence&, const Constructor&, const Descriptor*)
{
    return onStartList(count, CharSequence::create(), CharSequence::create(), 0);
}
void DataBuilder::onEndArray(uint32_t count, const Descriptor*)
{
    onEndList(count, 0);
}
qpid::types::Variant& DataBuilder::getValue()
{
    return base;
}
DataBuilder::DataBuilder(qpid::types::Variant v) : base(v)
{
    nested.push(&base);
}
DataBuilder::~DataBuilder() {}
}} // namespace qpid::amqp
