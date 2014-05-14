#ifndef QPID_BROKER_CONNECTION_H
#define QPID_BROKER_CONNECTION_H

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
#include "OwnershipToken.h"
#include <map>
#include <string>

namespace qpid {
namespace management {
class ObjectId;
}
namespace types {
class Variant;
}

namespace broker {

/**
 * Protocol independent connection abstraction.
 */
class Connection : public OwnershipToken {
public:
    virtual ~Connection() {}
    virtual const management::ObjectId getObjectId() const = 0;
    virtual const std::string& getUserId() const = 0;
    virtual const std::string& getMgmtId() const = 0;
    virtual const std::map<std::string, types::Variant>& getClientProperties() const = 0;
    virtual bool isLink() const = 0;
    virtual void abort() = 0;
};
}} // namespace qpid::broker

#endif  /*!QPID_BROKER_CONNECTION_H*/
