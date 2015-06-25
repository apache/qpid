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
#include "NullSaslClient.h"
#include "qpid/sys/SecurityLayer.h"
#include "Exception.h"

namespace qpid {
namespace {
const std::string ANONYMOUS("ANONYMOUS");
const std::string PLAIN("PLAIN");
}

NullSaslClient::NullSaslClient(const std::string& u, const std::string& p) : username(u), password(p) {}

bool NullSaslClient::start(const std::string& mechanisms, std::string& response,
                           const qpid::sys::SecuritySettings*)
{
    if (!username.empty() && !password.empty() && mechanisms.find(PLAIN) != std::string::npos) {
        mechanism = PLAIN;
        response = ((char)0) + username + ((char)0) + password;
    } else if (mechanisms.find(ANONYMOUS) != std::string::npos) {
        mechanism = ANONYMOUS;
        const char* u = username.empty() ? ANONYMOUS.c_str() : username.c_str();
        response = ((char)0) + u;
    } else {
        throw qpid::Exception("No suitable mechanism!");
    }
    return true;
}
std::string NullSaslClient::step(const std::string&)
{
    return std::string();
}
std::string NullSaslClient::getMechanism()
{
    return mechanism;
}
std::string NullSaslClient::getUserId()
{
    return username.empty() ? ANONYMOUS : username;
}
std::auto_ptr<qpid::sys::SecurityLayer> NullSaslClient::getSecurityLayer(uint16_t)
{
    return std::auto_ptr<qpid::sys::SecurityLayer>();
}
} // namespace qpid
