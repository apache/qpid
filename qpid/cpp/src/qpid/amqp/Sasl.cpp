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
#include "qpid/amqp/Sasl.h"
#include "qpid/amqp/Decoder.h"
#include "qpid/amqp/Descriptor.h"
#include "qpid/amqp/Encoder.h"
#include "qpid/log/Statement.h"
#include "qpid/framing/Buffer.h"
#include "qpid/framing/ProtocolVersion.h"
#include "qpid/framing/ProtocolInitiation.h"
#include <string.h>

namespace qpid {
namespace amqp {

Sasl::Sasl(const std::string& i) : id(i), buffer(2*512/*AMQP 1.0's MAX_MIN_FRAME_SIZE - is this enough though?*/), encoder(&buffer[0], buffer.size()) {}
Sasl::~Sasl() {}

void* Sasl::startFrame()
{
    //write sasl frame header, leaving 4 bytes for total size
    char* start = encoder.skip(4);
    encoder.write((uint8_t) 0x02);//data offset
    encoder.write((uint8_t) 0x01);//frame type
    encoder.write((uint16_t) 0x0000);//ignored
    return start;
}

void Sasl::endFrame(void* frame)
{
    //now backfill the frame size
    char* start = (char*) frame;
    char* current = &buffer[encoder.getPosition()];
    uint32_t frameSize = current - start;
    Encoder backfill(start, 4);
    backfill.write(frameSize);
    QPID_LOG(trace, "Completed encoding of frame of " << frameSize << " bytes");
}


std::size_t Sasl::read(const char* data, size_t available)
{
    size_t consumed = 0;
    while (!stopReading() && available - consumed > 4/*framesize*/) {
        Decoder decoder(data+consumed, available-consumed);
        //read frame-header
        uint32_t frameSize = decoder.readUInt();
        if (frameSize > decoder.getSize()) break;//don't have all the data for this frame yet

        QPID_LOG(trace, "Reading SASL frame of size " << frameSize);
        decoder.resetSize(frameSize);
        uint8_t dataOffset = decoder.readUByte();
        uint8_t frameType = decoder.readUByte();
        if (frameType != 0x01) {
            QPID_LOG(error, "Expected SASL frame; got type " << frameType);
        }
        uint16_t ignored = decoder.readUShort();
        if (ignored) {
            QPID_LOG(info, "Got non null bytes at end of SASL frame header");
        }

        //body is at offset 4*dataOffset from the start
        size_t skip = dataOffset*4 - 8;
        if (skip) {
            QPID_LOG(info, "Offset for sasl frame was not as expected");
            decoder.advance(skip);
        }
        decoder.read(*this);
        consumed += decoder.getPosition();
    }
    return consumed;
}

std::size_t Sasl::write(char* data, size_t size)
{
    size_t available = encoder.getPosition();
    if (available) {
        size_t encoded = available > size ? size : available;
        ::memcpy(data, &buffer[0], encoded);
        size_t remainder = encoder.getPosition() - encoded;
        if (remainder) {
            //shuffle
            ::memcpy(&buffer[0], &buffer[size], remainder);
        }
        encoder.resetPosition(remainder);
        return encoded;
    } else {
        return 0;
    }
}

std::size_t Sasl::readProtocolHeader(const char* buffer, std::size_t size)
{
    framing::ProtocolInitiation pi(qpid::framing::ProtocolVersion(1,0,qpid::framing::ProtocolVersion::SASL));
    if (size >= pi.encodedSize()) {
        qpid::framing::Buffer out(const_cast<char*>(buffer), size);
        pi.decode(out);
        QPID_LOG_CAT(debug, protocol, id << " read protocol header: " << pi);
        return pi.encodedSize();
    } else {
        return 0;
    }
}
std::size_t Sasl::writeProtocolHeader(char* buffer, std::size_t size)
{
    framing::ProtocolInitiation pi(qpid::framing::ProtocolVersion(1,0,qpid::framing::ProtocolVersion::SASL));
    if (size >= pi.encodedSize()) {
        QPID_LOG_CAT(debug, protocol, id << " writing protocol header: " << pi);
        qpid::framing::Buffer out(buffer, size);
        pi.encode(out);
        return pi.encodedSize();
    } else {
        QPID_LOG_CAT(warning, protocol, id << " insufficient buffer for protocol header: " << size)
        return 0;
    }
}

bool Sasl::stopReading()
{
    return false;
}

}} // namespace qpid::amqp
