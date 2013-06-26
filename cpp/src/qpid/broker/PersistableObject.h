#ifndef QPID_BROKER_PERSISTABLEOBJECT_H
#define QPID_BROKER_PERSISTABLEOBJECT_H

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
#include "PersistableConfig.h"
#include "qpid/types/Variant.h"
#include <vector>
#include <boost/shared_ptr.hpp>

namespace qpid {
namespace broker {
class Broker;
class RecoverableConfig;
/**
 * Generic persistence support for objects created through the brokers
 * create method.
 */
class PersistableObject : public PersistableConfig
{
  public:
    PersistableObject(const std::string& name, const std::string& type, const qpid::types::Variant::Map properties);
    virtual ~PersistableObject();
    const std::string& getName() const;
    void setPersistenceId(uint64_t id) const;
    uint64_t getPersistenceId() const;
    void encode(framing::Buffer& buffer) const;
    uint32_t encodedSize() const;
  friend class RecoveredObjects;
  private:
    std::string name;
    std::string type;
    qpid::types::Variant::Map properties;
    mutable uint64_t id;

    PersistableObject();
    void decode(framing::Buffer& buffer);
    bool recover(Broker&);
};

class RecoveredObjects
{
  public:
    boost::shared_ptr<RecoverableConfig> recover(framing::Buffer&);
    void restore(Broker&);
  private:
    typedef std::vector<boost::shared_ptr<PersistableObject> > Objects;
    Objects objects;
};

}} // namespace qpid::broker

#endif  /*!QPID_BROKER_PERSISTABLEOBJECT_H*/
