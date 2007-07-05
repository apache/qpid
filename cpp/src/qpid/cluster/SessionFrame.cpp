/*
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

#include "SessionFrame.h"

#include "qpid/QpidError.h"

namespace qpid {
namespace cluster {

void SessionFrame::encode(framing::Buffer& buf) {
    uuid.encode(buf);
    frame.encode(buf);
    buf.putOctet(isIncoming);
}

void SessionFrame::decode(framing::Buffer& buf) {
    uuid.decode(buf);
    if (!frame.decode(buf))
        THROW_QPID_ERROR(FRAMING_ERROR, "Incomplete frame");
    isIncoming = buf.getOctet();
}

size_t SessionFrame::size() const {
    return uuid.size() + frame.size() + 1 /*isIncoming*/;
}

std::ostream& operator<<(std::ostream& out, const SessionFrame& frame) {
    return out << "[session=" << frame.uuid
               << (frame.isIncoming ? ",in: ":",out: ")
               << frame.frame << "]";
}

}} // namespace qpid::cluster
