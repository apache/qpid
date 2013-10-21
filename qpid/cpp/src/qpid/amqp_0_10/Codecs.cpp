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
#include "qpid/amqp_0_10/Codecs.h"
#include "qpid/amqp_0_10/CodecsInternal.h"
#include "qpid/framing/Array.h"
#include "qpid/framing/Buffer.h"
#include "qpid/framing/FieldTable.h"
#include "qpid/framing/FieldValue.h"
#include "qpid/framing/List.h"
#include "qpid/log/Statement.h"
#include <algorithm>
#include <functional>
#include <limits>

using namespace qpid::framing;
using namespace qpid::types;

namespace qpid {
namespace amqp_0_10 {

namespace {
const std::string iso885915("iso-8859-15");
const std::string utf8("utf8");
const std::string utf16("utf16");
const std::string binary("binary");
const std::string amqp0_10_binary("amqp0-10:binary");
const std::string amqp0_10_bit("amqp0-10:bit");
const std::string amqp0_10_datetime("amqp0-10:datetime");
const std::string amqp0_10_struct("amqp0-10:struct");
}

template <class T, class U, class F> void convert(const T& from, U& to, F f)
{
    std::transform(from.begin(), from.end(), std::inserter(to, to.begin()), f);
}

Variant::Map::value_type toVariantMapEntry(const FieldTable::value_type& in);

template <class T, class U, class F> void translate(boost::shared_ptr<FieldValue> in, U& u, F f) 
{
    T t;
    getEncodedValue<T>(in, t);
    convert(t, u, f);
}

void setEncodingFor(Variant& out, uint8_t code)
{
    switch(code){
      case 0x80: 
      case 0x90: 
      case 0xa0:
        out.setEncoding(amqp0_10_binary);
        break;
      case 0x84:
      case 0x94:
        out.setEncoding(iso885915);
        break;
      case 0x85:
      case 0x95:
        out.setEncoding(utf8);
        break;
      case 0x86:
      case 0x96: 
        out.setEncoding(utf16);
        break;
      case 0xab: 
        out.setEncoding(amqp0_10_struct);
        break;
      default:
        //do nothing
        break;
    }
}

qpid::types::Uuid getUuid(FieldValue& value)
{
    unsigned char data[16];
    value.getFixedWidthValue<16>(data);
    return qpid::types::Uuid(data);
}

Variant toVariant(boost::shared_ptr<FieldValue> in)
{
    Variant out;
    //based on AMQP 0-10 typecode, pick most appropriate variant type
    switch (in->getType()) {
        //Fixed Width types:
      case 0x00: out.setEncoding(amqp0_10_binary);
      case 0x01: out = in->getIntegerValue<int8_t>(); break;
      case 0x02: out = in->getIntegerValue<uint8_t>(); break;
      case 0x04: break; //TODO: iso-8859-15 char
      case 0x08: out = static_cast<bool>(in->getIntegerValue<uint8_t>()); break;
      case 0x10: out.setEncoding(amqp0_10_binary);
      case 0x11: out = in->getIntegerValue<int16_t, 2>(); break;
      case 0x12: out = in->getIntegerValue<uint16_t, 2>(); break;
      case 0x20: out.setEncoding(amqp0_10_binary);
      case 0x21: out = in->getIntegerValue<int32_t, 4>(); break;
      case 0x22: out = in->getIntegerValue<uint32_t, 4>(); break;
      case 0x23: out = in->get<float>(); break;
      case 0x27: break; //TODO: utf-32 char
      case 0x30: out.setEncoding(amqp0_10_binary);
      case 0x31: out = in->getIntegerValue<int64_t, 8>(); break;
      case 0x38: out.setEncoding(amqp0_10_datetime); //treat datetime as uint64_t, but set encoding
      case 0x32: out = in->getIntegerValue<uint64_t, 8>(); break;
      case 0x33: out = in->get<double>(); break;

      case 0x48: out = getUuid(*in); break;

        //TODO: figure out whether and how to map values with codes 0x40-0xd8

      case 0xf0: break;//void, which is the default value for Variant
      case 0xf1: out.setEncoding(amqp0_10_bit); break;//treat 'bit' as void, which is the default value for Variant

        //Variable Width types:
        //strings:
      case 0x80: 
      case 0x84:
      case 0x85:
      case 0x86:
      case 0x90:
      case 0x94:
      case 0x95:
      case 0x96: 
      case 0xa0:
      case 0xab:
        out = in->get<std::string>();
        setEncodingFor(out, in->getType());
        break;

      case 0xa8:
        out = Variant::Map();
        translate<FieldTable>(in, out.asMap(), &toVariantMapEntry);
        break;

      case 0xa9:
        out = Variant::List();
        translate<List>(in, out.asList(), &toVariant);
        break;
      case 0xaa: //convert amqp0-10 array into variant list
        out = Variant::List();
        translate<Array>(in, out.asList(), &toVariant);
        break;

      default:
        //error?
        break;
    }
    return out;
}

Variant::Map::value_type toVariantMapEntry(const FieldTable::value_type& in)
{
    return Variant::Map::value_type(in.first, toVariant(in.second));
}

struct DecodeBuffer
{
    Buffer buffer;

