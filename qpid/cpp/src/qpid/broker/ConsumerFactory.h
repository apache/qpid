#ifndef QPID_BROKER_CONSUMERFACTORY_H
#define QPID_BROKER_CONSUMERFACTORY_H

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

// TODO aconway 2011-11-25: it's ugly exposing SemanticState::ConsumerImpl in public.
// Refactor to use a more abstract interface.

#include <boost/shared_ptr.hpp>

namespace qpid {
namespace broker {

class SemanticState;
class SemanticStateConsumerImpl;

/**
 * Base class for consumer factoires. Plugins can register a
 * ConsumerFactory via Broker:: getConsumerFactories() Each time a
 * conumer is created, each factory is tried in turn till one returns
 * non-0.
 */
class ConsumerFactory
{
  public:
    virtual ~ConsumerFactory() {}

    virtual boost::shared_ptr<SemanticStateConsumerImpl> create(
        SemanticState* parent,
        const std::string& name, boost::shared_ptr<Queue> queue,
        bool ack, bool acquire, bool exclusive, const std::string& tag,
        const std::string& resumeId, uint64_t resumeTtl, const framing::FieldTable& arguments) = 0;
};

/** A set of factories held by the broker
 * THREAD UNSAFE: see notes on member functions.
 */
class ConsumerFactories {
  public:
    typedef std::vector<boost::shared_ptr<ConsumerFactory> > Factories;

    /** Thread safety: May only be called during plug-in initialization. */
    void add(const boost::shared_ptr<ConsumerFactory>& cf) { factories.push_back(cf); }

    /** Thread safety: May only be called after plug-in initialization. */
    const Factories& get() const { return factories; }

  private:
    Factories factories;
};

}} // namespace qpid::broker

#endif  /*!QPID_BROKER_CONSUMERFACTORY_H*/
