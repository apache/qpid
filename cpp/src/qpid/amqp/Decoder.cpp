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
#include "qpid/amqp/Reader.h"
#include "qpid/amqp/typecodes.h"
#include "qpid/types/Uuid.h"
#include "qpid/types/Variant.h"
#include "qpid/log/Statement.h"
#include "qpid/Exception.h"

namespace qpid {
namespace amqp {

using namespace qpid::amqp::typecodes;

Decoder::Decoder(const char* d, size_t s) : start(d), size(s), position(0) {}

namespace {
class MapBuilder : public Reader
{
  public:
    void onNull(const Descriptor*)
    {
        qpid::types::Variant v;
        handle(v, NULL_NAME);
    }
    void onBoolean(bool v, const Descriptor*)
    {
        handle(v, BOOLEAN_NAME);
    }
    void onUByte(uint8_t v, const Descriptor*)
    {
        handle(v, UBYTE_NAME);
    }
    void onUShort(uint16_t v, const Descriptor*)
    {
        handle(v, USHORT_NAME);
    }
    void onUInt(uint32_t v, const Descriptor*)
    {
        handle(v, UINT_NAME);
    }
    void onULong(uint64_t v, const Descriptor*)
    {
        handle(v, ULONG_NAME);
    }
    void onByte(int8_t v, const Descriptor*)
    {
        handle(v, BYTE_NAME);
    }
    void onShort(int16_t v, const Descriptor*)
    {
        handle(v, SHORT_NAME);
    }
    void onInt(int32_t v, const Descriptor*)
    {
        handle(v, INT_NAME);
    }
    void onLong(int64_t v, const Descriptor*)
    {
        handle(v, LONG_NAME);
    }
    void onFloat(float v, const Descriptor*)
    {
        handle(v, FLOAT_NAME);
    }
    void onDouble(double v, const Descriptor*)
    {
        handle(v, DOUBLE_NAME);
    }
    void onUuid(const CharSequence& v, const Descriptor*)
    {
        handle(v, UUID_NAME);
    }
    void onTimestamp(int64_t v, const Descriptor*)
    {
        handle(v, TIMESTAMP_NAME);
    }
    void onBinary(const CharSequence& v, const Descriptor*)
    {
        handle(v);
    }
    void onString(const CharSequence& v, const Descriptor*)
    {
        handle(v);
    }
    void onSymbol(const CharSequence& v, const Descriptor*)
    {
        handle(v);
    }
    MapBuilder(qpid::types::Variant::Map& m) : map(m), state(KEY) {}
  private:
    qpid::types::Variant::Map& map;
    enum {KEY, SKIP, VALUE} state;
    std::string key;

    template <typename T> void handle(T value, const std::string& name)
    {
        switch (state) {
          case KEY:
            QPID_LOG(warning, "Ignoring key of type " << name);
            state = SKIP;
            break;
          case VALUE:
            map[key] = value;
          case SKIP:
            state = KEY;
            break;
        }
    }
    void handle(const CharSequence& value)
    {
        switch (state) {
          case KEY:
            key = value.str();
            state = VALUE;
            break;
          case VALUE:
            map[key] = value.str();
          case SKIP:
            state = KEY;
            break;
        }
    }
};
}
void Decoder::readMap(qpid::types::Variant::Map& map)
{
    MapBuilder builder(map);
    read(builder);
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
        reader.onStartList(0, CharSequence::create(), descriptor);
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
    if (reader.onStartList(count, CharSequence::create(data(), size), descriptor)) {
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
    if (reader.onStartMap(count, CharSequence::create(data(), size), descriptor)) {
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
        throw qpid::Exception(QPID_MSG("Expected descriptor of type ulong or symbol; found " << code));
    }
}

void Decoder::advance(size_t n)
{
    if (n > available()) throw qpid::Exception(QPID_MSG("Out of Bounds"));
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
}} // namespace qpid::amqp
