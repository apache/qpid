/*
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

#include "qpid/management/Buffer.h"
#include "qpid/framing/Buffer.h"
#include "qpid/amqp_0_10/Codecs.h"

using namespace std;

namespace qpid {
namespace management {

Buffer::Buffer(char* data, uint32_t size) : impl(new framing::Buffer(data, size)) {}
Buffer::~Buffer() { delete impl; }
void Buffer::reset() { impl->reset(); }
uint32_t Buffer::available() { return impl->available(); }
uint32_t Buffer::getSize() { return impl->getSize(); }
uint32_t Buffer::getPosition() { return impl->getPosition(); }
void Buffer::setPosition(uint32_t p) { impl->setPosition(p); }
char* Buffer::getPointer() { return impl->getPointer(); }
void Buffer::putOctet(uint8_t i) { impl->putOctet(i); }
void Buffer::putShort(uint16_t i) { impl->putShort(i); }
void Buffer::putLong(uint32_t i) { impl->putLong(i); }
void Buffer::putLongLong(uint64_t i) { impl->putLongLong(i); }
void Buffer::putInt8(int8_t i) { impl->putInt8(i); }
void Buffer::putInt16(int16_t i) { impl->putInt16(i); }
void Buffer::putInt32(int32_t i) { impl->putInt32(i); }
void Buffer::putInt64(int64_t i) { impl->putInt64(i); }
void Buffer::putFloat(float i) { impl->putFloat(i); }
void Buffer::putDouble(double i) { impl->putDouble(i); }
void Buffer::putBin128(const uint8_t* i) { impl->putBin128(i); }
uint8_t Buffer::getOctet() { return impl->getOctet(); }
uint16_t Buffer::getShort() { return impl->getShort(); }
uint32_t Buffer::getLong() { return impl->getLong(); }
uint64_t Buffer::getLongLong() { return impl->getLongLong(); }
int8_t Buffer:: getInt8() { return impl-> getInt8(); }
int16_t Buffer::getInt16() { return impl->getInt16(); }
int32_t Buffer::getInt32() { return impl->getInt32(); }
int64_t Buffer::getInt64() { return impl->getInt64(); }
float Buffer::getFloat() { return impl->getFloat(); }
double Buffer::getDouble() { return impl->getDouble(); }
void Buffer::putShortString(const string& i) { impl->putShortString(i); }
void Buffer::putMediumString(const string& i) { impl->putMediumString(i); }
void Buffer::putLongString(const string& i) { impl->putLongString(i); }
void Buffer::getShortString(string& i) { impl->getShortString(i); }
void Buffer::getMediumString(string& i) { impl->getMediumString(i); }
void Buffer::getLongString(string& i) { impl->getLongString(i); }
void Buffer::getBin128(uint8_t* i) { impl->getBin128(i); }
void Buffer::putRawData(const string& i) { impl->putRawData(i); }
void Buffer::getRawData(string& s, uint32_t size) { impl->getRawData(s, size); }
void Buffer::putRawData(const uint8_t* data, size_t size) { impl->putRawData(data, size); }
void Buffer::getRawData(uint8_t* data, size_t size) { impl->getRawData(data, size); }

void Buffer::putMap(const types::Variant::Map& i)
{
    string encoded;
    amqp_0_10::MapCodec::encode(i, encoded);
    impl->putRawData(encoded);
}

void Buffer::putList(const types::Variant::List& i)
{
    string encoded;
    amqp_0_10::ListCodec::encode(i, encoded);
    impl->putRawData(encoded);
}

void Buffer::getMap(types::Variant::Map& map)
{
    string encoded;
    uint32_t saved = impl->getPosition();
    uint32_t length = impl->getLong();
    impl->setPosition(saved);
    impl->getRawData(encoded, length + sizeof(uint32_t));
    amqp_0_10::MapCodec::decode(encoded, map);
}

void Buffer::getList(types::Variant::List& list)
{
    string encoded;
    uint32_t saved = impl->getPosition();
    uint32_t length = impl->getLong();
    impl->setPosition(saved);
    impl->getRawData(encoded, length + sizeof(uint32_t));
    amqp_0_10::ListCodec::decode(encoded, list);
}

}}
