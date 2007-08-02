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

#include "Correlator.h"

using qpid::client::Correlator;
using namespace qpid::framing;
using namespace boost;

void Correlator::receive(AMQMethodBody::shared_ptr response)
{
    if (listeners.empty()) {
        throw ConnectionException(503, "Unexpected method!");//TODO: include the method & class name
    } else {
        Listener l = listeners.front();
        if (l) l(response);
        listeners.pop();
    }
}

void Correlator::listen(Listener l)
{
    listeners.push(l);
}


