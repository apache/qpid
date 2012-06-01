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
#include "Decoder.h"
#include "qpid/log/Statement.h"
#include "qpid/messaging/exceptions.h"
#include "qpid/messaging/MessageImpl.h"
#include <string.h>

namespace qpid {
namespace messaging {
namespace amqp {

Decoder::~Decoder() {}

ssize_t Decoder::decode(const char* data, size_t size)
{
    pn_bytes_t input = {size, const_cast<char*>(data)};
    const size_t maxDatums(50);/*what is a reasonable number here*/
    pn_atom_t datums[maxDatums];
    pn_atoms_t output = {maxDatums, datums};
    while (input.size > 0) {
        if (pn_decode_one(&input, &output)) throw qpid::messaging::MessagingException("Decode failure!");
        input.start = const_cast<char*>(data + (size - input.size));
    }
    return size - input.size;
}

void DecoderBase::onNull() { QPID_LOG(debug, this << " onNull()"); }
void DecoderBase::onBool(bool v) { QPID_LOG(debug, this << " onBool(" << v << ")"); }
void DecoderBase::onUbyte(uint8_t v) { QPID_LOG(debug, this << " onUbyte(" << v << ")"); }
void DecoderBase::onByte(int8_t v) { QPID_LOG(debug, this << " onByte(" << v << ")"); }
void DecoderBase::onUshort(uint16_t v) { QPID_LOG(debug, this << " onUshort(" << v << ")"); }
void DecoderBase::onShort(int16_t v) { QPID_LOG(debug, this << " onShort(" << v << ")"); }
void DecoderBase::onUint(uint32_t v) { QPID_LOG(debug, this << " onUint(" << v << ")"); }
void DecoderBase::onInt(int32_t v) { QPID_LOG(debug, this << " onInt(" << v << ")"); }
void DecoderBase::onFloat(float v) { QPID_LOG(debug, this << " onFloat(" << v << ")"); }
void DecoderBase::onUlong(uint64_t v) { QPID_LOG(debug, this << " onUlong(" << v << ")"); }
void DecoderBase::onLong(int64_t v) { QPID_LOG(debug, this << " onLong(" << v << ")"); }
void DecoderBase::onDouble(double v) { QPID_LOG(debug, this << " onDouble(" << v << ")"); }
void DecoderBase::onBinary(size_t size, char*) { QPID_LOG(debug, this << " onBinary(" << size << ")"); }
void DecoderBase::onUtf8(size_t size, char*) { QPID_LOG(debug, this << " onUtf8(" << size << ")"); }
void DecoderBase::onSymbol(size_t size, char*) { QPID_LOG(debug, this << " onSymbol(" << size << ")"); }
void DecoderBase::startDescriptor() { inDescriptor = true; QPID_LOG(debug, this << " startDescriptor()"); }
void DecoderBase::stopDescriptor() { inDescriptor = false; QPID_LOG(debug, this << " stopDescriptor()"); }
void DecoderBase::startArray(size_t count, uint8_t code) { QPID_LOG(debug, this << " startArray(" << count << ", " << code << ")"); }
void DecoderBase::stopArray(size_t count, uint8_t code) { QPID_LOG(debug, this << " stopArray(" << count << ", " << code << ")"); }
void DecoderBase::startList(size_t count) { QPID_LOG(debug, this << " startList(" << count << ")"); }
void DecoderBase::stopList(size_t count) { QPID_LOG(debug, this << " stopList(" << count << ")"); }
void DecoderBase::startMap(size_t count) { QPID_LOG(debug, this << " startMap(" << count << ")"); }
void DecoderBase::stopMap(size_t count) { QPID_LOG(debug, this << " stopMap(" << count << ")"); }
DecoderBase::~DecoderBase() {}

void ContextSensitiveDecoder::onNull()
{
    if ((decoder = getDecoder())) decoder->onNull();
    else QPID_LOG(debug, this << " onNull() not handled");
    onValue();
}
void ContextSensitiveDecoder::onBool(bool v)
{
    if ((decoder = getDecoder())) decoder->onBool(v);
    else QPID_LOG(debug, this << " onBool(" << v << ") not handled");
    onValue();
}
void ContextSensitiveDecoder::onUbyte(uint8_t v)
{
    if ((decoder = getDecoder())) decoder->onUbyte(v);
    else QPID_LOG(debug, this << " onUbyte(" << v << ") not handled");
    onValue();
}

void ContextSensitiveDecoder::onByte(int8_t v)
{
    if ((decoder = getDecoder())) decoder->onByte(v);
    else QPID_LOG(debug, this << " onByte(" << v << ") not handled");
    onValue();
}

void ContextSensitiveDecoder::onUshort(uint16_t v)
{
    if ((decoder = getDecoder())) decoder->onUshort(v);
    else QPID_LOG(debug, this << " onUshort(" << v << ") not handled");
    onValue();
}

void ContextSensitiveDecoder::onShort(int16_t v)
{
    if ((decoder = getDecoder())) decoder->onShort(v);
    else QPID_LOG(debug, this << " onShort(" << v << ") not handled");
    onValue();
}

void ContextSensitiveDecoder::onUint(uint32_t v)
{
    if ((decoder = getDecoder())) decoder->onUint(v);
    else QPID_LOG(debug, this << " onUint(" << v << ") not handled");
    onValue();
}

void ContextSensitiveDecoder::onInt(int32_t v)
{
    if ((decoder = getDecoder())) decoder->onInt(v);
    else QPID_LOG(debug, this << " onInt(" << v << ") not handled");
    onValue();
}

void ContextSensitiveDecoder::onFloat(float v)
{
    if ((decoder = getDecoder())) decoder->onFloat(v);
    else QPID_LOG(debug, this << " onFloat(" << v << ") not handled");
    onValue();
}

void ContextSensitiveDecoder::onUlong(uint64_t v)
{
    if ((decoder = getDecoder())) decoder->onUlong(v);
    else QPID_LOG(debug, this << " onUlong(" << v << ") not handled");
    onValue();
}

void ContextSensitiveDecoder::onLong(int64_t v)
{
    if ((decoder = getDecoder())) decoder->onLong(v);
    else QPID_LOG(debug, this << " onLong(" << v << ") not handled");
    onValue();
}

void ContextSensitiveDecoder::onDouble(double v)
{
    if ((decoder = getDecoder())) decoder->onDouble(v);
    else QPID_LOG(debug, this << " onDouble(" << v << ") not handled");
    onValue();
}

void ContextSensitiveDecoder::onBinary(size_t size, char* bytes)
{
    if ((decoder = getDecoder())) decoder->onBinary(size, bytes);
    else QPID_LOG(debug, this << " onBinary(" << size << ") not handled");
    onValue();
}

void ContextSensitiveDecoder::onUtf8(size_t size, char* utf8)
{
    if ((decoder = getDecoder())) decoder->onUtf8(size, utf8);
    else QPID_LOG(debug, this << " onUtf8(" << size << ") not handled");
    onValue();
}

void ContextSensitiveDecoder::onSymbol(size_t size, char* symbol)
{
    if ((decoder = getDecoder())) decoder->onSymbol(size, symbol);
    else QPID_LOG(debug, this << " onSymbol(" << size << ") not handled");
    onValue();
}

void ContextSensitiveDecoder::startDescriptor()
{
    if ((decoder = getDecoder())) decoder->startDescriptor();
    else QPID_LOG(debug, this << " startDescriptor() not handled");
}

void ContextSensitiveDecoder::stopDescriptor()
{
    ++descriptorLevel;
    if ((decoder = getDecoder())) decoder->stopDescriptor();
    else QPID_LOG(debug, this << " stopDescriptor() not handled");
}

void ContextSensitiveDecoder::startArray(size_t count, uint8_t code)
{
    ++valueLevel;
    if ((decoder = getDecoder())) decoder->startArray(count, code);
    else QPID_LOG(debug, this << " startArray(" << count << ", " << code << ") not handled");
}

void ContextSensitiveDecoder::stopArray(size_t count, uint8_t code)
{
    stopNested();
    if ((decoder = getDecoder())) decoder->stopArray(count, code);
    else QPID_LOG(debug, this << " stopArray(" << count << ", " << code << ") not handled");
}

void ContextSensitiveDecoder::startList(size_t count)
{
    startNested();
    if ((decoder = getDecoder())) decoder->startList(count);
    else QPID_LOG(debug, this << " startList(" << count << ") not handled");
}

void ContextSensitiveDecoder::stopList(size_t count)
{
    stopNested();
    if ((decoder = getDecoder())) decoder->stopList(count);
    else QPID_LOG(debug, this << " stopList(" << count << ") not handled");
}

void ContextSensitiveDecoder::startMap(size_t count)
{
    startNested();
    if ((decoder = getDecoder())) decoder->startMap(count);
    else QPID_LOG(debug, this << " startMap(" << count << ") not handled");
}

void ContextSensitiveDecoder::stopMap(size_t count)
{
    stopNested();
    if ((decoder = getDecoder())) decoder->stopMap(count);
    else QPID_LOG(debug, this << " stopMap(" << count << ") not handled");
}

void ContextSensitiveDecoder::onValue()
{
    if (valueLevel == descriptorLevel) {
        described = false;
    }
}

void ContextSensitiveDecoder::startNested()
{
    ++valueLevel;
}

void ContextSensitiveDecoder::stopNested()
{
    if (--valueLevel == descriptorLevel) {
        --descriptorLevel;
        described = false;
    }
}

ContextSensitiveDecoder::ContextSensitiveDecoder() : described(false), descriptorLevel(-1), valueLevel(0), decoder(0) {}
void ContextSensitiveDecoder::setDecoder(DecoderBase* d) { decoder = d; }
DecoderBase* ContextSensitiveDecoder::getDecoder() { return decoder; }
ContextSensitiveDecoder::~ContextSensitiveDecoder() {}

DescribedValueDecoder::DescribedValueDecoder(uint64_t c, const std::string& s) : descriptorCode(c), descriptorSymbol(s),
                                                                                 descriptorDecoder(*this), matched(false) {}
DescribedValueDecoder::~DescribedValueDecoder() {}
DecoderBase* DescribedValueDecoder::getDecoder()
{
    if (inDescriptor) return &descriptorDecoder;
    else if (described && matched) return this;//when does matched get switched off?
    else return 0;
}

DescribedValueDecoder::DescriptorDecoder::DescriptorDecoder(DescribedValueDecoder& p) : parent(p) {}

void DescribedValueDecoder::DescriptorDecoder::onSymbol(size_t size, char *str)
{
    parent.matched = (::strncmp(str, parent.descriptorSymbol.c_str(), size) == 0);
    QPID_LOG(debug, &parent << ":" << this << ":DescriptorDecoder: onSymbol("
             << std::string(str, size) << " -> " << (parent.matched ? "matched" : "did not match") << ")");
}
void DescribedValueDecoder::DescriptorDecoder::onUlong(uint64_t v)
{
    parent.matched = (v == parent.descriptorCode);
    QPID_LOG(debug, &parent << ":" << this << ":DescriptorDecoder: onUlong(" << v << " -> " << (parent.matched ? "matched" : "did not match") << ")");
}

MessageDataDecoder::MessageDataDecoder(qpid::messaging::MessageImpl& m)
    : DescribedValueDecoder(0x00000075, "amqp:data:binary"), msg(m)
{
    msg.bytes.clear();
}

void MessageDataDecoder::onBinary(size_t size, char *bytes)
{
    QPID_LOG(debug, this << ":MessageDataDecoder: onBinary() with " << size << "  bytes");
    msg.appendBytes(bytes, size);
}


}}} // namespace qpid::messaging::amqp
