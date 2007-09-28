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

#ifndef _Response_
#define _Response_

#include <boost/shared_ptr.hpp>
#include "qpid/framing/amqp_framing.h"
#include "Completion.h"

namespace qpid {
namespace client {

class Response : public Completion
{
public:
    Response(Future f, shared_ptr<SessionCore> s) : Completion(f, s) {}

    template <class T> T& as() 
    {
        framing::AMQMethodBody* response(future.getResponse(*session));
        return *boost::polymorphic_downcast<T*>(response);
    }

    template <class T> bool isA() 
    {
        framing::AMQMethodBody* response(future.getResponse(*session));
        return response && response->isA<T>();
    }
};

}}

#endif
