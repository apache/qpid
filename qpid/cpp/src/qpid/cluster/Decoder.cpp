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
#include "Decoder.h"
#include "EventFrame.h"
#include "qpid/framing/ClusterConnectionDeliverCloseBody.h"
#include "qpid/framing/Buffer.h"
#include "qpid/framing/AMQFrame.h"


namespace qpid {
namespace cluster {

void Decoder::decode(const EventHeader& eh, const char* data) {
    assert(eh.getType() == DATA); // Only handle connection data events.
    const char* cp = static_cast<const char*>(data);
    framing::Buffer buf(const_cast<char*>(cp), eh.getSize());
    framing::FrameDecoder& decoder = map[eh.getConnectionId()];
    if (decoder.decode(buf)) {  // Decoded a frame
        framing::AMQFrame frame(decoder.getFrame());
        while (decoder.decode(buf)) {
            process(EventFrame(eh, frame));
            frame = decoder.getFrame();
        }
        // Set read-credit on the last frame ending in this event.
        // Credit will be given when this frame is processed.
        process(EventFrame(eh, frame, 1)); 
    }
    else {
        // We must give 1 unit read credit per event.
        // This event does not complete any frames so 
        // send an empty frame with the read credit.
        process(EventFrame(EventHeader(), framing::AMQFrame(), 1));
    }    
}

void Decoder::process(const EventFrame& ef) {
    if (ef.frame.getMethod() && ef.frame.getMethod()->isA<framing::ClusterConnectionDeliverCloseBody>())
        map.erase(ef.connectionId);
    callback(ef);
}

}} // namespace qpid::cluster
