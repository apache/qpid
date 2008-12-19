#ifndef QPID_FRAMING_UUID_H
#define QPID_FRAMING_UUID_H

/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#include "qpid/sys/uuid.h"

#include <boost/array.hpp>

#include <ostream>
#include <istream>

namespace qpid {
namespace framing {

class Buffer;

/**
 * A UUID is represented as a boost::array of 16 bytes.
 *
 * Full value semantics, operators ==, < etc.  are provided by
 * boost::array so Uuid can be the key type in a map etc.
 */
struct Uuid : public boost::array<uint8_t, 16> {
    /** If unique is true, generate a unique ID else a null ID. */
    Uuid(bool unique=false) { if (unique) generate(); else clear(); }

    /** Copy from 16 bytes of data. */
    Uuid(uint8_t* data) { assign(data); }

    /** Copy from 16 bytes of data. */
    void assign(uint8_t* data) {
        uuid_copy(c_array(), data);
    }
    
    /** Set to a new unique identifier. */
    void generate() { uuid_generate(c_array()); }

    /** Set to all zeros. */
    void clear() { uuid_clear(c_array()); }
    
    /** Test for null (all zeros). */
    bool isNull() {
        return uuid_is_null(data());
    }

    // Default op= and copy ctor are fine.
    // boost::array gives us ==, < etc.

    void encode(framing::Buffer& buf) const;
    void decode(framing::Buffer& buf);
    uint32_t encodedSize() const { return size(); }

    /** String value in format 1b4e28ba-2fa1-11d2-883f-b9a761bde3fb. */
    std::string str();

    template <class S> void serialize(S& s) {
        s.raw(begin(), size());
    }
};

/** Print in format 1b4e28ba-2fa1-11d2-883f-b9a761bde3fb. */
std::ostream& operator<<(std::ostream&, Uuid);

/** Read from format 1b4e28ba-2fa1-11d2-883f-b9a761bde3fb. */
std::istream& operator>>(std::istream&, Uuid&);

}} // namespace qpid::framing



#endif  /*!QPID_FRAMING_UUID_H*/
