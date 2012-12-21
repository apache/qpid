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

#include "qpid/broker/ConnectionState.h"

#include "qpid/broker/Broker.h"

namespace qpid {
namespace broker {

void ConnectionState::setUserId(const std::string& uid) {
    userId = uid;
    size_t at = userId.find('@');
    userName = userId.substr(0, at);
    isDefaultRealm = (
        at!= std::string::npos &&
        getBroker().getOptions().realm == userId.substr(at+1,userId.size()));
}
    
}}