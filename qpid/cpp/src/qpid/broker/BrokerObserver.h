#ifndef QPID_BROKER_BROKEROBSERVER_H
#define QPID_BROKER_BROKEROBSERVER_H

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

#include <boost/shared_ptr.hpp>
#include <string>

namespace qpid {

namespace framing {
class FieldTable;
}

namespace broker {
class Queue;
class Exchange;
class TxBuffer;
class DtxBuffer;

/**
 * Observer for changes to configuration (aka wiring)
 *
 * NOTE: create and destroy functions are called with
 * the registry lock held. This is necessary to ensure
 * they are called in the correct sequence.
 */
class BrokerObserver
{
  public:
    virtual ~BrokerObserver() {}
    virtual void queueCreate(const boost::shared_ptr<Queue>&) {}
    virtual void queueDestroy(const boost::shared_ptr<Queue>&) {}
    virtual void exchangeCreate(const boost::shared_ptr<Exchange>&) {}
    virtual void exchangeDestroy(const boost::shared_ptr<Exchange>&) {}
    virtual void bind(const boost::shared_ptr<Exchange>& ,
                      const boost::shared_ptr<Queue>& ,
                      const std::string& /*key*/,
                      const framing::FieldTable& /*args*/) {}
    virtual void unbind(const boost::shared_ptr<Exchange>&,
                        const boost::shared_ptr<Queue>& ,
                        const std::string& /*key*/,
                        const framing::FieldTable& /*args*/) {}
    virtual void startTx(const boost::intrusive_ptr<TxBuffer>&) {}
    virtual void startDtx(const boost::intrusive_ptr<DtxBuffer>&) {}
};
}} // namespace qpid::broker

#endif  /*!QPID_BROKER_BROKEROBSERVER_H*/
