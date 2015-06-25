#ifndef QPID_BROKER_BROKEROBSERVERS_H
#define QPID_BROKER_BROKEROBSERVERS_H

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

#include "BrokerObserver.h"
#include "Observers.h"

namespace qpid {
namespace broker {

/**
 * Collection of BrokerObserver.
 */
class BrokerObservers : public Observers<BrokerObserver> {
  public:
    void queueCreate(const boost::shared_ptr<Queue>& q) {
        each(boost::bind(&BrokerObserver::queueCreate, _1, q));
    }
    void queueDestroy(const boost::shared_ptr<Queue>& q) {
        each(boost::bind(&BrokerObserver::queueDestroy, _1, q));
    }
    void exchangeCreate(const boost::shared_ptr<Exchange>& e) {
        each(boost::bind(&BrokerObserver::exchangeCreate, _1, e));
    }
    void exchangeDestroy(const boost::shared_ptr<Exchange>& e) {
        each(boost::bind(&BrokerObserver::exchangeDestroy, _1, e));
    }
    void bind(const boost::shared_ptr<Exchange>& exchange,
              const boost::shared_ptr<Queue>& queue,
              const std::string& key,
              const framing::FieldTable& args) {
        each(boost::bind(&BrokerObserver::bind, _1, exchange, queue, key, args));
    }
    void unbind(const boost::shared_ptr<Exchange>& exchange,
                const boost::shared_ptr<Queue>& queue,
                const std::string& key,
                const framing::FieldTable& args) {
        each(boost::bind(&BrokerObserver::unbind, _1, exchange, queue, key, args));
    }
    void startTx(const boost::intrusive_ptr<TxBuffer>& tx) {
        each(boost::bind(&BrokerObserver::startTx, _1, tx));
    }
    void startDtx(const boost::intrusive_ptr<DtxBuffer>& dtx) {
        each(boost::bind(&BrokerObserver::startDtx, _1, dtx));
    }

  private:
    template <class F> void each(F f) { Observers<BrokerObserver>::each(f); }
};

}} // namespace qpid::broker

#endif  /*!QPID_BROKER_BROKEROBSERVERS_H*/