    DecodeBuffer(const std::string& s) : buffer(const_cast<char*>(s.data()), s.size()) {}

    template <class T> void decode(T& t) { t.decode(buffer); }

};

template <class T, class U, class F> void _decode(const std::string& data, U& value, F f)
{
    T t;
    DecodeBuffer buffer(data);
    buffer.decode(t);
    convert(t, value, f);
}

uint32_t encodedSize(const Variant& value)
{
    switch (value.getType()) {
      case VAR_VOID:
        return 0;
      case VAR_BOOL:
      case VAR_UINT8:
      case VAR_INT8:
        return 1;
      case VAR_UINT16:
      case VAR_INT16:
        return 2;
        break;
      case VAR_UINT32:
      case VAR_INT32:
      case VAR_FLOAT:
        return 4;
      case VAR_UINT64:
      case VAR_INT64:
      case VAR_DOUBLE:
        return 8;
      case VAR_UUID:
        return 16;
      case VAR_MAP:
        return encodedSize(value.asMap());
      case VAR_LIST:
        return encodedSize(value.asList());
      case VAR_STRING:
        return encodedSize(value.getString());
      default:
        throw Exception("Couldn't encode Variant: Illegal type code");
    }
}

uint32_t encodedSize(const Variant::Map& values)
{
    uint32_t size = 4/*size field*/ + 4/*count field*/;
    for(Variant::Map::const_iterator i = values.begin(); i != values.end(); ++i) {
        size += 1/*size of key*/ + (i->first).size() + 1/*typecode*/ + encodedSize(i->second);
    }
    return size;
}

uint32_t encodedSize(const Variant::Map& values, const std::string& efield, const Variant& evalue)
{
    uint32_t size = 4/*size field*/ + 4/*count field*/;
    for(Variant::Map::const_iterator i = values.begin(); i != values.end(); ++i) {
        size += 1/*size of key*/ + (i->first).size() + 1/*typecode*/ + encodedSize(i->second);
    }
    size += 1/*size of key*/ + efield.size() + 1/*typecode*/ + encodedSize(evalue);
    return size;
}

uint32_t encodedSize(const Variant::List& values)
{
    uint32_t size = 4/*size field*/ + 4/*count field*/;
    for(Variant::List::const_iterator i = values.begin(); i != values.end(); ++i) {
        size += 1/*typecode*/ + encodedSize(*i);
    }
    return size;
}

uint32_t encodedSize(const std::string& value)
{
    uint32_t size = value.size();
    if (size > std::numeric_limits<uint16_t>::max()) {
        return size + 4; /*Long size*/
    } else {
        return size + 2; /*Short size*/
    }
}

void encode(const std::string& value, const std::string& encoding, qpid::framing::Buffer& buffer)
{
    uint32_t size = value.size();
    if (size > std::numeric_limits<uint16_t>::max()) {
        if (encoding == utf8 || encoding == utf16 || encoding == iso885915) {
            throw Exception(QPID_MSG("Could not encode " << encoding << " character string - too long (" << size << " bytes)"));
        } else {
            buffer.putOctet(0xa0);
            buffer.putLong(size);
            buffer.putRawData(value);
        }
    } else {
        if (encoding == utf8) {
            buffer.putOctet(0x95);
        } else if (encoding == utf16) {
            buffer.putOctet(0x96);
        } else if (encoding == iso885915) {
            buffer.putOctet(0x94);
        } else {
            buffer.putOctet(0x90);
        }
        buffer.putShort(size);
        buffer.putRawData(value);
    }
}

void encode(const Variant& value, qpid::framing::Buffer& buffer)
{
    switch (value.getType()) {
      case VAR_VOID:
        buffer.putOctet(0xf0);
        break;
      case VAR_BOOL:
        buffer.putOctet(0x08);
        buffer.putOctet(value.asBool());
        break;
      case VAR_INT8:
        buffer.putOctet(0x01);
        buffer.putInt8(value.asInt8());
        break;
      case VAR_UINT8:
        buffer.putOctet(0x02);
        buffer.putOctet(value.asUint8());
        break;
      case VAR_INT16:
        buffer.putOctet(0x11);
        buffer.putInt16(value.asInt16());
        break;
      case VAR_UINT16:
        buffer.putOctet(0x12);
        buffer.putShort(value.asUint16());
        break;
      case VAR_INT32:
        buffer.putOctet(0x21);
        buffer.putInt32(value.asInt32());
        break;
      case VAR_UINT32:
        buffer.putOctet(0x22);
        buffer.putLong(value.asUint32());
        break;
      case VAR_FLOAT:
        buffer.putOctet(0x23);
        buffer.putFloat(value.asFloat());
        break;
      case VAR_INT64:
        buffer.putOctet(0x31);
        buffer.putInt64(value.asInt64());
        break;
      case VAR_UINT64:
        buffer.putOctet(0x32);
        buffer.putLongLong(value.asUint64());
        break;
      case VAR_DOUBLE:
        buffer.putOctet(0x33);
        buffer.putDouble(value.asDouble());
        break;
      case VAR_UUID:
        buffer.putOctet(0x48);
        buffer.putBin128(value.asUuid().data());
        break;
      case VAR_MAP:
        buffer.putOctet(0xa8);
        encode(value.asMap(), encodedSize(value.asMap()), buffer);
        break;
      case VAR_LIST:
        buffer.putOctet(0xa9);
        encode(value.asList(), encodedSize(value.asList()), buffer);
        break;
      case VAR_STRING:
        encode(value.getString(), value.getEncoding(), buffer);
        break;
    }
}

void encode(const Variant::Map& map, uint32_t len, qpid::framing::Buffer& buffer)
{
    uint32_t s = buffer.getPosition();
    buffer.putLong(len - 4);//exclusive of the size field itself
    buffer.putLong(map.size());
    for (Variant::Map::const_iterator i = map.begin(); i != map.end(); ++i) {
        buffer.putShortString(i->first);
    	encode(i->second, buffer);
    }
    (void) s; assert(s + len == buffer.getPosition());
}

void encode(const Variant::Map& map, const std::string& efield, const Variant& evalue, uint32_t len, qpid::framing::Buffer& buffer)
{
    uint32_t s = buffer.getPosition();
    buffer.putLong(len - 4);//exclusive of the size field itself
    buffer.putLong(map.size() + 1 /* The extra field */ );
    for (Variant::Map::const_iterator i = map.begin(); i != map.end(); ++i) {
        buffer.putShortString(i->first);
        encode(i->second, buffer);
    }
    buffer.putShortString(efield);
    encode(evalue, buffer);

    (void) s; assert(s + len == buffer.getPosition());
}

void encode(const Variant::List& list, uint32_t len, qpid::framing::Buffer& buffer)
{
    uint32_t s = buffer.getPosition();
    buffer.putLong(len - 4);//exclusive of the size field itself
    buffer.putLong(list.size());
    for (Variant::List::const_iterator i = list.begin(); i != list.end(); ++i) {
    	encode(*i, buffer);
    }
    (void) s; assert(s + len == buffer.getPosition());
}

void decode(qpid::framing::Buffer&, Variant::Map&)
{
}


void MapCodec::encode(const Variant::Map& value, std::string& data)
{
    uint32_t len = qpid::amqp_0_10::encodedSize(value);
    std::vector<char> space(len);
    qpid::framing::Buffer buff(&space[0], len);

    qpid::amqp_0_10::encode(value, len, buff);
    assert( len == buff.getPosition() );
    data.assign(&space[0], len);
}

void MapCodec::decode(const std::string& data, Variant::Map& value)
{
    _decode<FieldTable>(data, value, &toVariantMapEntry);
}

size_t MapCodec::encodedSize(const Variant::Map& value)
{
    return qpid::amqp_0_10::encodedSize(value);
}

void ListCodec::encode(const Variant::List& value, std::string& data)
{
    uint32_t len = qpid::amqp_0_10::encodedSize(value);
    std::vector<char> space(len);
    qpid::framing::Buffer buff(&space[0], len);

    qpid::amqp_0_10::encode(value, len, buff);
    assert( len == buff.getPosition() );
    data.assign(&space[0], len);
}

void ListCodec::decode(const std::string& data, Variant::List& value)
{
    _decode<List>(data, value, &toVariant);
}

size_t ListCodec::encodedSize(const Variant::List& value)
{
    return qpid::amqp_0_10::encodedSize(value);
}

void translate(const Variant::Map& from, FieldTable& to)
{
    // Create buffer of correct size to encode Variant::Map
    uint32_t len = encodedSize(from);
    std::vector<char> space(len);
    qpid::framing::Buffer buff(&space[0], len);

    // Encode Variant::Map into buffer directly -
    // We pass the already calculated length in to avoid
    // recalculating it.
    encode(from, len, buff);

    // Give buffer to FieldTable
    // Could speed this up a bit by avoiding copying
    // the buffer we just created into the FieldTable
    assert( len == buff.getPosition() );
    buff.reset();
    to.decode(buff);
}

void translate(const Variant::Map& from, const std::string& efield, const Variant& evalue, FieldTable& to)
{
    // Create buffer of correct size to encode Variant::Map
    uint32_t len = encodedSize(from, efield, evalue);
    std::vector<char> space(len);
    qpid::framing::Buffer buff(&space[0], len);

    // Encode Variant::Map into buffer directly -
    // We pass the already calculated length in to avoid
    // recalculating it.
    encode(from, efield, evalue, len, buff);

    // Give buffer to FieldTable
    // Could speed this up a bit by avoiding copying
    // the buffer we just created into the FieldTable
    assert( len == buff.getPosition() );
    buff.reset();
    to.decode(buff);
}

void translate(const FieldTable& from, Variant::Map& to)
{
    convert(from, to, &toVariantMapEntry);
}

namespace {
boost::shared_ptr<FieldValue> convertString(const std::string& value, const std::string& encoding);
FieldTableValue* toFieldTableValue(const Variant::Map& map);
ListValue* toListValue(const Variant::List& list);

boost::shared_ptr<FieldValue> toFieldValue(const Variant& in)
{
    boost::shared_ptr<FieldValue> out;
    switch (in.getType()) {
        case VAR_VOID: out = boost::shared_ptr<FieldValue>(new VoidValue()); break;
        case VAR_BOOL: out = boost::shared_ptr<FieldValue>(new BoolValue(in.asBool())); break;
        case VAR_UINT8: out = boost::shared_ptr<FieldValue>(new Unsigned8Value(in.asUint8())); break;
        case VAR_UINT16: out = boost::shared_ptr<FieldValue>(new Unsigned16Value(in.asUint16())); break;
        case VAR_UINT32: out = boost::shared_ptr<FieldValue>(new Unsigned32Value(in.asUint32())); break;
        case VAR_UINT64: out = boost::shared_ptr<FieldValue>(new Unsigned64Value(in.asUint64())); break;
        case VAR_INT8: out = boost::shared_ptr<FieldValue>(new Integer8Value(in.asInt8())); break;
        case VAR_INT16: out = boost::shared_ptr<FieldValue>(new Integer16Value(in.asInt16())); break;
        case VAR_INT32: out = boost::shared_ptr<FieldValue>(new Integer32Value(in.asInt32())); break;
        case VAR_INT64: out = boost::shared_ptr<FieldValue>(new Integer64Value(in.asInt64())); break;
        case VAR_FLOAT: out = boost::shared_ptr<FieldValue>(new FloatValue(in.asFloat())); break;
        case VAR_DOUBLE: out = boost::shared_ptr<FieldValue>(new DoubleValue(in.asDouble())); break;
        case VAR_STRING: out = convertString(in.asString(), in.getEncoding()); break;
        case VAR_UUID: out = boost::shared_ptr<FieldValue>(new UuidValue(in.asUuid().data())); break;
        case VAR_MAP:
            out = boost::shared_ptr<FieldValue>(toFieldTableValue(in.asMap()));
            break;
        case VAR_LIST:
            out = boost::shared_ptr<FieldValue>(toListValue(in.asList()));
    }
    return out;
}

boost::shared_ptr<FieldValue> convertString(const std::string& value, const std::string& encoding)
{
    bool large = value.size() > std::numeric_limits<uint16_t>::max();
    if (encoding.empty() || encoding == amqp0_10_binary || encoding == binary) {
        if (large) {
            return boost::shared_ptr<FieldValue>(new Var32Value(value, 0xa0));
        } else {
            return boost::shared_ptr<FieldValue>(new Var16Value(value, 0x90));
        }
    } else if (encoding == utf8) {
        if (!large)
            return boost::shared_ptr<FieldValue>(new Str16Value(value));
        throw Exception(QPID_MSG("Could not encode utf8 character string - too long (" << value.size() << " bytes)"));
    } else if (encoding == utf16) {
        if (!large)
            return boost::shared_ptr<FieldValue>(new Var16Value(value, 0x96));
        throw Exception(QPID_MSG("Could not encode utf16 character string - too long (" << value.size() << " bytes)"));
    } else if (encoding == iso885915) {
        if (!large)
            return boost::shared_ptr<FieldValue>(new Var16Value(value, 0x94));
        throw Exception(QPID_MSG("Could not encode iso-8859-15 character string - too long (" << value.size() << " bytes)"));
    } else {
        // the encoding was not recognised
        QPID_LOG(warning, "Unknown byte encoding: [" << encoding << "], encoding as vbin32.");
        return boost::shared_ptr<FieldValue>(new Var32Value(value, 0xa0));
    }
}

FieldTable::value_type toFieldTableEntry(const Variant::Map::value_type& in)
{
    return FieldTable::value_type(in.first, toFieldValue(in.second));
}

FieldTableValue* toFieldTableValue(const Variant::Map& map)
{
    FieldTable ft;
    convert(map, ft, &toFieldTableEntry);
    return new FieldTableValue(ft);
}

ListValue* toListValue(const Variant::List& list)
{
    List l;
    convert(list, l, &toFieldValue);
    return new ListValue(l);
}
}

void translate(const types::Variant& from, boost::shared_ptr<framing::FieldValue> to)
{
    to = toFieldValue(from);
}

void translate(const boost::shared_ptr<FieldValue> from, Variant& to)
{
    to = toVariant(from);
}

boost::shared_ptr<framing::FieldValue> translate(const types::Variant& from)
{
    return toFieldValue(from);
}

const std::string ListCodec::contentType("amqp/list");
const std::string MapCodec::contentType("amqp/map");

}} // namespace qpid::amqp_0_10
