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
#include "qpid/amqp/Encoder.h"
#include "qpid/amqp/CharSequence.h"
#include "qpid/amqp/Descriptor.h"
#include "qpid/amqp/typecodes.h"
#include "qpid/types/Uuid.h"
#include "qpid/types/Variant.h"
#include "qpid/types/encodings.h"
#include "qpid/log/Statement.h"
#include "qpid/Exception.h"
#include <assert.h>
#include <string.h>

using namespace qpid::types::encodings;
using qpid::types::Variant;

namespace qpid {
namespace amqp {

Encoder::Overflow::Overflow() : Exception("Buffer overflow in encoder!") {}

Encoder::Encoder(char* d, size_t s) : data(d), size(s), position(0), grow(false) {}

Encoder::Encoder() : data(0), size(0), position(0), grow(true) {}

namespace {
template <typename T> size_t encode(char* data, T i);
template <> size_t encode<uint8_t>(char* data, uint8_t i)
{
    *data = i;
    return 1;
}
template <> size_t encode<uint16_t>(char* data, uint16_t i)
{
    uint16_t b = i;
    size_t position(0);
    data[position++] = (uint8_t) (0xFF & (b >> 8));
    data[position++] = (uint8_t) (0xFF & b);
    return position;
}
template <> size_t encode<uint32_t>(char* data, uint32_t i)
{
    uint32_t b = i;
    size_t position(0);
    data[position++] = (uint8_t) (0xFF & (b >> 24));
    data[position++] = (uint8_t) (0xFF & (b >> 16));
    data[position++] = (uint8_t) (0xFF & (b >> 8));
    data[position++] = (uint8_t) (0xFF & b);
    return position;
}
template <> size_t encode<uint64_t>(char* data, uint64_t i)
{
    uint32_t hi = i >> 32;
    uint32_t lo = i;
    size_t r(0);
    r += encode(data, hi);
    r += encode(data + r, lo);
    return r;
}
template<typename T> struct Backfill
{
    T size;
    T count;
    char* location;
};

template<typename T> void end(T count, void* token, char* current)
{
    Backfill<T> b;
    b.location = (char*) token;
    b.size = (T) (current - b.location) - sizeof(b.size);
    b.count = count;
    b.location += encode<T>(b.location, b.size);
    encode<T>(b.location, b.count);
}
}
char* Encoder::skip(size_t n)
{
    char* current = data + position;
    check(n);
    position += n;
    return current;
}

void Encoder::write(bool b)
{
    check(sizeof(b));
    position += encode<uint8_t>(data+position, b ? 1u : 0u);
}
void Encoder::write(uint8_t i)
{
    check(sizeof(i));
    position += encode<uint8_t>(data+position, i);
}
void Encoder::write(uint16_t i)
{
    check(sizeof(i));
    position += encode<uint16_t>(data+position, i);
}
void Encoder::write(uint32_t i)
{
    check(sizeof(i));
    position += encode<uint32_t>(data+position, i);
}
void Encoder::write(uint64_t i)
{
    check(sizeof(i));
    position += encode<uint64_t>(data+position, i);
}
void Encoder::write(int8_t i)
{
    check(sizeof(i));
    position += encode(data+position, (uint8_t) i);
}
void Encoder::write(int16_t i)
{
    check(sizeof(i));
    position += encode(data+position, (uint16_t) i);
}
void Encoder::write(int32_t i)
{
    check(sizeof(i));
    position += encode(data+position, (uint32_t) i);
}
void Encoder::write(int64_t i)
{
    check(sizeof(i));
    position += encode(data+position, (uint64_t) i);
}
void Encoder::write(float f)
{
    check(sizeof(f));
    union {
        uint32_t i;
        float    f;
    } val;

    val.f = f;
    write(val.i);
}
void Encoder::write(double d)
{
    check(sizeof(d));
    union {
        uint64_t i;
        double   d;
    } val;

    val.d = d;
    write(val.i);
}
void Encoder::write(const qpid::types::Uuid& uuid)
{
    writeBytes((const char*) uuid.data(), uuid.size());
}

void Encoder::writeBytes(const char* bytes, size_t count)
{
    check(count);
    ::memcpy(data + position, bytes, count);
    position += count;
}

void Encoder::writeCode(uint8_t code)
{
    write(code);
}

void Encoder::writeNull(const Descriptor* d)
{
    if (d) writeDescriptor(*d);
    writeCode(typecodes::NULL_VALUE);
}
void Encoder::writeBoolean(bool b, const Descriptor* d)
{
    if (d) writeDescriptor(*d);
    writeCode(b ? typecodes::BOOLEAN_TRUE : typecodes::BOOLEAN_FALSE);
}
void Encoder::writeUByte(uint8_t i, const Descriptor* d)
{
    write(i, typecodes::UBYTE, d);
}

void Encoder::writeUShort(uint16_t i, const Descriptor* d)
{
    write(i, typecodes::USHORT, d);
}

void Encoder::writeUInt(uint32_t i, const Descriptor* d)
{
    if (i == 0) {
        if (d) writeDescriptor(*d);
        writeCode(typecodes::UINT_ZERO);
    } else {
        if (i < 256) {
            write((uint8_t) i, typecodes::UINT_SMALL, d);
        } else {
            write(i, typecodes::UINT, d);
        }
    }
}

void Encoder::writeULong(uint64_t i, const Descriptor* d)
{
    if (i == 0) {
        if (d) writeDescriptor(*d);
        writeCode(typecodes::ULONG_ZERO);
    } else {
        if (i < 256) {
            write((uint8_t) i, typecodes::ULONG_SMALL, d);
        } else {
            write(i, typecodes::ULONG, d);
        }
    }
}

void Encoder::writeByte(int8_t i, const Descriptor* d)
{
    write((uint8_t) i, typecodes::LONG, d);
}

void Encoder::writeShort(int16_t i, const Descriptor* d)
{
    write((uint16_t) i, typecodes::SHORT, d);
}

void Encoder::writeInt(int32_t i, const Descriptor* d)
{
    write((uint32_t) i, typecodes::INT, d);
}

void Encoder::writeLong(int64_t i, const Descriptor* d)
{
    write((uint64_t) i, typecodes::LONG, d);
}

void Encoder::writeTimestamp(int64_t t, const Descriptor* d)
{
    write((uint64_t) t, typecodes::TIMESTAMP, d);
}


void Encoder::writeFloat(float f, const Descriptor* d)
{
    write(f, typecodes::FLOAT, d);
}

void Encoder::writeDouble(double f, const Descriptor* d)
{
    write(f, typecodes::DOUBLE, d);
}

void Encoder::writeUuid(const qpid::types::Uuid& uuid, const Descriptor* d)
{
    write(uuid, typecodes::UUID, d);
}

void Encoder::write(const CharSequence& v, std::pair<uint8_t, uint8_t> codes, const Descriptor* d)
{
    if (d) writeDescriptor(*d);
    if (v.size < 256) {
        writeCode(codes.first);
        write((uint8_t) v.size);
    } else {
        writeCode(codes.second);
        write((uint32_t) v.size);
    }
    writeBytes(v.data, v.size);
}

void Encoder::write(const std::string& v, std::pair<uint8_t, uint8_t> codes, const Descriptor* d)
{
    if (d) writeDescriptor(*d);
    if (v.size() < 256) {
        writeCode(codes.first);
        write((uint8_t) v.size());
    } else {
        writeCode(codes.second);
        write((uint32_t) v.size());
    }
    writeBytes(v.data(), v.size());
}

void Encoder::writeSymbol(const CharSequence& v, const Descriptor* d)
{
    write(v, typecodes::SYMBOL, d);
}

void Encoder::writeSymbol(const std::string& v, const Descriptor* d)
{
    write(v, typecodes::SYMBOL, d);
}

void Encoder::writeString(const CharSequence& v, const Descriptor* d)
{
    write(v, typecodes::STRING, d);
}

void Encoder::writeString(const std::string& v, const Descriptor* d)
{
    write(v, typecodes::STRING, d);
}

void Encoder::writeBinary(const CharSequence& v, const Descriptor* d)
{
    write(v, typecodes::BINARY, d);
}

void Encoder::writeBinary(const std::string& v, const Descriptor* d)
{
    write(v, typecodes::BINARY, d);
}

void* Encoder::startList8(const Descriptor* d)
{
    return start<uint8_t>(typecodes::LIST8, d);
}

void* Encoder::startList32(const Descriptor* d)
{
    return start<uint32_t>(typecodes::LIST32, d);
}

void Encoder::endList8(uint8_t count, void* token)
{
    end<uint8_t>(count, token, data+position);
}

void Encoder::endList32(uint32_t count, void* token)
{
    end<uint32_t>(count, token, data+position);
}

void* Encoder::startMap8(const Descriptor* d)
{
    return start<uint8_t>(typecodes::MAP8, d);
}

void* Encoder::startMap32(const Descriptor* d)
{
    return start<uint32_t>(typecodes::MAP32, d);
}

void Encoder::endMap8(uint8_t count, void* token)
{
    end<uint8_t>(count, token, data+position);
}

void Encoder::endMap32(uint32_t count, void* token)
{
    end<uint32_t>(count, token, data+position);
}

void* Encoder::startArray8(const Constructor& c, const Descriptor* d)
{
    return startArray<uint8_t>(typecodes::ARRAY8, d, c);
}

void* Encoder::startArray32(const Constructor& c, const Descriptor* d)
{
    return startArray<uint8_t>(typecodes::ARRAY32, d, c);
}

void Encoder::endArray8(size_t count, void* token)
{
    end<uint8_t>(count, token, data+position);
}

void Encoder::endArray32(size_t count, void* token)
{
    end<uint32_t>(count, token, data+position);
}

void Encoder::writeMap(const std::map<std::string, qpid::types::Variant>& value, const Descriptor* d, bool large)
{
    void* token = large ? startMap32(d) : startMap8(d);
    for (qpid::types::Variant::Map::const_iterator i = value.begin(); i != value.end(); ++i) {
        writeString(i->first);
        writeValue(i->second);
    }
    if (large) endMap32(value.size()*2, token);
    else endMap8(value.size()*2, token);
}

void Encoder::writeList(const std::list<qpid::types::Variant>& value, const Descriptor* d, bool large)
{
    void* token = large ? startList32(d) : startList8(d);
    for (qpid::types::Variant::List::const_iterator i = value.begin(); i != value.end(); ++i) {
        writeValue(*i);
    }
    if (large) endList32(value.size(), token);
    else endList8(value.size(), token);
}

void Encoder::writeValue(const qpid::types::Variant& value, const Descriptor* d)
{
    if (d) {
        writeDescriptor(*d);    // Write this descriptor before any in the value.
        d = 0;
    }
    // Write any descriptors attached to the value.
    const Variant::List& descriptors = value.getDescriptors();
    for (Variant::List::const_iterator i = descriptors.begin(); i != descriptors.end(); ++i) {
        if (i->getType() == types::VAR_STRING)
            writeDescriptor(Descriptor(CharSequence::create(i->asString())));
        else
            writeDescriptor(Descriptor(i->asUint64()));
    }
    switch (value.getType()) {
      case qpid::types::VAR_VOID:
        writeNull(d);
        break;
      case qpid::types::VAR_BOOL:
        writeBoolean(value.asBool(), d);
        break;
      case qpid::types::VAR_UINT8:
        writeUByte(value.asUint8(), d);
        break;
      case qpid::types::VAR_UINT16:
        writeUShort(value.asUint16(), d);
        break;
      case qpid::types::VAR_UINT32:
        writeUInt(value.asUint32(), d);
        break;
      case qpid::types::VAR_UINT64:
        writeULong(value.asUint64(), d);
        break;
      case qpid::types::VAR_INT8:
        writeByte(value.asInt8(), d);
        break;
      case qpid::types::VAR_INT16:
        writeShort(value.asInt16(), d);
        break;
      case qpid::types::VAR_INT32:
        writeInt(value.asInt32(), d);
        break;
      case qpid::types::VAR_INT64:
        writeLong(value.asInt64(), d);
        break;
      case qpid::types::VAR_FLOAT:
        writeFloat(value.asFloat(), d);
        break;
      case qpid::types::VAR_DOUBLE:
        writeDouble(value.asDouble(), d);
        break;
      case qpid::types::VAR_STRING:
        if (value.getEncoding() == UTF8) {
            writeString(value.getString(), d);
        } else if (value.getEncoding() == ASCII) {
            writeSymbol(value.getString(), d);
        } else {
            writeBinary(value.getString(), d);
        }
        break;
      case qpid::types::VAR_MAP:
        writeMap(value.asMap(), d);
        break;
      case qpid::types::VAR_LIST:
        writeList(value.asList(), d);
        break;
      case qpid::types::VAR_UUID:
        writeUuid(value.asUuid(), d);
        break;
    }

}

void Encoder::writeDescriptor(const Descriptor& d)
{
    writeCode(typecodes::DESCRIPTOR);
    switch (d.type) {
        case Descriptor::NUMERIC:
          writeULong(d.value.code, 0);
          break;
        case Descriptor::SYMBOLIC:
          writeSymbol(d.value.symbol, 0);
          break;
    }
}

void Encoder::check(size_t s)
{
    if (position + s > size) {
        if (grow) {
            buffer.resize(buffer.size() + s);
            data = const_cast<char*>(buffer.data());
            size = buffer.size();
        }
        else {
            QPID_LOG(notice, "Buffer overflow for write of size " << s
                     << " to buffer of size " << size << " at position " << position);
            assert(false);
            throw Overflow();
        }
    }
}

size_t Encoder::getPosition() { return position; }
size_t Encoder::getSize() const { return size; }
char* Encoder::getData() { return data + position; }
std::string Encoder::getBuffer() { return buffer; }
void Encoder::resetPosition(size_t p) { assert(p <= size); position = p; }

}} // namespace qpid::amqp
