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
#include "qpid/broker/amqp/NodePolicy.h"
#include "qpid/broker/amqp/Connection.h"
#include "qpid/broker/amqp/Topic.h"
#include "qpid/broker/Broker.h"
#include "qpid/broker/Exchange.h"
#include "qpid/types/Exception.h"
#include "qpid/amqp_0_10/Codecs.h"
#include "qpid/log/Statement.h"
#include "qpid/management/ManagementAgent.h"

namespace _qmf = qmf::org::apache::qpid::broker;

namespace qpid {
namespace broker {
namespace amqp {
namespace {
const std::string DURABLE("durable");
const std::string AUTO_DELETE("auto-delete");
const std::string LIFETIME_POLICY("qpid.lifetime-policy");
const std::string MANUAL("manual");
const std::string UNUSED("delete-if-unused");
const std::string UNUSED_AND_EMPTY("delete-if-unused-and-empty");
const std::string QUEUE_POLICY("QueuePolicy");
const std::string TOPIC_POLICY("TopicPolicy");
const std::string QUEUE("queue");
const std::string TOPIC("topic");
const std::string ALTERNATE_EXCHANGE("alternate-exchange");
const std::string EXCHANGE_TYPE("exchange-type");
const std::string QPID_MSG_SEQUENCE("qpid.msg_sequence");
const std::string QPID_IVE("qpid.ive");
const std::string EMPTY;

template <typename T>
T get(const std::string& k, const qpid::types::Variant::Map& m, T defaultValue)
{
    qpid::types::Variant::Map::const_iterator i = m.find(k);
    if (i == m.end()) return defaultValue;
    else return i->second;
}

std::string getProperty(const std::string& k, const qpid::types::Variant::Map& m)
{
    return get(k, m, EMPTY);
}

bool testProperty(const std::string& k, const qpid::types::Variant::Map& m)
{
    return get(k, m, false);
}

qpid::types::Variant::Map filterForQueue(const qpid::types::Variant::Map& properties)
{
    qpid::types::Variant::Map filtered = properties;
    filtered.erase(DURABLE);
    filtered.erase(AUTO_DELETE);
    filtered.erase(ALTERNATE_EXCHANGE);
    return filtered;
}
qpid::types::Variant::Map filterForTopic(const qpid::types::Variant::Map& properties)
{
    qpid::types::Variant::Map filtered = properties;
    filtered.erase(DURABLE);
    filtered.erase(EXCHANGE_TYPE);
    filtered.erase(AUTO_DELETE);
    filtered.erase(QPID_IVE);
    filtered.erase(QPID_MSG_SEQUENCE);
    return filtered;
}
void copy(const std::string& key, const qpid::types::Variant::Map& from, qpid::types::Variant::Map& to)
{
    qpid::types::Variant::Map::const_iterator i = from.find(key);
    if (i != from.end()) to.insert(*i);
}

}
NodePolicy::NodePolicy(const std::string& type, const std::string& ptrn, const qpid::types::Variant::Map& props)
    : PersistableObject(ptrn, type, props), pattern(ptrn),
      durable(testProperty(DURABLE, props)),
      alternateExchange(getProperty(ALTERNATE_EXCHANGE, props)),
      compiled(pattern) {}

NodePolicy::~NodePolicy() {}

const std::string& NodePolicy::getPattern() const
{
    return pattern;
}

bool NodePolicy::isDurable() const
{
    return durable;
}

bool NodePolicy::match(const std::string& name) const
{
    return qpid::sys::regex_match(name, compiled);
}

QueuePolicy::QueuePolicy(Broker& broker, const std::string& pattern, const qpid::types::Variant::Map& props)
    : NodePolicy(QUEUE_POLICY, pattern, props),
      queueSettings(durable, testProperty(AUTO_DELETE, props))
{
    qpid::types::Variant::Map unused;
    qpid::types::Variant::Map filtered = filterForQueue(props);
    //if queue is not durable and neither lifetime policy nor
    //autodelete were explicitly specified, clean it up when not
    //needed by default:
    if (!queueSettings.durable && props.find(LIFETIME_POLICY) == props.end() && props.find(AUTO_DELETE) == props.end()) {
        filtered[LIFETIME_POLICY] = UNUSED_AND_EMPTY;
    }
    queueSettings.populate(filtered, unused);
    qpid::amqp_0_10::translate(filtered, queueSettings.storeSettings);

    qpid::management::ManagementAgent* agent = broker.getManagementAgent();
    if (agent != 0) {
        policy = _qmf::QueuePolicy::shared_ptr(new _qmf::QueuePolicy(agent, this, pattern));
        policy->set_properties(props);
        agent->addObject(policy);
    }
}
QueuePolicy::~QueuePolicy()
{
    if (policy != 0) policy->resourceDestroy();
}


std::pair<boost::shared_ptr<Queue>, boost::shared_ptr<Topic> > QueuePolicy::create(const std::string& name, Connection& connection)
{
    std::pair<boost::shared_ptr<Queue>, boost::shared_ptr<Topic> > result;
    result.first = connection.getBroker().createQueue(name, queueSettings, 0/*not exclusive*/, alternateExchange, connection.getUserId(), connection.getId()).first;
    return result;
}

boost::shared_ptr<qpid::management::ManagementObject> QueuePolicy::GetManagementObject() const
{
    return policy;
}

TopicPolicy::TopicPolicy(Broker& broker, const std::string& pattern, const qpid::types::Variant::Map& props)
    : NodePolicy(TOPIC_POLICY, pattern, props), exchangeType(getProperty(EXCHANGE_TYPE, props)),
      autodelete(get(AUTO_DELETE, props, !durable))
{
    if (exchangeType.empty()) exchangeType = TOPIC;
    broker.getExchanges().checkType(exchangeType);
    qpid::types::Variant::Map::const_iterator i = props.find(LIFETIME_POLICY);
    if (i != props.end()) {
        if (i->second == MANUAL) {
            autodelete = false;
        } else if (i->second == UNUSED || i->second == UNUSED_AND_EMPTY/*though empty doesn't mean much for an exchange*/) {
            autodelete = true;
        } else {
            QPID_LOG(warning, "Did not recognise lifetime policy " << i->second << " in topic policy for " << pattern);
        }
    }
    topicSettings = filterForTopic(props);
    copy(QPID_IVE, props, exchangeSettings);
    copy(QPID_MSG_SEQUENCE, props, exchangeSettings);

    qpid::management::ManagementAgent* agent = broker.getManagementAgent();
    if (agent != 0) {
        policy = _qmf::TopicPolicy::shared_ptr(new _qmf::TopicPolicy(agent, this, pattern));
        policy->set_properties(props);
        agent->addObject(policy);
    }
}

TopicPolicy::~TopicPolicy()
{
    if (policy != 0) policy->resourceDestroy();
}

std::pair<boost::shared_ptr<Queue>, boost::shared_ptr<Topic> > TopicPolicy::create(const std::string& name, Connection& connection)
{
    std::pair<boost::shared_ptr<Queue>, boost::shared_ptr<Topic> > result;
    qpid::framing::FieldTable args;
    qpid::amqp_0_10::translate(exchangeSettings, args);
    boost::shared_ptr<Exchange> exchange = connection.getBroker().createExchange(name, exchangeType, isDurable(), autodelete, alternateExchange,
                                                                                 args, connection.getUserId(), connection.getId()).first;
    result.second = connection.getTopics().declare(connection.getBroker(), name, exchange, topicSettings);
    return result;
}

boost::shared_ptr<qpid::management::ManagementObject> TopicPolicy::GetManagementObject() const
{
    return policy;
}

boost::shared_ptr<NodePolicy> NodePolicyRegistry::createQueuePolicy(Broker& broker, const std::string& name, const qpid::types::Variant::Map& properties)
{
    boost::shared_ptr<NodePolicy> nodePolicy(new QueuePolicy(broker, name, properties));
    add(nodePolicy);
    return nodePolicy;
}

boost::shared_ptr<NodePolicy> NodePolicyRegistry::createTopicPolicy(Broker& broker, const std::string& name, const qpid::types::Variant::Map& properties)
{
    boost::shared_ptr<NodePolicy> nodePolicy(new TopicPolicy(broker, name, properties));
    add(nodePolicy);
    return nodePolicy;
}

boost::shared_ptr<NodePolicy> NodePolicyRegistry::createNodePolicy(Broker& broker, const std::string& type, const std::string& name, const qpid::types::Variant::Map& properties)
{
    if (type == QUEUE_POLICY) {
         return createQueuePolicy(broker, name, properties);
    } else if (type == TOPIC_POLICY) {
         return createTopicPolicy(broker, name, properties);
    } else {
        return boost::shared_ptr<NodePolicy>();
    }
}

bool NodePolicyRegistry::createObject(Broker& broker, const std::string& type, const std::string& name, const qpid::types::Variant::Map& properties,
                                      const std::string& /*userId*/, const std::string& /*connectionId*/)
{
    boost::shared_ptr<NodePolicy> nodePolicy = createNodePolicy(broker, type, name, properties);
    if (nodePolicy) {
        if (nodePolicy->isDurable()) broker.getStore().create(*nodePolicy);
        return true;
    } else {
        return false;
    }
}
bool NodePolicyRegistry::deleteObject(Broker& broker, const std::string& type, const std::string& name, const qpid::types::Variant::Map&,
                                      const std::string& /*userId*/, const std::string& /*connectionId*/)
{
    if (type == QUEUE_POLICY || type == TOPIC_POLICY) {
        boost::shared_ptr<NodePolicy> nodePolicy = remove(name, type);
        if (nodePolicy) {
            if (nodePolicy->isDurable()) broker.getStore().destroy(*nodePolicy);
            return true;
        } else {
            return false;
        }
    } else {
        return false;
    }
}
bool NodePolicyRegistry::recoverObject(Broker& broker, const std::string& type, const std::string& name, const qpid::types::Variant::Map& properties,
                                       uint64_t persistenceId)
{

    boost::shared_ptr<NodePolicy> nodePolicy = createNodePolicy(broker, type, name, properties);
    if (nodePolicy) {
        nodePolicy->setPersistenceId(persistenceId);
        return true;
    } else {
        return false;
    }
}

void NodePolicyRegistry::add(boost::shared_ptr<NodePolicy> nodePolicy)
{
    qpid::sys::Mutex::ScopedLock l(lock);
    NodePolicies::const_iterator i = nodePolicies.find(nodePolicy->getName());
    if (i == nodePolicies.end()) {
        nodePolicies.insert(NodePolicies::value_type(nodePolicy->getName(), nodePolicy));
    } else {
        if (i->second->getType() != nodePolicy->getType()) {
            throw qpid::types::Exception(QPID_MSG("Cannot create object of type " << nodePolicy->getType() << " with key "
                                                  << nodePolicy->getName() << " as an object of type " << i->second->getType() << " already exists with the same key"));
        } else {
            throw qpid::types::Exception(QPID_MSG("An object of type " << nodePolicy->getType() << " with key " << nodePolicy->getName() << " already exists"));
        }
    }
}
boost::shared_ptr<NodePolicy> NodePolicyRegistry::remove(const std::string& pattern, const std::string& type)
{
    boost::shared_ptr<NodePolicy> result;
    qpid::sys::Mutex::ScopedLock l(lock);
    NodePolicies::iterator i = nodePolicies.find(pattern);
    if (i != nodePolicies.end()) {
        if (i->second->getType() != type) {
            throw qpid::types::Exception(QPID_MSG("Object with key " << i->first << " is of type " << i->second->getType() << " not " << type));
        }
        result = i->second;
        nodePolicies.erase(i);
    }
    return result;
}
boost::shared_ptr<NodePolicy> NodePolicyRegistry::get(const std::string& pattern)
{
    qpid::sys::Mutex::ScopedLock l(lock);
    NodePolicies::const_iterator i = nodePolicies.find(pattern);
    if (i == nodePolicies.end()) {
        return boost::shared_ptr<NodePolicy>();
    } else {
        return i->second;
    }
}

boost::shared_ptr<NodePolicy> NodePolicyRegistry::match(const std::string& name)
{
    qpid::sys::Mutex::ScopedLock l(lock);
    boost::shared_ptr<NodePolicy> best;
    for (NodePolicies::const_iterator i = nodePolicies.begin(); i != nodePolicies.end(); ++i) {
        //where multiple policies match, pick the one with the longest
        //pattern as a crude guesstimate of the more specific one
        if (i->second->match(name) && (!best || i->first.size() > best->getPattern().size())) {
            best = i->second;
        }
    }
    return best;
}

}}} // namespace qpid::broker::amqp
