#ifndef QPID_BROKER_CONFIGURATIONOBSERVERS_H
#define QPID_BROKER_CONFIGURATIONOBSERVERS_H

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

#include "ConfigurationObserver.h"
#include "Observers.h"
#include "qpid/sys/Mutex.h"

namespace qpid {
namespace broker {

/**
 * A configuration observer that delegates to a collection of
 * configuration observers.
 *
 * THREAD SAFE
 */
class ConfigurationObservers : public ConfigurationObserver,
                               public Observers<ConfigurationObserver>
{
  public:
    void queueCreate(const boost::shared_ptr<Queue>& q) {
        each(boost::bind(&ConfigurationObserver::queueCreate, _1, q));
    }
    void queueDestroy(const boost::shared_ptr<Queue>& q) {
        each(boost::bind(&ConfigurationObserver::queueDestroy, _1, q));
    }
    void exchangeCreate(const boost::shared_ptr<Exchange>& e) {
        each(boost::bind(&ConfigurationObserver::exchangeCreate, _1, e));
    }
    void exchangeDestroy(const boost::shared_ptr<Exchange>& e) {
        each(boost::bind(&ConfigurationObserver::exchangeDestroy, _1, e));
    }
    void bind(const boost::shared_ptr<Exchange>& exchange,
              const boost::shared_ptr<Queue>& queue,
              const std::string& key,
              const framing::FieldTable& args) {
        each(boost::bind(
                 &ConfigurationObserver::bind, _1, exchange, queue, key, args));
    }
    void unbind(const boost::shared_ptr<Exchange>& exchange,
                const boost::shared_ptr<Queue>& queue,
                const std::string& key,
                const framing::FieldTable& args) {
        each(boost::bind(
                 &ConfigurationObserver::unbind, _1, exchange, queue, key, args));
    }
};

}} // namespace qpid::broker

#endif  /*!QPID_BROKER_CONFIGURATIONOBSERVERS_H*/
