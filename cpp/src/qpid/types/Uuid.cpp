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
#include "qpid/types/Uuid.h"
#include "qpid/sys/uuid.h"
#include "qpid/sys/IntegerTypes.h"
#include <sstream>
#include <iostream>
#include <string.h>

namespace qpid {
namespace types {

using namespace std;

const size_t Uuid::SIZE=16;
static const size_t UNPARSED_SIZE=36; 

Uuid::Uuid(bool unique)
{
    if (unique) {
        generate();
    } else {
        clear();
    }
}

Uuid::Uuid(const Uuid& other)
{
    ::memcpy(bytes, other.bytes, Uuid::SIZE);
}

Uuid::Uuid(const unsigned char* uuid)
{
    ::memcpy(bytes, uuid, Uuid::SIZE);
}

Uuid& Uuid::operator=(const Uuid& other)
{
    if (this == &other) return *this;
    ::memcpy(bytes, other.bytes, Uuid::SIZE);
    return *this;
}

void Uuid::generate()
{
    uuid_generate(bytes);
}

void Uuid::clear()
{
    uuid_clear(bytes);
}

// Force int 0/!0 to false/true; avoids compile warnings.
bool Uuid::isNull() const
{
    // This const cast is for Solaris which has non const arguments
    return !!uuid_is_null(const_cast<uint8_t*>(bytes));
}

Uuid::operator bool() const { return !isNull(); }
bool Uuid::operator!() const { return isNull(); }

size_t Uuid::size() const { return SIZE; }

const unsigned char* Uuid::data() const
{ 
    return bytes;
}

bool operator==(const Uuid& a, const Uuid& b)
{
    // This const cast is for Solaris which has non const arguments
    return uuid_compare(const_cast<uint8_t*>(a.bytes), const_cast<uint8_t*>(b.bytes)) == 0;
}

bool operator!=(const Uuid& a, const Uuid& b)
{
    return !(a == b);
}

bool operator<(const Uuid& a, const Uuid& b)
{
    // This const cast is for Solaris which has non const arguments
    return uuid_compare(const_cast<uint8_t*>(a.bytes), const_cast<uint8_t*>(b.bytes)) < 0;
}

bool operator>(const Uuid& a, const Uuid& b)
{
    // This const cast is for Solaris which has non const arguments
    return uuid_compare(const_cast<uint8_t*>(a.bytes), const_cast<uint8_t*>(b.bytes)) > 0;
}

bool operator<=(const Uuid& a, const Uuid& b)
{
    // This const cast is for Solaris which has non const arguments
    return uuid_compare(const_cast<uint8_t*>(a.bytes), const_cast<uint8_t*>(b.bytes)) <= 0;
}

bool operator>=(const Uuid& a, const Uuid& b)
{
    // This const cast is for Solaris which has non const arguments
    return uuid_compare(const_cast<uint8_t*>(a.bytes), const_cast<uint8_t*>(b.bytes)) >= 0;
}

ostream& operator<<(ostream& out, Uuid uuid)
{
    char unparsed[UNPARSED_SIZE + 1];
    uuid_unparse(uuid.bytes, unparsed);
    return out << unparsed;
}

istream& operator>>(istream& in, Uuid& uuid)
{
    char unparsed[UNPARSED_SIZE + 1] = {0};
    in.get(unparsed, sizeof(unparsed));
    if (uuid_parse(unparsed, uuid.bytes) != 0) 
        in.setstate(ios::failbit);
    return in;
}

std::string Uuid::str() const
{
    std::ostringstream os;
    os << *this;
    return os.str();
}

size_t Uuid::hash() const {
    std::size_t seed = 0;
    for(size_t i = 0; i < SIZE; ++i)
        seed ^= static_cast<std::size_t>(bytes[i]) + 0x9e3779b9 + (seed << 6) + (seed >> 2);
    return seed;
}


}} // namespace qpid::types
