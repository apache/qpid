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
#include "PnData.h"
#include "qpid/types/encodings.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace messaging {
namespace amqp {

using types::Variant;
using namespace types::encodings;

// TODO aconway 2014-11-20: PnData duplicates functionality of qpid::amqp::Encoder,Decoder.
// Collapse them all into a single proton-based codec.

void PnData::put(const Variant::Map& map)
{
    pn_data_put_map(data);
    pn_data_enter(data);
    for (Variant::Map::const_iterator i = map.begin(); i != map.end(); ++i) {
        pn_data_put_string(data, bytes(i->first));
        put(i->second);
    }
    pn_data_exit(data);
}

void PnData::put(const Variant::List& list)
{
    pn_data_put_list(data);
    pn_data_enter(data);
    for (Variant::List::const_iterator i = list.begin(); i != list.end(); ++i) {
        put(*i);
    }
    pn_data_exit(data);
}

void PnData::put(const Variant& value)
{
    // Open data descriptors associated with the value.
    const Variant::List& descriptors = value.getDescriptors();
    for (Variant::List::const_iterator i = descriptors.begin(); i != descriptors.end(); ++i) {
        pn_data_put_described(data);
        pn_data_enter(data);
        if (i->getType() == types::VAR_STRING)
            pn_data_put_symbol(data, bytes(i->asString()));
        else
            pn_data_put_ulong(data, i->asUint64());
    }

    // Put the variant value
    switch (value.getType()) {
      case qpid::types::VAR_VOID:
        pn_data_put_null(data);
        break;
      case qpid::types::VAR_BOOL:
        pn_data_put_bool(data, value.asBool());
        break;
      case qpid::types::VAR_UINT64:
        pn_data_put_ulong(data, value.asUint64());
        break;
      case qpid::types::VAR_INT64:
        pn_data_put_long(data, value.asInt64());
        break;
      case qpid::types::VAR_DOUBLE:
        pn_data_put_double(data, value.asDouble());
        break;
      case qpid::types::VAR_STRING:
        if (value.getEncoding() == ASCII)
            pn_data_put_symbol(data, bytes(value.asString()));
        else if (value.getEncoding() == BINARY)
            pn_data_put_binary(data, bytes(value.asString()));
        else
            pn_data_put_string(data, bytes(value.asString()));
        break;
      case qpid::types::VAR_MAP:
        put(value.asMap());
        break;
      case qpid::types::VAR_LIST:
        put(value.asList());
        break;
      default:
        break;
    }

    // Close any descriptors.
    for (Variant::List::const_iterator i = descriptors.begin(); i != descriptors.end(); ++i)
        pn_data_exit(data);
}

bool PnData::get(qpid::types::Variant& value)
{
    return get(pn_data_type(data), value);
}

void PnData::getList(qpid::types::Variant::List& value)
{
    size_t count = pn_data_get_list(data);
    pn_data_enter(data);
    for (size_t i = 0; i < count && pn_data_next(data); ++i) {
        qpid::types::Variant e;
        if (get(e)) value.push_back(e);
    }
    pn_data_exit(data);
}

void PnData::getMap(qpid::types::Variant::Map& value)
{
    size_t count = pn_data_get_list(data);
    pn_data_enter(data);
    for (size_t i = 0; i < (count/2) && pn_data_next(data); ++i) {
        std::string key = string(pn_data_get_symbol(data));
        pn_data_next(data);
        qpid::types::Variant e;
        if (get(e)) value[key]= e;
    }
    pn_data_exit(data);
}

void PnData::getArray(qpid::types::Variant::List& value)
{
    size_t count = pn_data_get_array(data);
    pn_type_t type = pn_data_get_array_type(data);
    pn_data_enter(data);
    for (size_t i = 0; i < count && pn_data_next(data); ++i) {
        qpid::types::Variant e;
        if (get(type, e)) value.push_back(e);
    }
    pn_data_exit(data);
}

bool PnData::get(pn_type_t type, qpid::types::Variant& value)
{
    switch (type) {
      case PN_NULL:
        if (value.getType() != qpid::types::VAR_VOID) value = qpid::types::Variant();
        return true;
      case PN_BOOL:
        value = pn_data_get_bool(data);
        return true;
      case PN_UBYTE:
        value = pn_data_get_ubyte(data);
        return true;
      case PN_BYTE:
        value = pn_data_get_byte(data);
        return true;
      case PN_USHORT:
        value = pn_data_get_ushort(data);
        return true;
      case PN_SHORT:
        value = pn_data_get_short(data);
        return true;
      case PN_UINT:
        value = pn_data_get_uint(data);
        return true;
      case PN_INT:
        value = pn_data_get_int(data);
        return true;
      case PN_CHAR:
        value = pn_data_get_char(data);
        return true;
      case PN_ULONG:
        value = pn_data_get_ulong(data);
        return true;
      case PN_LONG:
        value = pn_data_get_long(data);
        return true;
      case PN_TIMESTAMP:
        value = pn_data_get_timestamp(data);
        return true;
      case PN_FLOAT:
        value = pn_data_get_float(data);
        return true;
      case PN_DOUBLE:
        value = pn_data_get_double(data);
        return true;
      case PN_UUID:
        value = qpid::types::Uuid(pn_data_get_uuid(data).bytes);
        return true;
      case PN_BINARY:
        value = string(pn_data_get_binary(data));
        value.setEncoding(qpid::types::encodings::BINARY);
        return true;
      case PN_STRING:
        value = string(pn_data_get_string(data));
        value.setEncoding(qpid::types::encodings::UTF8);
        return true;
      case PN_SYMBOL:
        value = string(pn_data_get_string(data));
        value.setEncoding(qpid::types::encodings::ASCII);
        return true;
      case PN_LIST:
        value = qpid::types::Variant::List();
        getList(value.asList());
        return true;
        break;
      case PN_MAP:
        value = qpid::types::Variant::Map();
        getMap(value.asMap());
        return true;
      case PN_ARRAY:
        value = qpid::types::Variant::List();
        getArray(value.asList());
        return true;
      case PN_DESCRIBED:
        // TODO aconway 2014-11-20: get described values.
      case PN_DECIMAL32:
      case PN_DECIMAL64:
      case PN_DECIMAL128:
      default:
        return false;
    }
}

pn_bytes_t PnData::bytes(const std::string& s)
{
    pn_bytes_t result;
    result.start = const_cast<char*>(s.data());
    result.size = s.size();
    return result;
}

std::string PnData::string(const pn_bytes_t& in)
{
    return std::string(in.start, in.size);
}

}}} // namespace qpid::messaging::amqp
