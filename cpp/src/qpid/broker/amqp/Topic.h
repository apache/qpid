#ifndef QPID_BROKER_AMQP_TOPIC_H
#define QPID_BROKER_AMQP_TOPIC_H

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
#include "qpid/types/Variant.h"
#include "qpid/management/Manageable.h"
#include "qmf/org/apache/qpid/broker/Exchange.h"
#include "qmf/org/apache/qpid/broker/Topic.h"
#include <boost/shared_ptr.hpp>

namespace qpid {
namespace broker {
class Broker;
class Exchange;
class QueueDepth;

namespace amqp {

/**
 * A topic is a node supporting a pub-sub style. It is at present
 * implemented by an exchange with an additional policy for handling
 * subscription queues.
 */
class Topic : public PersistableObject, public management::Manageable
{
  public:
    Topic(Broker&, const std::string& name, boost::shared_ptr<Exchange>, const qpid::types::Variant::Map& properties);
    ~Topic();
    const std::string& getName() const;
    const QueueSettings& getPolicy() const;
    const std::string& getAlternateExchange() const;
    boost::shared_ptr<Exchange> getExchange();
    bool isDurable() const;
    boost::shared_ptr<qpid::management::ManagementObject> GetManagementObject() const;
  private:
    std::string name;
    bool durable;
    boost::shared_ptr<Exchange> exchange;
    QueueSettings policy;
    std::string alternateExchange;
    qmf::org::apache::qpid::broker::Topic::shared_ptr topic;
};

class TopicRegistry : public ObjectFactory
{
  public:
    bool createObject(Broker&, const std::string& type, const std::string& name, const qpid::types::Variant::Map& properties,
                              const std::string& userId, const std::string& connectionId);
    bool deleteObject(Broker&, const std::string& type, const std::string& name, const qpid::types::Variant::Map& properties,
                              const std::string& userId, const std::string& connectionId);
    bool recoverObject(Broker&, const std::string& type, const std::string& name, const qpid::types::Variant::Map& properties,
                       uint64_t persistenceId);

    bool add(boost::shared_ptr<Topic> topic);
    boost::shared_ptr<Topic> remove(const std::string& name);
    boost::shared_ptr<Topic> get(const std::string& name);
    boost::shared_ptr<Topic> createTopic(Broker&, const std::string& name, boost::shared_ptr<Exchange> exchange, const qpid::types::Variant::Map& properties);
    boost::shared_ptr<Topic> declare(Broker&, const std::string& name, boost::shared_ptr<Exchange> exchange, const qpid::types::Variant::Map& properties);
  private:
    typedef std::map<std::string, boost::shared_ptr<Topic> > Topics;
    qpid::sys::Mutex lock;
    Topics topics;

};

}}} // namespace qpid::broker::amqp

#endif  /*!QPID_BROKER_AMQP_TOPIC_H*/
