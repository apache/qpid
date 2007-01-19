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

#include "OutputHandler.h"
#include "ProtocolVersion.h"

namespace qpid {
namespace framing {

class BodyHandler;

/**
 * Invocation context for an AMQP method.
 * Some of the context information is related to the channel, some
 * to the specific invocation - e.g. requestId.
 * 
 * All generated proxy and handler functions take a MethodContext parameter.
 * 
 * The user does not need to create MethodContext objects explicitly,
 * the constructor will implicitly create one from a channel ID.
 * 
 * Other context members are for internal use.
 */
struct MethodContext
{
    /**
     * Passing a integer channel-id in place of a MethodContext
     * will automatically construct the MethodContext.
     */
    MethodContext(
        ChannelId channel, OutputHandler* output=0, RequestId request=0)
        : channelId(channel), out(output), requestId(request){}

    /** \internal Channel on which the method is sent. */
    const ChannelId channelId;

    /** Output handler for responses in this context */
    OutputHandler* out;

    /** \internal If we are in the context of processing an incoming request,
     * this is the ID. Otherwise it is 0.
     */ 
    const RequestId requestId;

};

}} // namespace qpid::framing



#endif  /*!_framing_MethodContext_h*/
