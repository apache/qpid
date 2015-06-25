#ifndef COORDINATORCONTEXT_H
#define COORDINATORCONTEXT_H
/*
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
 */


#include "SenderContext.h"
#include <qpid/types/Variant.h>
#include "qpid/sys/ExceptionHolder.h"
#include <boost/enable_shared_from_this.hpp>
#include <boost/function.hpp>

struct pn_session_t;

namespace qpid {
namespace messaging {
namespace amqp {

class SessionContext;
class ConnectionContext;

/**
 * Track the current transaction for a session.
 *
 * Implements SenderContext, to send transaction command messages to remote coordinator.
 */
class Transaction : public SenderContext, public boost::enable_shared_from_this<Transaction> {
  public:
    typedef boost::shared_ptr<SessionContext> SessionPtr;

    typedef boost::function<void (boost::shared_ptr<SessionContext> ssn,
                                  boost::shared_ptr<SenderContext> snd,
                                  const qpid::messaging::Message& message,
                                  bool sync,
                                  SenderContext::Delivery** delivery)> SendFunction;

    Transaction(pn_session_t*);

    sys::ExceptionHolder error;

    /** Declare a transaction using connection and session to send to remote co-ordinator. */
    void declare(SendFunction, const SessionPtr& session);

    /** Discharge a transaction using connection and session to send to remote co-ordinator.
     *@param fail: true means rollback, false means commit.
     */
    void discharge(SendFunction, const SessionPtr& session, bool fail);

    /** Update a delivery with a transactional accept state. */
    void acknowledge(pn_delivery_t* delivery);

    /** Get delivery state to attach to transfers sent in a transaction. */
    types::Variant getSendState() const;

    /** Override SenderContext::getTarget with a more readable value */
    const std::string& getTarget() const;

    bool isCommitting() const { return committing; }

  protected:
    // SenderContext overrides
    void configure();
    void verify();

  private:
    std::string id;
    types::Variant sendState;
    types::Variant acceptState;
    bool committing;


    void clear();
    void setId(const SenderContext::Delivery& delivery);
    void setId(const std::string& id);
};

}}}

#endif
