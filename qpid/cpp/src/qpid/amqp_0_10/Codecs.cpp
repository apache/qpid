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
FieldTable::value_type toFieldTableEntry(const Variant::Map::value_type& in);
Variant toVariant(boost::shared_ptr<FieldValue> in);
boost::shared_ptr<FieldValue> toFieldValue(const Variant& in);

template <class T, class U, class F> void translate(boost::shared_ptr<FieldValue> in, U& u, F f) 
{
    T t;
    getEncodedValue<T>(in, t);
    convert(t, u, f);
}

template <class T, class U, class F> T* toFieldValueCollection(const U& u, F f) 
{
    typename T::ValueType t;
    convert(u, t, f);
    return new T(t);
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
      case 0x01: out.setEncoding(amqp0_10_binary);
      case 0x02: out = in->getIntegerValue<int8_t, 1>(); break;
      case 0x03: out = in->getIntegerValue<uint8_t, 1>(); break;
      case 0x04: break; //TODO: iso-8859-15 char
      case 0x08: out = static_cast<bool>(in->getIntegerValue<uint8_t, 1>()); break;
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

boost::shared_ptr<FieldValue> convertString(const std::string& value, const std::string& encoding)
{
    bool large = value.size() > std::numeric_limits<uint16_t>::max();
    if (encoding.empty() || encoding == amqp0_10_binary || encoding == binary) {
        if (large) {
            return boost::shared_ptr<FieldValue>(new Var32Value(value, 0xa0));
        } else {
            return boost::shared_ptr<FieldValue>(new Var16Value(value, 0x90));
        }
    } else if (encoding == utf8 && !large) {
            return boost::shared_ptr<FieldValue>(new Str16Value(value));
    } else if (encoding == utf16 && !large) {
        return boost::shared_ptr<FieldValue>(new Var16Value(value, 0x96));
    } else if (encoding == iso885915 && !large) {
        return boost::shared_ptr<FieldValue>(new Var16Value(value, 0x94));
    } else {
        //either the string is too large for the encoding in amqp 0-10, or the encoding was not recognised
        QPID_LOG(warning, "Could not encode " << value.size() << " byte value as " << encoding << ", encoding as vbin32.");
        return boost::shared_ptr<FieldValue>(new Var32Value(value, 0xa0));
    }
}

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
        break;
    }
    return out;
}

Variant::Map::value_type toVariantMapEntry(const FieldTable::value_type& in)
{
    return Variant::Map::value_type(in.first, toVariant(in.second));
}

FieldTable::value_type toFieldTableEntry(const Variant::Map::value_type& in)
{
    return FieldTable::value_type(in.first, toFieldValue(in.second));
}

struct EncodeBuffer
{
    char* data;
    Buffer buffer;

    EncodeBuffer(size_t size) : data(new char[size]), buffer(data, size) {}
    ~EncodeBuffer() { delete[] data; }

    template <class T> void encode(T& t) { t.encode(buffer); }

    void getData(std::string& s) { 
        s.assign(data, buffer.getSize()); 
    }
};

struct DecodeBuffer
{
    Buffer buffer;

    DecodeBuffer(const std::string& s) : buffer(const_cast<char*>(s.data()), s.size()) {}

    template <class T> void decode(T& t) { t.decode(buffer); }
    
};

template <class T, class U, class F> void _encode(const U& value, std::string& data, F f)
{
    T t;
    convert(value, t, f);
    EncodeBuffer buffer(t.encodedSize());
    buffer.encode(t);
    buffer.getData(data);
}

template <class T, class U, class F> void _decode(const std::string& data, U& value, F f)
{
    T t;
    DecodeBuffer buffer(data);
    buffer.decode(t);
    convert(t, value, f);
}

void MapCodec::encode(const Variant::Map& value, std::string& data)
{
    _encode<FieldTable>(value, data, &toFieldTableEntry);
}

void MapCodec::decode(const std::string& data, Variant::Map& value)
{
    _decode<FieldTable>(data, value, &toVariantMapEntry);
}

void ListCodec::encode(const Variant::List& value, std::string& data)
{
    _encode<List>(value, data, &toFieldValue);
}

void ListCodec::decode(const std::string& data, Variant::List& value)
{
    _decode<List>(data, value, &toVariant);
}

void translate(const Variant::Map& from, FieldTable& to)
{
    convert(from, to, &toFieldTableEntry);
}

void translate(const FieldTable& from, Variant::Map& to)
{
    convert(from, to, &toVariantMapEntry);
}

const std::string ListCodec::contentType("amqp/list");
const std::string MapCodec::contentType("amqp/map");

}} // namespace qpid::amqp_0_10
