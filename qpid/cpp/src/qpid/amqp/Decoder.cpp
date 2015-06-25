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
#include "qpid/amqp/Decoder.h"
#include "qpid/amqp/CharSequence.h"
#include "qpid/amqp/Constructor.h"
#include "qpid/amqp/Descriptor.h"
#include "qpid/amqp/MapBuilder.h"
#include "qpid/amqp/Reader.h"
#include "qpid/amqp/typecodes.h"
#include "qpid/types/Uuid.h"
#include "qpid/types/Variant.h"
#include "qpid/log/Statement.h"
#include "qpid/Exception.h"

namespace qpid {
namespace amqp {

using namespace qpid::amqp::typecodes;

Decoder::Decoder(const char* d, size_t s) : start(d), size(s), position(0), current(0) {}

void Decoder::readMap(qpid::types::Variant::Map& map)
{
    MapBuilder builder;
    read(builder);
    map = builder.getMap();
}

qpid::types::Variant::Map Decoder::readMap()
{
    qpid::types::Variant::Map map;
    readMap(map);
    return map;
}

void Decoder::read(Reader& reader)
{
    while (available() && reader.proceed()) {
        readOne(reader);
    }
}

void Decoder::readOne(Reader& reader)
{
    const char* temp = start + position;
    current = position;
    Constructor c = readConstructor();
    if (c.isDescribed) reader.onDescriptor(c.descriptor, temp);
    readValue(reader, c.code, c.isDescribed ? &c.descriptor : 0);
}

void Decoder::readValue(Reader& reader, uint8_t code, const Descriptor* descriptor)
{
    switch(code) {
      case NULL_VALUE:
        reader.onNull(descriptor);
        break;
      case BOOLEAN:
        reader.onBoolean(readBoolean(), descriptor);
        break;
      case BOOLEAN_TRUE:
        reader.onBoolean(true, descriptor);
        break;
      case BOOLEAN_FALSE:
        reader.onBoolean(false, descriptor);
        break;
      case UBYTE:
        reader.onUByte(readUByte(), descriptor);
        break;
      case USHORT:
        reader.onUShort(readUShort(), descriptor);
        break;
      case UINT:
        reader.onUInt(readUInt(), descriptor);
        break;
      case UINT_SMALL:
        reader.onUInt(readUByte(), descriptor);
        break;
      case UINT_ZERO:
        reader.onUInt(0, descriptor);
        break;
      case ULONG:
        reader.onULong(readULong(), descriptor);
        break;
      case ULONG_SMALL:
        reader.onULong(readUByte(), descriptor);
        break;
      case ULONG_ZERO:
        reader.onULong(0, descriptor);
        break;
      case BYTE:
        reader.onByte(readByte(), descriptor);
        break;
      case SHORT:
        reader.onShort(readShort(), descriptor);
        break;
      case INT:
        reader.onInt(readInt(), descriptor);
        break;
      case INT_SMALL:
        reader.onInt(readByte(), descriptor);
        break;
      case LONG:
        reader.onLong(readLong(), descriptor);
        break;
      case LONG_SMALL:
        reader.onLong(readByte(), descriptor);
        break;
      case FLOAT:
        reader.onFloat(readFloat(), descriptor);
        break;
      case DOUBLE:
        reader.onDouble(readDouble(), descriptor);
        break;
      case UUID:
        reader.onUuid(readRawUuid(), descriptor);
        break;
      case TIMESTAMP:
        reader.onTimestamp(readLong(), descriptor);
        break;

      case BINARY8:
        reader.onBinary(readSequence8(), descriptor);
        break;
      case BINARY32:
        reader.onBinary(readSequence32(), descriptor);
        break;
      case STRING8:
        reader.onString(readSequence8(), descriptor);
        break;
      case STRING32:
        reader.onString(readSequence32(), descriptor);
        break;
      case SYMBOL8:
        reader.onSymbol(readSequence8(), descriptor);
        break;
      case SYMBOL32:
        reader.onSymbol(readSequence32(), descriptor);
        break;

      case LIST0:
        reader.onStartList(0, CharSequence::create(), getCurrent(0), descriptor);
        reader.onEndList(0, descriptor);
        break;
      case LIST8:
        readList8(reader, descriptor);
        break;
      case LIST32:
        readList32(reader, descriptor);
        break;
      case MAP8:
        readMap8(reader, descriptor);
        break;
      case MAP32:
        readMap32(reader, descriptor);
        break;
      case ARRAY8:
        readArray8(reader, descriptor);
        break;
      case ARRAY32:
        readArray32(reader, descriptor);
        break;
      default:
        break;
    }
}

void Decoder::readList8(Reader& reader, const Descriptor* descriptor)
{
    uint8_t size = readUByte();
    uint8_t count = readUByte();
    readList(reader, size-sizeof(size), count, descriptor);
}

void Decoder::readList32(Reader& reader, const Descriptor* descriptor)
{
    uint32_t size = readUInt();
    uint32_t count = readUInt();
    readList(reader, size-sizeof(size), count, descriptor);
}

void Decoder::readMap8(Reader& reader, const Descriptor* descriptor)
{
    uint8_t size = readUByte();
    uint8_t count = readUByte();
    readMap(reader, size-sizeof(size), count, descriptor);
}

void Decoder::readMap32(Reader& reader, const Descriptor* descriptor)
{
    uint32_t size = readUInt();
    uint32_t count = readUInt();
    readMap(reader, size-sizeof(size), count, descriptor);
}

void Decoder::readArray8(Reader& reader, const Descriptor* descriptor)
{
    uint8_t size = readUByte();
    uint8_t count = readUByte();
    readArray(reader, size-sizeof(size), count, descriptor);
}

void Decoder::readArray32(Reader& reader, const Descriptor* descriptor)
{
    uint32_t size = readUInt();
    uint32_t count = readUInt();
    readArray(reader, size-sizeof(size), count, descriptor);
}

void Decoder::readList(Reader& reader, uint32_t size, uint32_t count, const Descriptor* descriptor)
{
    if (reader.onStartList(count, CharSequence::create(data(), size), getCurrent(size), descriptor)) {
        for (uint32_t i = 0; i < count; ++i) {
            readOne(reader);
        }
        reader.onEndList(count, descriptor);
    } else {
        //skip
        advance(size);
    }
}
void Decoder::readMap(Reader& reader, uint32_t size, uint32_t count, const Descriptor* descriptor)
{
    if (reader.onStartMap(count, CharSequence::create(data(), size), getCurrent(size), descriptor)) {
        for (uint32_t i = 0; i < count; ++i) {
            readOne(reader);
        }
        reader.onEndMap(count, descriptor);
    } else {
        //skip
        advance(size);
    }
}

void Decoder::readArray(Reader& reader, uint32_t size, uint32_t count, const Descriptor* descriptor)
{
    size_t temp = position;
    Constructor constructor = readConstructor();
    CharSequence raw = CharSequence::create(data(), size-(position-temp));
    if (reader.onStartArray(count, raw, constructor, descriptor)) {
        for (uint32_t i = 0; i < count; ++i) {
            readValue(reader, constructor.code, constructor.isDescribed ? &constructor.descriptor : 0);
        }
        reader.onEndArray(count, descriptor);
    } else {
        //skip
        advance(raw.size);
    }
}


Constructor Decoder::readConstructor()
{
    Constructor result(readCode());
    if (result.code == DESCRIPTOR) {
        result.isDescribed = true;
        result.descriptor = readDescriptor();
        result.code = readCode();
        for (Descriptor* d = &result.descriptor; result.code == DESCRIPTOR; result.code = readCode()) {
            d = d->nest(readDescriptor());
        }
    } else {
        result.isDescribed = false;
    }
    return result;
}

Descriptor Decoder::readDescriptor()
{
    uint8_t code = readCode();
    switch(code) {
      case SYMBOL8:
        return Descriptor(readSequence8());
      case SYMBOL32:
        return Descriptor(readSequence32());
      case ULONG:
        return Descriptor(readULong());
      case ULONG_SMALL:
        return Descriptor((uint64_t) readUByte());
      case ULONG_ZERO:
        return Descriptor((uint64_t) 0);
      default:
        throw qpid::Exception(QPID_MSG("Expected descriptor of type ulong or symbol; found " << (int)code));
    }
}

void Decoder::advance(size_t n)
{
    if (n > available()) throw qpid::Exception(QPID_MSG("Out of Bounds: requested advance of " << n << " at " << position << " but only " << available() << " available"));
    position += n;
}

const char* Decoder::data()
{
    return start + position;
}

size_t Decoder::available()
{
    return size - position;
}

uint8_t Decoder::readCode()
{
    return readUByte();
}

bool Decoder::readBoolean()
{
    return readUByte();
}

uint8_t Decoder::readUByte()
{
    return static_cast<uint8_t>(start[position++]);
}

uint16_t Decoder::readUShort()
{
    uint16_t hi = (unsigned char) start[position++];
    hi = hi << 8;
    hi |= (unsigned char) start[position++];
    return hi;
}

uint32_t Decoder::readUInt()
{
    uint32_t a = (unsigned char) start[position++];
    uint32_t b = (unsigned char) start[position++];
    uint32_t c = (unsigned char) start[position++];
    uint32_t d = (unsigned char) start[position++];
    a = a << 24;
    a |= b << 16;
    a |= c << 8;
    a |= d;
    return a;
}

uint64_t Decoder::readULong()
{
    uint64_t hi =readUInt();
    uint64_t lo = readUInt();
    hi = hi << 32;
    return hi | lo;
}

int8_t Decoder::readByte()
{
    return (int8_t) readUByte();
}

int16_t Decoder::readShort()
{
    return (int16_t) readUShort();
}

int32_t Decoder::readInt()
{
    return (int32_t) readUInt();
}

int64_t Decoder::readLong()
{
    return (int64_t) readULong();
}

float Decoder::readFloat()
{
    union {
        uint32_t i;
        float    f;
    } val;
    val.i = readUInt();
    return val.f;
}

double Decoder::readDouble()
{
    union {
        uint64_t i;
        double   f;
    } val;
    val.i = readULong();
    return val.f;
}

CharSequence Decoder::readSequence8()
{
    CharSequence s;
    s.size = readUByte();
    s.data = start + position;
    advance(s.size);
    return s;
}

CharSequence Decoder::readSequence32()
{
    CharSequence s;
    s.size = readUInt();
    s.data = start + position;
    advance(s.size);
    return s;
}

qpid::types::Uuid Decoder::readUuid()
{
    qpid::types::Uuid uuid(start + position);
    advance(16);
    return uuid;
}

CharSequence Decoder::readRawUuid()
{
    CharSequence s;
    s.data = start + position;
    s.size = 16;
    advance(s.size);
    return s;
}

size_t Decoder::getPosition() const { return position; }
size_t Decoder::getSize() const { return size; }
void Decoder::resetSize(size_t s) { size = s; }

CharSequence Decoder::getCurrent(size_t remaining) const
{
    return CharSequence::create(start + current, (position-current)+remaining);
}

}} // namespace qpid::amqp
