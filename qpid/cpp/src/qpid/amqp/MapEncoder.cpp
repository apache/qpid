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
#include "MapEncoder.h"
#include "CharSequence.h"
#include "qpid/amqp/typecodes.h"
#include <string.h>

namespace qpid {
namespace amqp {

MapEncoder::MapEncoder(char* data, size_t size) : Encoder(data, size) {}

void MapEncoder::handleVoid(const CharSequence& key)
{
    writeString(key);
    writeNull();
}

void MapEncoder::handleBool(const CharSequence& key, bool value)
{
    writeString(key);
    writeBoolean(value);
}

void MapEncoder::handleUint8(const CharSequence& key, uint8_t value)
{
    writeString(key);
    writeUByte(value);
}

void MapEncoder::handleUint16(const CharSequence& key, uint16_t value)
{
    writeString(key);
    writeUShort(value);
}

void MapEncoder::handleUint32(const CharSequence& key, uint32_t value)
{
    writeString(key);
    writeUInt(value);
}

void MapEncoder::handleUint64(const CharSequence& key, uint64_t value)
{
    writeString(key);
    writeULong(value);
}

void MapEncoder::handleInt8(const CharSequence& key, int8_t value)
{
    writeString(key);
    writeByte(value);
}

void MapEncoder::handleInt16(const CharSequence& key, int16_t value)
{
    writeString(key);
    writeShort(value);
}

void MapEncoder::handleInt32(const CharSequence& key, int32_t value)
{
    writeString(key);
    writeInt(value);
}

void MapEncoder::handleInt64(const CharSequence& key, int64_t value)
{
    writeString(key);
    writeLong(value);
}

void MapEncoder::handleFloat(const CharSequence& key, float value)
{
    writeString(key);
    writeFloat(value);
}

void MapEncoder::handleDouble(const CharSequence& key, double value)
{
    writeString(key);
    writeDouble(value);
}

namespace {
const std::string BINARY("binary");
}

void MapEncoder::handleString(const CharSequence& key, const CharSequence& value, const CharSequence& encoding)
{
    writeString(key);
    if (encoding.size == BINARY.size() && ::strncmp(encoding.data, BINARY.data(), encoding.size)) {
        writeBinary(value);
    } else {
        writeString(value);
    }
}

void MapEncoder::writeMetaData(size_t size, size_t count, const Descriptor* d)
{
    if (count > 255 || size > 255) {
        writeMap32MetaData((uint32_t) size, (uint32_t) count, d);
    } else {
        writeMap8MetaData((uint8_t) size, (uint8_t) count, d);
    }
}

void MapEncoder::writeMap8MetaData(uint8_t size, uint8_t count, const Descriptor* d)
{
    if (d) writeDescriptor(*d);
    writeCode(typecodes::MAP8);
    write((uint8_t) (size+1)/*size includes count field*/);
    write(count);
}

void MapEncoder::writeMap32MetaData(uint32_t size, uint32_t count, const Descriptor* d)
{
    if (d) writeDescriptor(*d);
    writeCode(typecodes::MAP32);
    write((uint32_t) (size+4)/*size includes count field*/);
    write(count);
}

}} // namespace qpid::amqp
