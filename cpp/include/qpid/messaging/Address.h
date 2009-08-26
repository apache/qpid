#ifndef QPID_MESSAGING_ADDRESS_H
#define QPID_MESSAGING_ADDRESS_H

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
#include "qpid/client/ClientImportExport.h"
#include <ostream>

namespace qpid {
namespace client {
}

namespace messaging {

/**
 * Represents an address to which messages can be sent and from which
 * messages can be received. Often a simple name is sufficient for
 * this. However this struct allows the type of address to be
 * specified allowing more sophisticated treatment if necessary.
 */
struct Address
{
    std::string value;
    std::string type;

    QPID_CLIENT_EXTERN Address();
    QPID_CLIENT_EXTERN Address(const std::string& address);
    QPID_CLIENT_EXTERN Address(const std::string& address, const std::string& type);
    QPID_CLIENT_EXTERN operator const std::string&() const;
    QPID_CLIENT_EXTERN const std::string& toStr() const;
    QPID_CLIENT_EXTERN operator bool() const;
    QPID_CLIENT_EXTERN bool operator !() const;
};

QPID_CLIENT_EXTERN std::ostream& operator<<(std::ostream& out, const Address& address);

}} // namespace qpid::messaging

#endif  /*!QPID_MESSAGING_ADDRESS_H*/
