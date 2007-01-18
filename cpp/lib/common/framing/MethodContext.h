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

namespace qpid {
namespace framing {

class BodyHandler;

/**
 * Invocation context for an AMQP method.
 * All generated proxy and handler functions take a MethodContext parameter.
 * 
 * The user calling on a broker proxy can simply pass an integer
 * channel ID, it will implicitly be converted to an appropriate context.
 *
 * Other context members are for internal use.
 */
struct MethodContext
{
    /**
     * Passing a integer channel-id in place of a MethodContext
     * will automatically construct the MethodContext.
     */
    MethodContext(ChannelId channel, RequestId request=0)
        : channelId(channel), requestId(request) {}

    /** Channel on which the method is sent. */
    ChannelId channelId;
    /** \internal For proxy response: the original request or 0. */
    RequestId requestId;
};

}} // namespace qpid::framing



#endif  /*!_framing_MethodContext_h*/
