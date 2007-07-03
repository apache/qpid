#ifndef QPID_CLUSTER_CLASSIFIERHANDLER_H
#define QPID_CLUSTER_CLASSIFIERHANDLER_H

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

#include "qpid/framing/FrameHandler.h"
#include "qpid/framing/amqp_types.h"

#include <map>

namespace qpid {
namespace cluster {

/**
 * Classify frames and forward to the appropriate handler.
 */
class ClassifierHandler : public framing::FrameHandler
{
  public:
    ClassifierHandler(Chain wiring, Chain other);

    void handle(framing::AMQFrame& frame);

  private:
    std::map<uint32_t, framing::FrameHandler::Chain> map;
};

}} // namespace qpid::cluster



#endif  /*!QPID_CLUSTER_CLASSIFIERHANDLER_H*/
