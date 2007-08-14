#ifndef _framing_MethodContext_h
#define _framing_MethodContext_h

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

#include <boost/shared_ptr.hpp>

#include "OutputHandler.h"
#include "ProtocolVersion.h"

namespace qpid {
namespace framing {

class BodyHandler;
class AMQMethodBody;
class ChannelAdapter;

/**
 * Invocation context for an AMQP method.
 * 
 * It provides the method being processed and the channel on which
 * it arrived.
 *
 * All Handler functions take a MethodContext as the last parameter.
 */
struct MethodContext
{
    typedef boost::shared_ptr<AMQMethodBody> BodyPtr;

    MethodContext(ChannelAdapter* ch=0, BodyPtr method=BodyPtr())
        : channel(ch), methodBody(method) {}

    /**
     * Channel on which the method being processed arrived.
     * 0 if the method was constructed by the caller
     * rather than received from a channel.
     */
    ChannelAdapter* channel;

    /**
     * Body of the method being processed.
     * It's useful for passing around instead of unpacking all its parameters.
     * It's also provides the request ID  when constructing a response.
     */
    BodyPtr methodBody;
};


}} // namespace qpid::framing



#endif  /*!_framing_MethodContext_h*/
