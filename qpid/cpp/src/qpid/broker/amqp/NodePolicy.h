#ifndef QPID_BROKER_AMQP_NODEPOLICY_H
#define QPID_BROKER_AMQP_NODEPOLICY_H

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
#include "qpid/broker/ObjectFactory.h"
#include "qpid/broker/PersistableObject.h"
#include "qpid/broker/QueueSettings.h"
#include "qpid/sys/Mutex.h"
#include "qpid/sys/regex.h"
#include "qpid/types/Variant.h"
#include "qpid/management/Manageable.h"
#include "qmf/org/apache/qpid/broker/QueuePolicy.h"
#include "qmf/org/apache/qpid/broker/TopicPolicy.h"
#include <boost/shared_ptr.hpp>

namespace qpid {
namespace broker {
class Broker;
class Queue;
namespace amqp {
class Connection;
class Topic;

/**
 * Policy for creation of nodes 'on-demand'
 */
class NodePolicy : public PersistableObject, public management::Manageable
{
  public:
    NodePolicy(const std::string& type, const std::string& ptrn, const qpid::types::Variant::Map& props);
    virtual ~NodePolicy();
    const std::string& getPattern() const;
    bool match(const std::string&) const;
    bool isDurable() const;
    virtual std::pair<boost::shared_ptr<Queue>, boost::shared_ptr<Topic> > create(const std::string&, Connection&) = 0;
    virtual boost::shared_ptr<qpid::management::ManagementObject> GetManagementObject() const = 0;
  protected:
    NodePolicy(Broker&, const std::string& type, const std::string& pattern, const qpid::types::Variant::Map& properties);
    const std::string pattern;
    bool durable;
    std::string alternateExchange;
    qpid::sys::regex compiled;
};

class QueuePolicy : public NodePolicy
{
  public:
    QueuePolicy(Broker&, const std::string& pattern, const qpid::types::Variant::Map& properties);
    ~QueuePolicy();
    std::pair<boost::shared_ptr<Queue>, boost::shared_ptr<Topic> > create(const std::string&, Connection&);
    boost::shared_ptr<qpid::management::ManagementObject> GetManagementObject() const;
  private:
    qpid::broker::QueueSettings queueSettings;
    qmf::org::apache::qpid::broker::QueuePolicy::shared_ptr policy;
};

class TopicPolicy : public NodePolicy
{
  public:
    TopicPolicy(Broker&, const std::string& pattern, const qpid::types::Variant::Map& properties);
    ~TopicPolicy();
    std::pair<boost::shared_ptr<Queue>, boost::shared_ptr<Topic> > create(const std::string&, Connection&);
    boost::shared_ptr<qpid::management::ManagementObject> GetManagementObject() const;
  private:
    qpid::types::Variant::Map topicSettings;
    std::string exchangeType;
    bool autodelete;
    qpid::types::Variant::Map exchangeSettings;
    qmf::org::apache::qpid::broker::TopicPolicy::shared_ptr policy;
};

class NodePolicyRegistry : public ObjectFactory
{
  public:
    bool createObject(Broker&, const std::string& type, const std::string& name, const qpid::types::Variant::Map& properties,
                              const std::string& userId, const std::string& connectionId);
    bool deleteObject(Broker&, const std::string& type, const std::string& name, const qpid::types::Variant::Map& properties,
                              const std::string& userId, const std::string& connectionId);
    bool recoverObject(Broker&, const std::string& type, const std::string& name, const qpid::types::Variant::Map& properties,
                       uint64_t persistenceId);

    boost::shared_ptr<NodePolicy> match(const std::string& name);
    boost::shared_ptr<NodePolicy> createQueuePolicy(Broker&, const std::string& name, const qpid::types::Variant::Map& properties);
    boost::shared_ptr<NodePolicy> createTopicPolicy(Broker&, const std::string& name, const qpid::types::Variant::Map& properties);
  private:
    typedef std::map<std::string, boost::shared_ptr<NodePolicy> > NodePolicies;
    qpid::sys::Mutex lock;
    NodePolicies nodePolicies;

    boost::shared_ptr<NodePolicy> createNodePolicy(Broker&, const std::string& type, const std::string& name, const qpid::types::Variant::Map& properties);
    void add(boost::shared_ptr<NodePolicy> nodePolicy);
    boost::shared_ptr<NodePolicy> remove(const std::string& pattern, const std::string& type);
    boost::shared_ptr<NodePolicy> get(const std::string& pattern);
};

}}} // namespace qpid::broker::amqp

#endif  /*!QPID_BROKER_AMQP_NODEPOLICY_H*/
