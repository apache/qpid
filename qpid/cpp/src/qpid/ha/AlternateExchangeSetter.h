#ifndef QPID_HA_ALTERNATEEXCHANGESETTER_H
#define QPID_HA_ALTERNATEEXCHANGESETTER_H

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

#include "qpid/log/Statement.h"
#include "qpid/broker/Exchange.h"
#include "qpid/broker/ExchangeRegistry.h"
#include <map>

namespace qpid {
namespace ha {

/**
 * Sets the alternate exchange on queues and exchanges.
 * Holds onto queues/exchanges if necessary till the alternate exchange is available.
 * THREAD UNSAFE
 */
class AlternateExchangeSetter
{
  public:
    typedef boost::function<void(boost::shared_ptr<broker::Exchange>)> SetFunction;

    AlternateExchangeSetter(broker::ExchangeRegistry& er) : exchanges(er) {}

    /** If altEx is already known, call setter(altEx) now else save for later */
    void setAlternate(const std::string& altEx, const SetFunction& setter) {
        boost::shared_ptr<broker::Exchange> ex = exchanges.find(altEx);
        if (ex) setter(ex);     // Set immediately.
        else setters.insert(Setters::value_type(altEx, setter)); // Save for later.
    }

    /** Add an exchange and call any setters that are waiting for it. */
    void addExchange(boost::shared_ptr<broker::Exchange> exchange) {
        // Update the setters for this exchange
        std::pair<Setters::iterator, Setters::iterator> range = setters.equal_range(exchange->getName());
        for (Setters::iterator i = range.first; i != range.second; ++i) 
            i->second(exchange);
        setters.erase(range.first, range.second);
    }

    void clear() {
        if (!setters.empty())
            QPID_LOG(warning, "Some alternate exchanges were not resolved.");
        setters.clear();
    }

  private:
    typedef std::multimap<std::string, SetFunction> Setters;
    broker::ExchangeRegistry& exchanges;
    Setters setters;
};
}} // namespace qpid::ha

#endif  /*!QPID_HA_ALTERNATEEXCHANGESETTER_H*/
