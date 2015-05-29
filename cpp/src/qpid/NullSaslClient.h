#ifndef QPID_NULLSASLCLIENT_H
#define QPID_NULLSASLCLIENT_H

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
#include "Sasl.h"

namespace qpid {

class NullSaslClient : public Sasl
{
  public:
    NullSaslClient(const std::string& username, const std::string& password);
    bool start(const std::string& mechanisms, std::string& response,
                       const qpid::sys::SecuritySettings* externalSecuritySettings = 0);
    std::string step(const std::string& challenge);
    std::string getMechanism();
    std::string getUserId();
    std::auto_ptr<qpid::sys::SecurityLayer> getSecurityLayer(uint16_t maxFrameSize);
  private:
    const std::string username;
    const std::string password;
    std::string mechanism;
};
} // namespace qpid

#endif  /*!QPID_NULLSASLCLIENT_H*/
