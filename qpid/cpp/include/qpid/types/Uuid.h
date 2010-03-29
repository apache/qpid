#ifndef QPID_TYPES_UUID_H
#define QPID_TYPES_UUID_H

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

#include "qpid/CommonImportExport.h"
#include <iosfwd>
#include <string>

namespace qpid {
namespace types {

class Uuid
{
  public:
    static const size_t SIZE;
    /** 
     * If unique is true, this will generate a new unique uuid, if not
     * it will construct a null uuid.
     */
    QPID_COMMON_EXTERN Uuid(bool unique=false);
    QPID_COMMON_EXTERN Uuid(const Uuid&);
    QPID_COMMON_EXTERN Uuid& operator=(const Uuid&);
    /** Copy the UUID from data16, which must point to a 16-byte UUID */
    QPID_COMMON_EXTERN Uuid(const unsigned char* data16);

    /** Set to a new unique identifier. */
    QPID_COMMON_EXTERN void generate();

    /** Set to all zeros. */
    QPID_COMMON_EXTERN void clear();

    /** Test for null (all zeros). */
    QPID_COMMON_EXTERN bool isNull() const;
    QPID_COMMON_EXTERN operator bool() const;
    QPID_COMMON_EXTERN bool operator!() const;

    /** String value in format 1b4e28ba-2fa1-11d2-883f-b9a761bde3fb. */
    QPID_COMMON_EXTERN std::string str() const;

    QPID_COMMON_EXTERN size_t size() const;
    QPID_COMMON_EXTERN const unsigned char* data() const;

    friend QPID_COMMON_EXTERN bool operator==(const Uuid&, const Uuid&);
    friend QPID_COMMON_EXTERN bool operator!=(const Uuid&, const Uuid&);
    friend QPID_COMMON_EXTERN bool operator<(const Uuid&, const Uuid&);
    friend QPID_COMMON_EXTERN bool operator>(const Uuid&, const Uuid&);
    friend QPID_COMMON_EXTERN bool operator<=(const Uuid&, const Uuid&);
    friend QPID_COMMON_EXTERN bool operator>=(const Uuid&, const Uuid&);
    friend QPID_COMMON_EXTERN std::ostream& operator<<(std::ostream&, Uuid);
    friend QPID_COMMON_EXTERN std::istream& operator>>(std::istream&, Uuid&);

  private:
    unsigned char bytes[16];
};

/** Returns true if the uuids are equal, false otherwise. **/
QPID_COMMON_EXTERN bool operator==(const Uuid&, const Uuid&);
/** Returns true if the uuids are NOT equal, false if they are. **/
QPID_COMMON_EXTERN bool operator!=(const Uuid&, const Uuid&);

QPID_COMMON_EXTERN bool operator<(const Uuid&, const Uuid&);
QPID_COMMON_EXTERN bool operator>(const Uuid&, const Uuid&);
QPID_COMMON_EXTERN bool operator<=(const Uuid&, const Uuid&);
QPID_COMMON_EXTERN bool operator>=(const Uuid&, const Uuid&);

/** Print in format 1b4e28ba-2fa1-11d2-883f-b9a761bde3fb. */
QPID_COMMON_EXTERN std::ostream& operator<<(std::ostream&, Uuid);

/** Read from format 1b4e28ba-2fa1-11d2-883f-b9a761bde3fb. */
QPID_COMMON_EXTERN std::istream& operator>>(std::istream&, Uuid&);

}} // namespace qpid::types

#endif  /*!QPID_TYPES_UUID_H*/
