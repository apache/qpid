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
#include "qpid/framing/Buffer.h"
#include "qpid/framing/FieldTable.h" 
#include "qpid/Msg.h"
#include <string.h>
#include <boost/format.hpp>

namespace qpid {

namespace framing {

using std::string;

Buffer::Buffer(char* _data, uint32_t _size)
    : size(_size), data(_data), position(0) {
}

void Buffer::reset(){
    position = 0;
}

///////////////////////////////////////////////////

void Buffer::putOctet(uint8_t i){
    checkAvailable(1);
    data[position++] = i;
}

void Buffer::putShort(uint16_t i){
    checkAvailable(2);
    uint16_t b = i;
    data[position++] = (uint8_t) (0xFF & (b >> 8));
    data[position++] = (uint8_t) (0xFF & b);
}

void Buffer::putLong(uint32_t i){
    checkAvailable(4);
    uint32_t b = i;
    data[position++] = (uint8_t) (0xFF & (b >> 24));
    data[position++] = (uint8_t) (0xFF & (b >> 16));
    data[position++] = (uint8_t) (0xFF & (b >> 8));
    data[position++] = (uint8_t) (0xFF & b);
}

void Buffer::putLongLong(uint64_t i){
    uint32_t hi = i >> 32;
    uint32_t lo = i;
    putLong(hi);
    putLong(lo);
}

void Buffer::putInt8(int8_t i){
    checkAvailable(1);
    data[position++] = (uint8_t) i;
}

void Buffer::putInt16(int16_t i){
    putShort((uint16_t) i);
}

void Buffer::putInt32(int32_t i){
    putLong((uint32_t) i);
}

void Buffer::putInt64(int64_t i){
    putLongLong((uint64_t) i);
}

void Buffer::putFloat(float f){
    union {
        uint32_t i;
        float    f;
    } val;

    val.f = f;
    putLong (val.i);
}

void Buffer::putDouble(double f){
    union {
        uint64_t i;
        double   f;
    } val;

    val.f = f;
    putLongLong (val.i);
}

void Buffer::putBin128(const uint8_t* b){
    checkAvailable(16);
    memcpy (data + position, b, 16);
    position += 16;
}

uint8_t Buffer::getOctet(){
    checkAvailable(1);
    uint8_t octet = static_cast<uint8_t>(data[position++]);
    return octet;
}

uint16_t Buffer::getShort(){
    checkAvailable(2);
    uint16_t hi = (unsigned char) data[position++];
    hi = hi << 8;
    hi |= (unsigned char) data[position++];
    return hi;
}

uint32_t Buffer::getLong(){
    checkAvailable(4);
    uint32_t a = (unsigned char) data[position++];
    uint32_t b = (unsigned char) data[position++];
    uint32_t c = (unsigned char) data[position++];
    uint32_t d = (unsigned char) data[position++];
    a = a << 24;
    a |= b << 16;
    a |= c << 8;
    a |= d;
    return a;
}

uint64_t Buffer::getLongLong(){
    uint64_t hi = getLong();
    uint64_t lo = getLong();
    hi = hi << 32;
    return hi | lo;
}

int8_t Buffer::getInt8(){
    checkAvailable(1);
    int8_t i = static_cast<int8_t>(data[position++]);
    return i;
}

int16_t Buffer::getInt16(){
    return (int16_t) getShort();
}

int32_t Buffer::getInt32(){
    return (int32_t) getLong();
}

int64_t Buffer::getInt64(){
    return (int64_t) getLongLong();
}

float Buffer::getFloat(){
    union {
        uint32_t i;
        float    f;
    } val;
    val.i = getLong();
    return val.f;
}

double Buffer::getDouble(){
    union {
        uint64_t i;
        double   f;
    } val;
    val.i = getLongLong();
    return val.f;
}

template <>
QPID_COMMON_EXTERN uint64_t Buffer::getUInt<1>() {
    return getOctet();
}

template <>
QPID_COMMON_EXTERN uint64_t Buffer::getUInt<2>() {
    return getShort();
}

template <>
QPID_COMMON_EXTERN uint64_t Buffer::getUInt<4>() {
    return getLong();
}

template <>
QPID_COMMON_EXTERN uint64_t Buffer::getUInt<8>() {
    return getLongLong();
}

template <>
QPID_COMMON_EXTERN void Buffer::putUInt<1>(uint64_t i) {
    if (std::numeric_limits<uint8_t>::min() <= i && i <= std::numeric_limits<uint8_t>::max()) {
        putOctet(i);
        return;
    }
    throw Exception(QPID_MSG("Could not encode (" << i << ") as uint8_t."));
}

template <>
QPID_COMMON_EXTERN void Buffer::putUInt<2>(uint64_t i) {
    if (std::numeric_limits<uint16_t>::min() <= i && i <= std::numeric_limits<uint16_t>::max()) {
        putShort(i);
        return;
    }
    throw Exception(QPID_MSG("Could not encode (" << i << ") as uint16_t."));
}

template <>
QPID_COMMON_EXTERN void Buffer::putUInt<4>(uint64_t i) {
    if (std::numeric_limits<uint32_t>::min() <= i && i <= std::numeric_limits<uint32_t>::max()) {
        putLong(i);
        return;
    }
    throw Exception(QPID_MSG("Could not encode (" << i << ") as uint32_t."));
}

template <>
QPID_COMMON_EXTERN void Buffer::putUInt<8>(uint64_t i) {
    putLongLong(i);
}

void Buffer::putShortString(const string& s){
    size_t slen = s.length();
    if (slen <= std::numeric_limits<uint8_t>::max()) {
        uint8_t len = (uint8_t) slen;
        putOctet(len);
        checkAvailable(slen);
        s.copy(data + position, len);
        position += len;
        return;
    }
    throw Exception(QPID_MSG("Could not encode string of " << slen << " bytes as uint8_t string."));
}

void Buffer::putMediumString(const string& s){
    size_t slen = s.length();
    if (slen <= std::numeric_limits<uint16_t>::max()) {
        uint16_t len = (uint16_t) slen;
        putShort(len);
        checkAvailable(slen);
        s.copy(data + position, len);
        position += len;
        return;
    }
    throw Exception(QPID_MSG("Could not encode string of " << slen << " bytes as uint16_t string."));
}

void Buffer::putLongString(const string& s){
    size_t slen = s.length();
    if (slen <= std::numeric_limits<uint32_t>::max()) {
        uint32_t len = (uint32_t) slen;
        putLong(len);
        checkAvailable(slen);
        s.copy(data + position, len);
        position += len;
        return;
    }
    throw Exception(QPID_MSG("Could not encode string of " << slen << " bytes as uint32_t string."));
}

void Buffer::getShortString(string& s){
    uint8_t len = getOctet();
    checkAvailable(len);
    s.assign(data + position, len);
    position += len;
}

void Buffer::getMediumString(string& s){
    uint16_t len = getShort();
    checkAvailable(len);
    s.assign(data + position, len);
    position += len;
}

void Buffer::getLongString(string& s){
    uint32_t len = getLong();
    checkAvailable(len);
    s.assign(data + position, len);
    position += len;
}

void Buffer::getBin128(uint8_t* b){
    checkAvailable(16);
    memcpy (b, data + position, 16);
    position += 16;
}

void Buffer::putRawData(const string& s){
    size_t len = s.length();
    checkAvailable(len);
    s.copy(data + position, len);
    position += len;    
}

void Buffer::getRawData(string& s, uint32_t len){
    checkAvailable(len);
    s.assign(data + position, len);
    position += len;
}

void Buffer::putRawData(const uint8_t* s, size_t len){
    checkAvailable(len);
    memcpy(data + position, s, len);
    position += len;    
}

void Buffer::getRawData(uint8_t* s, size_t len){
    checkAvailable(len);
    memcpy(s, data + position, len);
    position += len;
}

void Buffer::dump(std::ostream& out) const {
    for (uint32_t i = position; i < size; i++)
    {
        if (i != position)
            out << " ";
        out << boost::format("%02x") % ((unsigned) (uint8_t) data[i]);
    }
}

std::ostream& operator<<(std::ostream& out, const Buffer& b){
    out << "Buffer[";
    b.dump(out);
    return out << "]";
}

}}
