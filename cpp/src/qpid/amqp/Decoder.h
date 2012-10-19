#ifndef QPID_AMQP_DECODER_H
#define QPID_AMQP_DECODER_H

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
#include "qpid/sys/IntegerTypes.h"
#include <map>
#include <string>
#include <stddef.h>

namespace qpid {
namespace types {
class Uuid;
class Variant;
}
namespace amqp {
struct CharSequence;
struct Constructor;
struct Descriptor;
class Reader;

/**
 * Class to assist in decoding an AMQP encoded data-stream.
 */
class Decoder
{
  public:
    Decoder(const char*, size_t);

    size_t available();
    uint8_t readCode();

    bool readBoolean();
    uint8_t readUByte();
    uint16_t readUShort();
    uint32_t readUInt();
    uint64_t readULong();
    int8_t readByte();
    int16_t readShort();
    int32_t readInt();
    int64_t readLong();
    float readFloat();
    double readDouble();
    qpid::types::Uuid readUuid();
    CharSequence readSequence8();
    CharSequence readSequence32();
    Descriptor readDescriptor();
    void read(Reader& reader);

    void readMap(std::map<std::string, qpid::types::Variant>&);
    std::map<std::string, qpid::types::Variant> readMap();
    void advance(size_t);
    size_t getPosition() const;
    void resetSize(size_t size);

  private:
    const char* const start;
    size_t size;
    size_t position;

    void readOne(Reader& reader);
    void readValue(Reader& reader, uint8_t code, const Descriptor* descriptor);
    void readList(Reader& reader, uint32_t size, uint32_t count, const Descriptor* descriptor);
    void readMap(Reader& reader, uint32_t size, uint32_t count, const Descriptor* descriptor);
    void readArray(Reader& reader, uint32_t size, uint32_t count, const Descriptor* descriptor);
    void readList8(Reader& reader, const Descriptor* descriptor);
    void readList32(Reader& reader, const Descriptor* descriptor);
    void readMap8(Reader& reader, const Descriptor* descriptor);
    void readMap32(Reader& reader, const Descriptor* descriptor);
    void readArray8(Reader& reader, const Descriptor* descriptor);
    void readArray32(Reader& reader, const Descriptor* descriptor);
    CharSequence readRawUuid();
    Constructor readConstructor();
    const char* data();

};
}} // namespace qpid::amqp

#endif  /*!QPID_AMQP_DECODER_H*/
