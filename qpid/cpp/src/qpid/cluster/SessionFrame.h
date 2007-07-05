#ifndef QPID_CLUSTER_SESSIONFRAME_H
#define QPID_CLUSTER_SESSIONFRAME_H

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

#include "qpid/framing/Handler.h"
#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/Uuid.h"

#include <ostream>

namespace qpid {

namespace framing {
class AMQFrame;
class Buffer;
}

namespace cluster {

/**
 * An AMQFrame with a UUID and direction.
 */
struct SessionFrame
{
    SessionFrame() : isIncoming(false) {}
    
    SessionFrame(const framing::Uuid& id, const framing::AMQFrame& f, bool incoming)
        : uuid(id), frame(f), isIncoming(incoming) {}
    
    void encode(framing::Buffer&);

    void decode(framing::Buffer&);

    size_t size() const;
    
    static const bool IN = true;
    static const bool OUT = false;

    framing::Uuid uuid;
    framing::AMQFrame frame;
    bool isIncoming;
};

typedef framing::Handler<SessionFrame&> SessionFrameHandler;

std::ostream& operator<<(std::ostream&, const SessionFrame&);

}} // namespace qpid::cluster



#endif  /*!QPID_CLUSTER_SESSIONFRAME_H*/
