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
#include "FieldValue.h"
#include "Array.h"
#include "Buffer.h"
#include "Endian.h"
#include "qpid/framing/reply_exceptions.h"

namespace qpid {
namespace framing {

uint8_t FieldValue::getType()
{
    return typeOctet;
}

void FieldValue::setType(uint8_t type)
{
    typeOctet = type;
    if (typeOctet == 0xA8) {
        data.reset(new EncodedValue<FieldTable>());
    } else if (typeOctet == 0xAA) {
        data.reset(new EncodedValue<Array>());
    } else {    
        uint8_t lenType = typeOctet >> 4;
        switch(lenType){
          case 0:
            data.reset(new FixedWidthValue<1>());
            break;
          case 1:
            data.reset(new FixedWidthValue<2>());
            break;
          case 2:
            data.reset(new FixedWidthValue<4>());
            break;
          case 3:
            data.reset(new FixedWidthValue<8>());
            break;
          case 4:
            data.reset(new FixedWidthValue<16>());
            break;
          case 5:
            data.reset(new FixedWidthValue<32>());
            break;
          case 6:
            data.reset(new FixedWidthValue<64>());
            break;
          case 7:
            data.reset(new FixedWidthValue<128>());
            break;
          case 8:
            data.reset(new VariableWidthValue<1>());
            break;
          case 9:
            data.reset(new VariableWidthValue<2>());
            break;
          case 0xA:
            data.reset(new VariableWidthValue<4>());
            break;
          case 0xC:
            data.reset(new FixedWidthValue<5>());
            break;
          case 0xD:
            data.reset(new FixedWidthValue<9>());
            break;
          case 0xF:
            data.reset(new FixedWidthValue<0>());
            break;
          default:
            throw IllegalArgumentException(QPID_MSG("Unknown field table value type: " << (int)typeOctet));
        }
    }
}

void FieldValue::decode(Buffer& buffer)
{
    setType(buffer.getOctet());
    data->decode(buffer);
}

void FieldValue::encode(Buffer& buffer)
{
    buffer.putOctet(typeOctet);
    data->encode(buffer);
}

bool FieldValue::operator==(const FieldValue& v) const
{
    return
        typeOctet == v.typeOctet &&
        *data == *v.data;
}

Str8Value::Str8Value(const std::string& v) :
    FieldValue(
        TYPE_CODE_STR8,
        new VariableWidthValue<1>(
            reinterpret_cast<const uint8_t*>(v.data()),
            reinterpret_cast<const uint8_t*>(v.data()+v.size())))
{
}

Str16Value::Str16Value(const std::string& v) :
    FieldValue(
        0x95,
        new VariableWidthValue<2>(
            reinterpret_cast<const uint8_t*>(v.data()),
            reinterpret_cast<const uint8_t*>(v.data()+v.size())))
{}

Struct32Value::Struct32Value(const std::string& v) :
    FieldValue(
        0xAB,
        new VariableWidthValue<4>(
            reinterpret_cast<const uint8_t*>(v.data()),
            reinterpret_cast<const uint8_t*>(v.data()+v.size())))
{}

IntegerValue::IntegerValue(int v) :
    FieldValue(0x21, new FixedWidthValue<4>(v))
{}

FloatValue::FloatValue(float v) :
    FieldValue(0x23, new FixedWidthValue<4>(Endian::convertIfRequired(reinterpret_cast<uint8_t*>(&v), 4)))
{}

DoubleValue::DoubleValue(double v) :
    FieldValue(0x33, new FixedWidthValue<8>(Endian::convertIfRequired(reinterpret_cast<uint8_t*>(&v), 8)))
{}

Integer64Value::Integer64Value(int64_t v) :
    FieldValue(0x31, new FixedWidthValue<8>(v))
{}

Unsigned64Value::Unsigned64Value(uint64_t v) :
    FieldValue(0x32, new FixedWidthValue<8>(v))
{}


TimeValue::TimeValue(uint64_t v) :
    FieldValue(0x38, new FixedWidthValue<8>(v))
{
}

FieldTableValue::FieldTableValue(const FieldTable& f) : FieldValue(0xa8, new EncodedValue<FieldTable>(f))
{
}

ArrayValue::ArrayValue(const Array& a) : FieldValue(0xaa, new EncodedValue<Array>(a))
{
}

void FieldValue::print(std::ostream& out) const {
    data->print(out);
    out << TypeCode(typeOctet) << '(';
    if (data->convertsToString()) out << data->getString();
    else if (data->convertsToInt()) out << data->getInt();
    else data->print(out);
    out << ')';
}

}}
