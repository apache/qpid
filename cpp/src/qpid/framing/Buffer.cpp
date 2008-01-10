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
#include "Buffer.h"
#include "FramingContent.h" 
#include "FieldTable.h" 

namespace qpid {

namespace framing {

Buffer::Buffer(char* _data, uint32_t _size)
    : size(_size), data(_data), position(0) {
}

void Buffer::record(){
    r_position = position;
}

void Buffer::restore(bool reRecord){
    uint32_t savedPosition = position;

    position = r_position;

    if (reRecord)
	r_position = savedPosition;
}

void Buffer::reset(){
    position = 0;
}

///////////////////////////////////////////////////

void Buffer::putOctet(uint8_t i){
    data[position++] = i;
}

void Buffer::putShort(uint16_t i){
    uint16_t b = i;
    data[position++] = (uint8_t) (0xFF & (b >> 8));
    data[position++] = (uint8_t) (0xFF & b);
}

void Buffer::putLong(uint32_t i){
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

uint8_t Buffer::getOctet(){ 
    return (uint8_t) data[position++]; 
}

uint16_t Buffer::getShort(){ 
    uint16_t hi = (unsigned char) data[position++];
    hi = hi << 8;
    hi |= (unsigned char) data[position++];
    return hi;
}

uint32_t Buffer::getLong(){ 
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

template <>
uint64_t Buffer::getUInt<1>() {
    return getOctet();
}

template <>
uint64_t Buffer::getUInt<2>() {
    return getShort();
}

template <>
uint64_t Buffer::getUInt<4>() {
    return getLong();
}

template <>
uint64_t Buffer::getUInt<8>() {
    return getLongLong();
}

template <>
void Buffer::putUInt<1>(uint64_t i) {
    putOctet(i);
}

template <>
void Buffer::putUInt<2>(uint64_t i) {
    putShort(i);
}

template <>
void Buffer::putUInt<4>(uint64_t i) {
    putLong(i);
}

template <>
void Buffer::putUInt<8>(uint64_t i) {
    putLongLong(i);
}

void Buffer::putShortString(const string& s){
    uint8_t len = s.length();
    putOctet(len);
    s.copy(data + position, len);
    position += len;    
}

void Buffer::putLongString(const string& s){
    uint32_t len = s.length();
    putLong(len);
    s.copy(data + position, len);
    position += len;    
}

void Buffer::getShortString(string& s){
    uint8_t len = getOctet();
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

void Buffer::putRawData(const string& s){
    uint32_t len = s.length();
    s.copy(data + position, len);
    position += len;    
}

void Buffer::getRawData(string& s, uint32_t len){
    checkAvailable(len);
    s.assign(data + position, len);
    position += len;
}

void Buffer::putRawData(const uint8_t* s, size_t len){
    memcpy(data + position, s, len);
    position += len;    
}

void Buffer::getRawData(uint8_t* s, size_t len){
    checkAvailable(len);
    memcpy(s, data + position, len);
    position += len;
}

}}
