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
#include <Buffer.h>
#include <FramingContent.h> 
#include <FieldTable.h> 

qpid::framing::Buffer::Buffer(u_int32_t _size) : size(_size), owner(true), position(0), limit(_size){
    data = new char[size];
}

qpid::framing::Buffer::Buffer(char* _data, u_int32_t _size) : size(_size), owner(false), data(_data), position(0), limit(_size){
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
    u_int32_t p = limit - position;
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

u_int32_t qpid::framing::Buffer::available(){
    return limit - position;
}

char* qpid::framing::Buffer::start(){
    return data + position;
}

void qpid::framing::Buffer::move(u_int32_t bytes){
    position += bytes;
}
    
void qpid::framing::Buffer::putOctet(u_int8_t i){
    data[position++] = i;
}

void qpid::framing::Buffer::putShort(u_int16_t i){
    u_int16_t b = i;
    data[position++] = (u_int8_t) (0xFF & (b >> 8));
    data[position++] = (u_int8_t) (0xFF & b);
}

void qpid::framing::Buffer::putLong(u_int32_t i){
    u_int32_t b = i;
    data[position++] = (u_int8_t) (0xFF & (b >> 24));
    data[position++] = (u_int8_t) (0xFF & (b >> 16));
    data[position++] = (u_int8_t) (0xFF & (b >> 8));
    data[position++] = (u_int8_t) (0xFF & b);
}

void qpid::framing::Buffer::putLongLong(u_int64_t i){
    u_int32_t hi = i >> 32;
    u_int32_t lo = i;
    putLong(hi);
    putLong(lo);
}

u_int8_t qpid::framing::Buffer::getOctet(){ 
    return (u_int8_t) data[position++]; 
}

u_int16_t qpid::framing::Buffer::getShort(){ 
    u_int16_t hi = (unsigned char) data[position++];
    hi = hi << 8;
    hi |= (unsigned char) data[position++];
    return hi;
}

u_int32_t qpid::framing::Buffer::getLong(){ 
    u_int32_t a = (unsigned char) data[position++];
    u_int32_t b = (unsigned char) data[position++];
    u_int32_t c = (unsigned char) data[position++];
    u_int32_t d = (unsigned char) data[position++];
    a = a << 24;
    a |= b << 16;
    a |= c << 8;
    a |= d;
    return a;
}

u_int64_t qpid::framing::Buffer::getLongLong(){
    u_int64_t hi = getLong();
    u_int64_t lo = getLong();
    hi = hi << 32;
    return hi | lo;
}


void qpid::framing::Buffer::putShortString(const string& s){
    u_int8_t len = s.length();
    putOctet(len);
    s.copy(data + position, len);
    position += len;    
}

void qpid::framing::Buffer::putLongString(const string& s){
    u_int32_t len = s.length();
    putLong(len);
    s.copy(data + position, len);
    position += len;    
}

void qpid::framing::Buffer::getShortString(string& s){
    u_int8_t len = getOctet();
    s.assign(data + position, len);
    position += len;
}

void qpid::framing::Buffer::getLongString(string& s){
    u_int32_t len = getLong();
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
    u_int32_t len = s.length();
    s.copy(data + position, len);
    position += len;    
}

void qpid::framing::Buffer::getRawData(string& s, u_int32_t len){
    s.assign(data + position, len);
    position += len;
}
