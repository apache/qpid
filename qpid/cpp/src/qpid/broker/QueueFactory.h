#ifndef QPID_BROKER_QUEUEFACTORY_H
#define QPID_BROKER_QUEUEFACTORY_H

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
#include <boost/shared_ptr.hpp>

#define DEFAULT_MAX_PAGES 4
#define DEFAULT_PAGE_FACTOR 1

namespace qpid {
namespace management {
class Manageable;
}
namespace broker {
class Broker;
class MessageStore;
class Queue;
struct QueueSettings;

/**
 * Handles the creation and configuration of a Queue instance in order
 * to meet the required settings
 */
class QueueFactory
{
  public:
    QPID_BROKER_EXTERN QueueFactory();

    QPID_BROKER_EXTERN boost::shared_ptr<Queue> create(const std::string& name, const QueueSettings& settings);

    void setBroker(Broker*);
    Broker* getBroker();

    /**
     * Set the store to use.  May only be called once.
     */
    void setStore (MessageStore*);

    /**
     * Return the message store used.
     */
    MessageStore* getStore() const;

    /**
     * Register the manageable parent for declared queues
     */
    void setParent(management::Manageable*);
  private:
    Broker* broker;
    MessageStore* store;
    management::Manageable* parent;
};
}} // namespace qpid::broker

#endif  /*!QPID_BROKER_QUEUEFACTORY_H*/
