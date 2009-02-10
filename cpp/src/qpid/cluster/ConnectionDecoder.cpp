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

#include "ConnectionDecoder.h"
#include "EventFrame.h"
#include "ConnectionMap.h"

namespace qpid {
namespace cluster {

using namespace framing;

ConnectionDecoder::ConnectionDecoder(const Handler& h) : handler(h) {}

void ConnectionDecoder::decode(const EventHeader& eh, const void* data, ConnectionMap& map) {
    assert(eh.getType() == DATA); // Only handle connection data events.
    const char* cp = static_cast<const char*>(data);
    Buffer buf(const_cast<char*>(cp), eh.getSize());
    if (decoder.decode(buf)) {  // Decoded a frame
        AMQFrame frame(decoder.frame);
        while (decoder.decode(buf)) {
            handler(EventFrame(eh, frame));
            frame = decoder.frame;
        }
        handler(EventFrame(eh, frame, 1)); // Set read-credit on the last frame.
    }
    else {
        // We must give 1 unit read credit per event.
        // This event does not contain any complete frames so 
        // we must give read credit directly.
        ConnectionPtr connection = map.getLocal(eh.getConnectionId());
        if (connection)
            connection->giveReadCredit(1);
    }
}

}} // namespace qpid::cluster
