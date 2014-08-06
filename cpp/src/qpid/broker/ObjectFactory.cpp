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
#include "ObjectFactory.h"
#include "Broker.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace broker {

bool ObjectFactoryRegistry::createObject(Broker& broker, const std::string& type, const std::string& name, const qpid::types::Variant::Map& properties,
                                         const std::string& userId, const std::string& connectionId)
{
    for (Factories::iterator i = factories.begin(); i != factories.end(); ++i)
    {
        if ((*i)->createObject(broker, type, name, properties, userId, connectionId)) return true;
    }
    return false;
}

bool ObjectFactoryRegistry::deleteObject(Broker& broker, const std::string& type, const std::string& name, const qpid::types::Variant::Map& properties,
                                         const std::string& userId, const std::string& connectionId)
{
    for (Factories::iterator i = factories.begin(); i != factories.end(); ++i)
    {
        if ((*i)->deleteObject(broker, type, name, properties, userId, connectionId)) return true;
    }
    return false;
}

bool ObjectFactoryRegistry::recoverObject(Broker& broker, const std::string& type, const std::string& name, const qpid::types::Variant::Map& properties,
                                          uint64_t persistenceId)
{
    for (Factories::iterator i = factories.begin(); i != factories.end(); ++i)
    {
        try {
            if ((*i)->recoverObject(broker, type, name, properties, persistenceId)) return true;
        } catch (const std::exception& e) {
            QPID_LOG(warning, "Error while recovering object " << name << " of type " << type << ": " << e.what());
        }
    }
    return false;
}

ObjectFactoryRegistry::~ObjectFactoryRegistry()
{
    for (Factories::iterator i = factories.begin(); i != factories.end(); ++i)
    {
        delete *i;
    }
    factories.clear();
}
void ObjectFactoryRegistry::add(ObjectFactory* factory)
{
    factories.push_back(factory);
}

}} // namespace qpid::broker
