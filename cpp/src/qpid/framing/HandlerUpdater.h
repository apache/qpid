#ifndef QPID_FRAMING_HANDLERUPDATER_H
#define QPID_FRAMING_HANDLERUPDATER_H

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

#include "qpid/Plugin.h"
#include "qpid/framing/amqp_types.h"
#include "qpid/framing/FrameHandler.h"

namespace qpid {
namespace framing {

/** Interface for objects that can update handler chains. */
struct HandlerUpdater {
    virtual ~HandlerUpdater() {}
    
    /** Update the handler chains.
     *@param channel Id of associated channel.
     *@param chains Handler chains to be updated.
     */
    virtual void update(ChannelId channel, FrameHandler::Chains& chains) = 0;
};

}} // namespace qpid::framing





#endif  /*!QPID_FRAMING_HANDLERUPDATER_H*/
