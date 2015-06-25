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
#include "PersistableObject.h"
#include "Broker.h"
#include "qpid/amqp_0_10/Codecs.h"
#include "qpid/amqp_0_10/CodecsInternal.h"
#include "qpid/log/Statement.h"

namespace qpid {
namespace broker {
namespace {
const std::string UTF8("utf8");
}
PersistableObject::PersistableObject(const std::string& n, const std::string& t, const qpid::types::Variant::Map p) : name(n), type(t), properties(p), id(0) {}
PersistableObject::PersistableObject() : id(0) {}
PersistableObject::~PersistableObject() {}
const std::string& PersistableObject::getName() const { return name; }
const std::string& PersistableObject::getType() const { return type; }
void PersistableObject::setPersistenceId(uint64_t i) const { id = i; }
uint64_t PersistableObject::getPersistenceId() const { return id; }
void PersistableObject::encode(framing::Buffer& buffer) const
{
    buffer.putShortString(type);
    buffer.putMediumString(name);
    qpid::amqp_0_10::encode(properties, qpid::amqp_0_10::encodedSize(properties), buffer);
}
uint32_t PersistableObject::encodedSize() const
{
    return type.size()+1 + name.size()+2 + qpid::amqp_0_10::encodedSize(properties);
}
void PersistableObject::decode(framing::Buffer& buffer)
{
    buffer.getShortString(type);
    buffer.getMediumString(name);
    qpid::framing::FieldTable ft;
    buffer.get(ft);
    qpid::amqp_0_10::translate(ft, properties);
}
bool PersistableObject::recover(Broker& broker)
{
    return broker.getObjectFactoryRegistry().recoverObject(broker, type, name, properties, id);
}

namespace {
class RecoverableObject : public RecoverableConfig
{
  public:
    RecoverableObject(boost::shared_ptr<PersistableObject> o) : object(o) {}
    void setPersistenceId(uint64_t id) { object->setPersistenceId(id); }
  private:
    boost::shared_ptr<PersistableObject> object;
};
}
boost::shared_ptr<RecoverableConfig> RecoveredObjects::recover(framing::Buffer& buffer)
{
    boost::shared_ptr<PersistableObject> object(new PersistableObject());
    object->decode(buffer);
    objects.push_back(object);
    return boost::shared_ptr<RecoverableConfig>(new RecoverableObject(object));
}
void RecoveredObjects::restore(Broker& broker)
{
    //recover objects created through ObjectFactory
    for (Objects::iterator i = objects.begin(); i != objects.end(); ++i) {
        if (!(*i)->recover(broker)) {
            QPID_LOG(warning, "Failed to recover object " << (*i)->name << " of type " << (*i)->type);
        }
    }
}

}} // namespace qpid::broker
