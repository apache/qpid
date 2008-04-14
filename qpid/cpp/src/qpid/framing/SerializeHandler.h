#ifndef QPID_FRAMING_SERIALIZEHANDLER_H
#define QPID_FRAMING_SERIALIZEHANDLER_H

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
#include "qpid/sys/Serializer.h"
#include "qpid/framing/Handler.h"

#include <boost/bind.hpp>

namespace qpid {
namespace framing {


/** Serializer that can be inserted into a Handler chain */
template <class T>
struct SerializeHandler : public framing::Handler<T>, public sys::Serializer {
    SerializeHandler(typename framing::Handler<T>::Chain next)
        : framing::Handler<T>(next) {}
    void handle(T value) {
        execute(boost::bind(&framing::Handler<T>::handle, this->next.get(), value));
    }
};

}} // namespace qpid::framing





#endif  /*!QPID_FRAMING_SERIALIZEHANDLER_H*/
