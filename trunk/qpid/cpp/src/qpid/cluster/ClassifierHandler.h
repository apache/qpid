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

namespace qpid {
namespace cluster {

/**
 * Classify frames and forward to the appropriate handler.
 */
class ClassifierHandler : public framing::FrameHandler
{
  public:
    ClassifierHandler(framing::FrameHandler& wiring_,
                      framing::FrameHandler& other_)
        : wiring(wiring_), other(other_) {}

    void handle(framing::AMQFrame&);
    
  private:
    struct Visitor;
  friend struct Visitor;
    framing::FrameHandler& wiring;
    framing::FrameHandler& other;
};

}} // namespace qpid::cluster



#endif  /*!QPID_CLUSTER_CLASSIFIERHANDLER_H*/
