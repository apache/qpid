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
#include "qpid/broker/amqp/Topic.h"
#include "qpid/broker/Broker.h"
#include "qpid/management/ManagementAgent.h"

namespace _qmf = qmf::org::apache::qpid::broker;

namespace qpid {
namespace broker {
namespace amqp {
namespace {
const std::string TOPIC("topic");
const std::string EXCHANGE("exchange");
const std::string DURABLE("durable");
const std::string ALTERNATE_EXCHANGE("alternate-exchange");
const std::string EMPTY;

std::string getProperty(const std::string& k, const qpid::types::Variant::Map& m)
{
    qpid::types::Variant::Map::const_iterator i = m.find(k);
    if (i == m.end()) return EMPTY;
    else return i->second;
}

bool testProperty(const std::string& k, const qpid::types::Variant::Map& m)
{
    qpid::types::Variant::Map::const_iterator i = m.find(k);
    if (i == m.end()) return false;
    else return i->second;
}

qpid::types::Variant::Map filter(const qpid::types::Variant::Map& properties, bool queue)
{
    qpid::types::Variant::Map filtered = properties;
    filtered.erase(DURABLE);
    filtered.erase(EXCHANGE);
    if (queue) filtered.erase(ALTERNATE_EXCHANGE);
    return filtered;
}
}

Topic::Topic(Broker& broker, const std::string& n, boost::shared_ptr<Exchange> e, const qpid::types::Variant::Map& properties)
    : PersistableObject(n, TOPIC, properties), name(n), durable(testProperty(DURABLE, properties)), exchange(e),
      alternateExchange(getProperty(ALTERNATE_EXCHANGE, properties))
{
    if (exchange->getName().empty()) throw qpid::Exception("Exchange must be specified.");
    if (durable && !exchange->isDurable()) throw qpid::Exception("Durable topic must be backed by durable exchange");

    qpid::types::Variant::Map unused;
    qpid::types::Variant::Map filtered = filter(properties, true);
    policy.populate(filtered, unused);

    qpid::management::ManagementAgent* agent = broker.getManagementAgent();
    if (agent != 0) {
        topic = _qmf::Topic::shared_ptr(new _qmf::Topic(agent, this, name, exchange->GetManagementObject()->getObjectId(), durable));
        topic->set_properties(filter(properties, false));
        agent->addObject(topic);
    }
}

bool Topic::isDurable() const
{
    return durable;
}

Topic::~Topic()
{
    if (topic != 0) topic->resourceDestroy();
}

boost::shared_ptr<qpid::management::ManagementObject> Topic::GetManagementObject() const
{
    return topic;
}

const QueueSettings& Topic::getPolicy() const
{
    return policy;
}
boost::shared_ptr<Exchange> Topic::getExchange()
{
    return exchange;
}
const std::string& Topic::getName() const
{
    return name;
}
const std::string& Topic::getAlternateExchange() const
{
    return alternateExchange;
}
boost::shared_ptr<Topic> TopicRegistry::createTopic(Broker& broker, const std::string& name, boost::shared_ptr<Exchange> exchange, const qpid::types::Variant::Map& properties)
{
    boost::shared_ptr<Topic> topic(new Topic(broker, name, exchange, properties));
    add(topic);
    topic->getExchange()->setDeletionListener(name, boost::bind(&TopicRegistry::remove, this, name));
    return topic;
}

boost::shared_ptr<Topic> TopicRegistry::declare(Broker& broker, const std::string& name, boost::shared_ptr<Exchange> exchange, const qpid::types::Variant::Map& properties)
{
    qpid::sys::Mutex::ScopedLock l(lock);
    Topics::const_iterator i = topics.find(name);
    if (i == topics.end()) {
        boost::shared_ptr<Topic> topic(new Topic(broker, name, exchange, properties));
        topics.insert(Topics::value_type(name, topic));
        topic->getExchange()->setDeletionListener(name, boost::bind(&TopicRegistry::remove, this, name));
        return topic;
    } else {
        return i->second;
    }
}

bool TopicRegistry::createObject(Broker& broker, const std::string& type, const std::string& name, const qpid::types::Variant::Map& props,
                                 const std::string& /*userId*/, const std::string& /*connectionId*/)
{
    if (type == TOPIC) {
        boost::shared_ptr<Topic> topic = createTopic(broker, name, broker.getExchanges().get(getProperty(EXCHANGE, props)), props);
        if (topic->isDurable()) broker.getStore().create(*topic);
        return true;
    } else {
        return false;
    }
}

bool TopicRegistry::deleteObject(Broker& broker, const std::string& type, const std::string& name, const qpid::types::Variant::Map& /*properties*/,
                                 const std::string& /*userId*/, const std::string& /*connectionId*/)
{
    if (type == TOPIC) {
        boost::shared_ptr<Topic> topic = remove(name);
        if (topic) {
            if (topic->isDurable()) broker.getStore().destroy(*topic);
            return true;
        } else {
            return false;
        }
    } else {
        return false;
    }
}

bool TopicRegistry::recoverObject(Broker& broker, const std::string& type, const std::string& name, const qpid::types::Variant::Map& properties,
                   uint64_t persistenceId)
{
    if (type == TOPIC) {
        boost::shared_ptr<Topic> topic = createTopic(broker, name, broker.getExchanges().get(getProperty(EXCHANGE, properties)), properties);
        topic->setPersistenceId(persistenceId);
        return true;
    } else {
        return false;
    }
}

bool TopicRegistry::add(boost::shared_ptr<Topic> topic)
{
    qpid::sys::Mutex::ScopedLock l(lock);
    Topics::const_iterator i = topics.find(topic->getName());
    if (i == topics.end()) {
        topics.insert(Topics::value_type(topic->getName(), topic));
        return true;
    } else {
        throw qpid::types::Exception(QPID_MSG("A topic named " << topic->getName() << " already exists"));
    }

}
boost::shared_ptr<Topic> TopicRegistry::remove(const std::string& name)
{
    boost::shared_ptr<Topic> result;
    qpid::sys::Mutex::ScopedLock l(lock);
    Topics::iterator i = topics.find(name);
    if (i != topics.end()) {
        result = i->second;
        topics.erase(i);
        result->getExchange()->unsetDeletionListener(name);
    }
    return result;
}

boost::shared_ptr<Topic> TopicRegistry::get(const std::string& name)
{
    qpid::sys::Mutex::ScopedLock l(lock);
    Topics::const_iterator i = topics.find(name);
    if (i == topics.end()) {
        return boost::shared_ptr<Topic>();
    } else {
        return i->second;
    }
}

}}} // namespace qpid::broker::amqp
