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
#include "DataReader.h"
#include "qpid/amqp/CharSequence.h"
#include "qpid/amqp/Descriptor.h"
#include "qpid/amqp/MapBuilder.h"
#include "qpid/log/Statement.h"
#include <string>
#include <proton/engine.h>

namespace qpid {
namespace broker {
namespace amqp {
namespace {
qpid::amqp::CharSequence convert(pn_bytes_t in)
{
    qpid::amqp::CharSequence out;
    out.data = in.start;
    out.size = in.size;
    return out;
}

qpid::amqp::CharSequence convert(pn_uuid_t in)
{
    qpid::amqp::CharSequence out;
    out.data = in.bytes;
    out.size = 16;
    return out;
}
}

DataReader::DataReader(qpid::amqp::Reader& r) : reader(r) {}

void DataReader::read(pn_data_t* data)
{
    do {
        readOne(data);
    } while (pn_data_next(data));
}
void DataReader::readOne(pn_data_t* data)
{
    qpid::amqp::Descriptor descriptor(0);
    bool described = pn_data_is_described(data);
    if (described) {
        pn_data_enter(data);
        pn_data_next(data);
        if (pn_data_type(data) == PN_ULONG) {
            descriptor = qpid::amqp::Descriptor(pn_data_get_ulong(data));
        } else if (pn_data_type(data) == PN_SYMBOL) {
            descriptor = qpid::amqp::Descriptor(convert(pn_data_get_symbol(data)));
        } else {
            QPID_LOG(notice, "Ignoring descriptor of type " << pn_data_type(data));
        }
        pn_data_next(data);
    }
    switch (pn_data_type(data)) {
      case PN_NULL:
        reader.onNull(described ? &descriptor : 0);
        break;
      case PN_BOOL:
        reader.onBoolean(pn_data_get_bool(data), described ? &descriptor : 0);
        break;
      case PN_UBYTE:
        reader.onUByte(pn_data_get_ubyte(data), described ? &descriptor : 0);
        break;
      case PN_BYTE:
        reader.onByte(pn_data_get_byte(data), described ? &descriptor : 0);
        break;
      case PN_USHORT:
        reader.onUShort(pn_data_get_ushort(data), described ? &descriptor : 0);
        break;
      case PN_SHORT:
        reader.onShort(pn_data_get_short(data), described ? &descriptor : 0);
        break;
      case PN_UINT:
        reader.onUInt(pn_data_get_uint(data), described ? &descriptor : 0);
        break;
      case PN_INT:
        reader.onInt(pn_data_get_int(data), described ? &descriptor : 0);
        break;
      case PN_CHAR:
        pn_data_get_char(data);
        break;
      case PN_ULONG:
        reader.onULong(pn_data_get_ulong(data), described ? &descriptor : 0);
        break;
      case PN_LONG:
        reader.onLong(pn_data_get_long(data), described ? &descriptor : 0);
        break;
      case PN_TIMESTAMP:
        reader.onTimestamp(pn_data_get_timestamp(data), described ? &descriptor : 0);
        break;
      case PN_FLOAT:
        reader.onFloat(pn_data_get_float(data), described ? &descriptor : 0);
        break;
      case PN_DOUBLE:
        reader.onDouble(pn_data_get_double(data), described ? &descriptor : 0);
        break;
      case PN_DECIMAL32:
        pn_data_get_decimal32(data);
        break;
      case PN_DECIMAL64:
        pn_data_get_decimal64(data);
        break;
      case PN_DECIMAL128:
        pn_data_get_decimal128(data);
        break;
      case PN_UUID:
        reader.onUuid(convert(pn_data_get_uuid(data)), described ? &descriptor : 0);
        break;
      case PN_BINARY:
        reader.onBinary(convert(pn_data_get_binary(data)), described ? &descriptor : 0);
        break;
      case PN_STRING:
        reader.onString(convert(pn_data_get_string(data)), described ? &descriptor : 0);
        break;
      case PN_SYMBOL:
        reader.onSymbol(convert(pn_data_get_symbol(data)), described ? &descriptor : 0);
        break;
      case PN_DESCRIBED:
        break;
      case PN_ARRAY:
        readArray(data, described ? &descriptor : 0);
        break;
      case PN_LIST:
        readList(data, described ? &descriptor : 0);
        break;
      case PN_MAP:
        readMap(data, described ? &descriptor : 0);
        break;
      default:
        break;
    }
    if (described) pn_data_exit(data);
}

void DataReader::readArray(pn_data_t* /*data*/, const qpid::amqp::Descriptor* /*descriptor*/)
{
    //not yet implemented
}

void DataReader::readList(pn_data_t* data, const qpid::amqp::Descriptor* descriptor)
{
    size_t count = pn_data_get_list(data);
    bool skip = reader.onStartList(count, qpid::amqp::CharSequence(), qpid::amqp::CharSequence(), descriptor);
    if (!skip) {
        pn_data_enter(data);
        for (size_t i = 0; i < count && pn_data_next(data); ++i) {
            read(data);
        }
        pn_data_exit(data);
        reader.onEndList(count, descriptor);
    }
}

void DataReader::readMap(pn_data_t* data, const qpid::amqp::Descriptor* descriptor)
{
    size_t count = pn_data_get_map(data);
    reader.onStartMap(count, qpid::amqp::CharSequence(), qpid::amqp::CharSequence(), descriptor);
    pn_data_enter(data);
    for (size_t i = 0; i < count && pn_data_next(data); ++i) {
        read(data);
    }
    pn_data_exit(data);
    reader.onEndMap(count, descriptor);
}

void DataReader::read(pn_data_t* data, std::map<std::string, qpid::types::Variant>& out)
{
    qpid::amqp::MapBuilder builder;
    DataReader reader(builder);
    reader.read(data);
    out = builder.getMap();
}
}}} // namespace qpid::broker::amqp
