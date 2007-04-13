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

qpid::framing::Buffer::Buffer(uint32_t _size) : size(_size), owner(true), position(0), limit(_size){
    data = new char[size];
}

qpid::framing::Buffer::Buffer(char* _data, uint32_t _size) : size(_size), owner(false), data(_data), position(0), limit(_size){
}

qpid::framing::Buffer::~Buffer(){
    if(owner) delete[] data;
}

void qpid::framing::Buffer::flip(){
    limit = position;
    position = 0;
}

void qpid::framing::Buffer::clear(){
    limit = size;
    position = 0;
}

void qpid::framing::Buffer::compact(){
    uint32_t p = limit - position;
    //copy p chars from position to 0
    memmove(data, data + position, p);
    limit = size;
    position = p;
}

void qpid::framing::Buffer::record(){
    r_position = position;
    r_limit = limit;
}

void qpid::framing::Buffer::restore(){
    position = r_position;
    limit = r_limit;
}

uint32_t qpid::framing::Buffer::available(){
    return limit - position;
}

char* qpid::framing::Buffer::start(){
    return data + position;
}

void qpid::framing::Buffer::move(uint32_t bytes){
    position += bytes;
}
    
void qpid::framing::Buffer::putOctet(uint8_t i){
    data[position++] = i;
}

void qpid::framing::Buffer::putShort(uint16_t i){
    uint16_t b = i;
    data[position++] = (uint8_t) (0xFF & (b >> 8));
    data[position++] = (uint8_t) (0xFF & b);
}

void qpid::framing::Buffer::putLong(uint32_t i){
    uint32_t b = i;
    data[position++] = (uint8_t) (0xFF & (b >> 24));
    data[position++] = (uint8_t) (0xFF & (b >> 16));
    data[position++] = (uint8_t) (0xFF & (b >> 8));
    data[position++] = (uint8_t) (0xFF & b);
}

void qpid::framing::Buffer::putLongLong(uint64_t i){
    uint32_t hi = i >> 32;
    uint32_t lo = i;
    putLong(hi);
    putLong(lo);
}

uint8_t qpid::framing::Buffer::getOctet(){ 
    return (uint8_t) data[position++]; 
}

uint16_t qpid::framing::Buffer::getShort(){ 
    uint16_t hi = (unsigned char) data[position++];
    hi = hi << 8;
    hi |= (unsigned char) data[position++];
    return hi;
}

uint32_t qpid::framing::Buffer::getLong(){ 
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

uint64_t qpid::framing::Buffer::getLongLong(){
    uint64_t hi = getLong();
    uint64_t lo = getLong();
    hi = hi << 32;
    return hi | lo;
}


void qpid::framing::Buffer::putShortString(const string& s){
    uint8_t len = s.length();
    putOctet(len);
    s.copy(data + position, len);
    position += len;    
}

void qpid::framing::Buffer::putLongString(const string& s){
    uint32_t len = s.length();
    putLong(len);
    s.copy(data + position, len);
    position += len;    
}

void qpid::framing::Buffer::getShortString(string& s){
    uint8_t len = getOctet();
    s.assign(data + position, len);
    position += len;
}

void qpid::framing::Buffer::getLongString(string& s){
    uint32_t len = getLong();
    s.assign(data + position, len);
    position += len;
}

void qpid::framing::Buffer::putFieldTable(const FieldTable& t){
    t.encode(*this);
}

void qpid::framing::Buffer::getFieldTable(FieldTable& t){
    t.decode(*this);
}

void qpid::framing::Buffer::putContent(const Content& c){
    c.encode(*this);
}

void qpid::framing::Buffer::getContent(Content& c){
    c.decode(*this);
}

void qpid::framing::Buffer::putRawData(const string& s){
    uint32_t len = s.length();
    s.copy(data + position, len);
    position += len;    
}

void qpid::framing::Buffer::getRawData(string& s, uint32_t len){
    s.assign(data + position, len);
    position += len;
}
