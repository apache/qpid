#ifndef QPID_BROKER_OBJECTFACTORY_H
#define QPID_BROKER_OBJECTFACTORY_H

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
#include "qpid/broker/BrokerImportExport.h"
#include "qpid/types/Variant.h"
#include <vector>

namespace qpid {
namespace broker {

class Broker;

/**
 * An extension point through which plugins can register functionality
 * for creating (and deleting) particular types of objects via the
 * Broker::createObject() method (or deleteObject()), invoked via management.
 */
class ObjectFactory
{
  public:
    virtual bool createObject(Broker&, const std::string& type, const std::string& name, const qpid::types::Variant::Map& properties,
                              const std::string& userId, const std::string& connectionId) = 0;
    virtual bool deleteObject(Broker&, const std::string& type, const std::string& name, const qpid::types::Variant::Map& properties,
                              const std::string& userId, const std::string& connectionId) = 0;
    virtual bool recoverObject(Broker&, const std::string& type, const std::string& name, const qpid::types::Variant::Map& properties,  uint64_t persistenceId) = 0;
    virtual ~ObjectFactory() {}
  private:
};

class ObjectFactoryRegistry : public ObjectFactory
{
  public:
    bool createObject(Broker&, const std::string& type, const std::string& name, const qpid::types::Variant::Map& properties,
                      const std::string& userId, const std::string& connectionId);
    bool deleteObject(Broker&, const std::string& type, const std::string& name, const qpid::types::Variant::Map& properties,
                      const std::string& userId, const std::string& connectionId);
    bool recoverObject(Broker&, const std::string& type, const std::string& name, const qpid::types::Variant::Map& properties,  uint64_t persistenceId);

    ~ObjectFactoryRegistry();
    QPID_BROKER_EXTERN void add(ObjectFactory*);
  private:
    typedef std::vector<ObjectFactory*> Factories;
    Factories factories;
};

}} // namespace qpid::broker

#endif  /*!QPID_BROKER_OBJECTFACTORY_H*/
