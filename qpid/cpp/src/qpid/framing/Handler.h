#ifndef QPID_FRAMING_HANDLER_H
#define QPID_FRAMING_HANDLER_H

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
#include "qpid/shared_ptr.h"
#include <assert.h>

namespace qpid {
namespace framing {

/** Interface for handler for values of type T.
 * Handlers can be linked into chains via the next pointer.
 */
template <class T> struct Handler {
    typedef T ParamType;
    typedef shared_ptr<Handler> Chain;

    /** Handler chains for incoming and outgoing traffic. */
    struct Chains {
        Chain in;
        Chain out;
    };

    virtual ~Handler() {}
    virtual void handle(T) = 0;
    Chain next;
};


}}


#endif  /*!QPID_FRAMING_HANDLER_H*/
