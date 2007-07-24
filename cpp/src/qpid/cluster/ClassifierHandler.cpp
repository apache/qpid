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

#include "ClassifierHandler.h"

#include "qpid/framing/AMQFrame.h"
#include "qpid/framing/ExchangeDeclareBody.h"
#include "qpid/framing/ExchangeDeleteBody.h"
#include "qpid/framing/QueueBindBody.h"
#include "qpid/framing/QueueDeclareBody.h"
#include "qpid/framing/QueueDeleteBody.h"
#include "qpid/framing/QueueUnbindBody.h"


namespace qpid {
namespace cluster {

using namespace framing;

typedef uint32_t FullMethodId;  // Combind class & method ID.

FullMethodId fullId(ClassId c, MethodId m) { return c<<16+m; }

FullMethodId fullId(const shared_ptr<AMQMethodBody>& body) {
    return fullId(body->amqpClassId(), body->amqpMethodId());
}

template <class M>
FullMethodId fullId() { return fullId(M::CLASS_ID, M::METHOD_ID); }


ClassifierHandler::ClassifierHandler(Chain wiring, Chain other)
    : FrameHandler(other)
{
    map[fullId<ExchangeDeclareBody>()] = wiring;
    map[fullId<ExchangeDeleteBody>()] = wiring;
    map[fullId<QueueBindBody>()] = wiring;
    map[fullId<QueueDeclareBody>()] = wiring;
    map[fullId<QueueDeleteBody>()] = wiring;
    map[fullId<QueueUnbindBody>()] = wiring;
}

void  ClassifierHandler::handle(AMQFrame& frame) {
    // TODO aconway 2007-07-03: Flatten the frame hierarchy so we
    // can do a single lookup to dispatch a frame.
    Chain chosen;
    shared_ptr<AMQMethodBody> method =
        dynamic_pointer_cast<AMQMethodBody>(frame.getBody());
    // FIXME aconway 2007-07-05: Need to stop bypassed frames
    // from overtaking mcast frames.
    //
    if (method) 
        chosen=map[fullId(method)];
    if (chosen)
        chosen->handle(frame);
    else
        next->handle(frame);
}
 

}} // namespace qpid::cluster
