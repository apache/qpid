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

#include "qpid/CommonImportExport.h"
#include "qpid/sys/IntegerTypes.h"

#include "qpid/types/Uuid.h"

#include <ostream>
#include <istream>

namespace qpid {
namespace framing {

class Buffer;

/**
 * Framing UUID is now a thine wrapper around qpid::types::Uuid
 */
struct Uuid : public qpid::types::Uuid {
    /** If unique is true, generate a unique ID else a null ID. */
    QPID_COMMON_EXTERN Uuid(bool unique=false);

    /** Copy from 16 bytes of data. */
    QPID_COMMON_EXTERN Uuid(const uint8_t* data);

    // We get most of our operations directly from qpid::types::Uuid
    QPID_COMMON_INLINE_EXTERN static size_t size()
        { return SIZE; }

    QPID_COMMON_EXTERN void encode(framing::Buffer& buf) const;
    QPID_COMMON_EXTERN void decode(framing::Buffer& buf);
    QPID_COMMON_INLINE_EXTERN uint32_t encodedSize() const
        { return size(); }
};

}} // namespace qpid::framing

#endif  /*!QPID_FRAMING_UUID_H*/
