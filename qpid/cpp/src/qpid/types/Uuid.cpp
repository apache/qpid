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
#include <iomanip>
#include <stdio.h>
#include <string.h>

namespace qpid {
namespace types {

using namespace std;

const size_t Uuid::SIZE=16;
static const int UNPARSED_SIZE=36;

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

Uuid::Uuid(const char* uuid)
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
    sys::uuid_generate(bytes);
}

void Uuid::clear()
{
    ::memset(bytes, 0, Uuid::SIZE);
}

bool Uuid::isNull() const
{
    static Uuid nullUuid;
    return *this == nullUuid;
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
    return ::memcmp(a.bytes, b.bytes, Uuid::SIZE) == 0;
}

bool operator!=(const Uuid& a, const Uuid& b)
{
    return !(a == b);
}

bool operator<(const Uuid& a, const Uuid& b)
{
    return ::memcmp(a.bytes, b.bytes, Uuid::SIZE) < 0;
}

bool operator>(const Uuid& a, const Uuid& b)
{
    return ::memcmp(a.bytes, b.bytes, Uuid::SIZE) > 0;
}

bool operator<=(const Uuid& a, const Uuid& b)
{
    return ::memcmp(a.bytes, b.bytes, Uuid::SIZE) <= 0;
}

bool operator>=(const Uuid& a, const Uuid& b)
{
    return ::memcmp(a.bytes, b.bytes, Uuid::SIZE) >= 0;
}

ostream& operator<<(ostream& out, Uuid uuid)
{
    const uint8_t* bytes = uuid.bytes;

    ios_base::fmtflags f = out.flags();
    out << hex << setfill('0')
        << setw(2) << int(bytes[0])
        << setw(2) << int(bytes[1])
        << setw(2) << int(bytes[2])
        << setw(2) << int(bytes[3])
        << "-"
        << setw(2) << int(bytes[4])
        << setw(2) << int(bytes[5])
        << "-"
        << setw(2) << int(bytes[6])
        << setw(2) << int(bytes[7])
        << "-"
        << setw(2) << int(bytes[8])
        << setw(2) << int(bytes[9])
        << "-"
        << setw(2) << int(bytes[10])
        << setw(2) << int(bytes[11])
        << setw(2) << int(bytes[12])
        << setw(2) << int(bytes[13])
        << setw(2) << int(bytes[14])
        << setw(2) << int(bytes[15]);
    out.flags(f);
    return out;
}

istream& operator>>(istream& in, Uuid& uuid)
{
    unsigned bytes[16];
    char unparsed[UNPARSED_SIZE + 1] = {0};

    istream::sentry s(in);
    if ( !s ) return in;

    in.get(unparsed, UNPARSED_SIZE+1);

    // Check if we read enough characters
    if ( in.gcount()!=UNPARSED_SIZE ) {
        in.setstate(ios::failbit);
        return in;
    }
    int r = ::sscanf(unparsed, "%2x%2x%2x%2x-"
                               "%2x%2x-"
                               "%2x%2x-"
                               "%2x%2x-"
                               "%2x%2x%2x%2x%2x%2x",
                     &bytes[0], &bytes[1], &bytes[2], &bytes[3],
                     &bytes[4], &bytes[5],
                     &bytes[6], &bytes[7],
                     &bytes[8], &bytes[9],
                     &bytes[10], &bytes[11], &bytes[12], &bytes[13], &bytes[14], &bytes[15]
                    );
    // Check if we got enough converted input
    if ( r!=int(Uuid::SIZE) ) {
        in.setstate(ios::failbit);
        return in;
    }

    for (unsigned i=0; i<16; ++i) {
      uuid.bytes[i] = bytes[i];
    }
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
