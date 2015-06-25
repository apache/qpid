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

#include "qpid/framing/Uuid.h"

#include "qpid/sys/uuid.h"
#include "qpid/Exception.h"
#include "qpid/framing/Buffer.h"
#include "qpid/framing/reply_exceptions.h"
#include "qpid/Msg.h"

namespace qpid {
namespace framing {

using namespace std;

Uuid::Uuid(bool unique):
  qpid::types::Uuid(unique)
{}

Uuid::Uuid(const uint8_t* data):
  qpid::types::Uuid(data)
{}

void Uuid::encode(Buffer& buf) const {
    buf.putRawData(data(), size());
}

void Uuid::decode(Buffer& buf) {
    if (buf.available() < size())
        throw IllegalArgumentException(QPID_MSG("Not enough data for UUID."));

    // Break qpid::types::Uuid encapsulation - Nasty, but efficient
    buf.getRawData(const_cast<uint8_t*>(data()), size());
}

}} // namespace qpid::framing
