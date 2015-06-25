#ifndef QPID_SASLSERVER_H
#define QPID_SASLSERVER_H

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
#include <string>
#include <memory>

namespace qpid {
namespace sys {
class SecurityLayer;
}
/**
 *
 */
class SaslServer
{
  public:
    typedef enum {OK, FAIL, CHALLENGE} Status;
    QPID_COMMON_EXTERN virtual ~SaslServer() {}
    virtual Status start(const std::string& mechanism, const std::string* response, std::string& challenge) = 0;
    virtual Status step(const std::string* response, std::string& challenge) = 0;
    virtual std::string getMechanisms() = 0;
    virtual std::string getUserid() = 0;
    virtual std::auto_ptr<qpid::sys::SecurityLayer> getSecurityLayer(size_t) = 0;
  private:
};
} // namespace qpid

#endif  /*!QPID_SASLSERVER_H*/
