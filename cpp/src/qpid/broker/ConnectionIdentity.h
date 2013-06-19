#ifndef QPID_BROKER_CONNECTIONIDENTITY_H
#define QPID_BROKER_CONNECTIONIDENTITY_H

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

namespace qpid {

namespace management {
class ObjectId;
}

namespace broker {

class OwnershipToken;

// Interface used to hold Connection authentication and object details for use when authenticating
// publihed management requests.
class ConnectionIdentity {
public:
    virtual const OwnershipToken* getOwnership() const = 0;
    virtual const management::ObjectId getObjectId() const = 0;
    virtual const std::string& getUserId() const = 0;
    virtual const std::string& getUrl() const = 0;
};

}}
#endif // QPID_BROKER_CONNECTIONIDENTITY_H
