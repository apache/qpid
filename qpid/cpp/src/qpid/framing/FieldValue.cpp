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
#include "Buffer.h"
#include "qpid/QpidError.h"


namespace qpid {
namespace framing {

/*
 * Specialisations for construction from integers
 */
template<>
FixedWidthValue<8>::FixedWidthValue(uint64_t v)
{
    octets[7] = (uint8_t) (0xFF & v); v >>= 8;
    octets[6] = (uint8_t) (0xFF & v); v >>= 8;
    octets[5] = (uint8_t) (0xFF & v); v >>= 8;
    octets[4] = (uint8_t) (0xFF & v); v >>= 8;
    octets[3] = (uint8_t) (0xFF & v); v >>= 8;
    octets[2] = (uint8_t) (0xFF & v); v >>= 8;
    octets[1] = (uint8_t) (0xFF & v); v >>= 8;
    octets[0] = (uint8_t) (0xFF & v);
}

template<>
FixedWidthValue<4>::FixedWidthValue(uint64_t v)
{
    octets[3] = (uint8_t) (0xFF & v); v >>= 8;
    octets[2] = (uint8_t) (0xFF & v); v >>= 8;
    octets[1] = (uint8_t) (0xFF & v); v >>= 8;
    octets[0] = (uint8_t) (0xFF & v);
}

template<>
FixedWidthValue<2>::FixedWidthValue(uint64_t v)
{
    octets[1] = (uint8_t) (0xFF & v); v >>= 8;
    octets[0] = (uint8_t) (0xFF & v);
}

template<>
FixedWidthValue<1>::FixedWidthValue(uint64_t v)
{
    octets[0] = (uint8_t) (0xFF & v);
}

/*
 * Specialisations for turning into integers
 */
template<>
int64_t FixedWidthValue<8>::getInt() const
{
    int64_t v = 0;
    v |= octets[0]; v <<= 8;
    v |= octets[1]; v <<= 8;
    v |= octets[2]; v <<= 8;
    v |= octets[3]; v <<= 8;
    v |= octets[4]; v <<= 8;
    v |= octets[5]; v <<= 8;
    v |= octets[6]; v <<= 8;
    v |= octets[7];
    return v;
}

template<>
int64_t FixedWidthValue<4>::getInt() const
{
    int64_t v = 0;
    v |= octets[0]; v <<= 8;
    v |= octets[1]; v <<= 8;
    v |= octets[2]; v <<= 8;
    v |= octets[3];
    return v;
}

template<>
int64_t FixedWidthValue<2>::getInt() const
{
    int64_t v = 0;
    v |= octets[0]; v <<= 8;
    v |= octets[1];
    return v;
}

template<>
int64_t FixedWidthValue<1>::getInt() const
{
    int64_t v = 0;
    v |= octets[0];
    return v;
}

/*
 * Specialisations for convertion to int predicate
 */
template<>
bool FixedWidthValue<8>::convertsToInt() const
{
    return true;
}

template<>
bool FixedWidthValue<4>::convertsToInt() const
{
    return true;
}

template<>
bool FixedWidthValue<2>::convertsToInt() const
{
    return true;
}

template<>
bool FixedWidthValue<1>::convertsToInt() const
{
    return true;
}

void FieldValue::decode(Buffer& buffer)
{
    typeOctet = buffer.getOctet();
    
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
        std::stringstream out;
        out << "Unknown field table value type: " << typeOctet;
        THROW_QPID_ERROR(FRAMING_ERROR, out.str());
    }
    data->decode(buffer);
}

template<>
bool FieldValue::convertsTo<int>() const
{
    return data->convertsToInt();
}

template<>
bool FieldValue::convertsTo<string>() const
{
    return data->convertsToString();
}

template<>
int FieldValue::get<int>() const
{
    return data->getInt();
}

template<>
std::string FieldValue::get<std::string>() const
{
    return data->getString();
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

StringValue::StringValue(const std::string& v) :
    FieldValue(
        0xA4,
        new VariableWidthValue<4>(
            reinterpret_cast<const uint8_t*>(v.data()),
            reinterpret_cast<const uint8_t*>(v.data()+v.size())))
{
}

IntegerValue::IntegerValue(int v) :
    FieldValue(0x21, new FixedWidthValue<4>(v))
{
}

TimeValue::TimeValue(uint64_t v) :
    FieldValue(0x32, new FixedWidthValue<8>(v))
{
}

FieldTableValue::FieldTableValue(const FieldTable&) : FieldValue()
{
}

}}
