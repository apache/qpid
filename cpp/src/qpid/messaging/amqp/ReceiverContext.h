#ifndef QPID_MESSAGING_AMQP_RECEIVERCONTEXT_H
#define QPID_MESSAGING_AMQP_RECEIVERCONTEXT_H

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
#include "qpid/messaging/Address.h"
#include "qpid/messaging/amqp/AddressHelper.h"
#include <string>
#include "qpid/sys/AtomicCount.h"
#include "qpid/sys/IntegerTypes.h"

struct pn_link_t;
struct pn_session_t;
struct pn_terminus_t;

namespace qpid {
namespace messaging {

class Duration;
class Message;

namespace amqp {

/**
 *
 */
class ReceiverContext
{
  public:
    ReceiverContext(pn_session_t* session, const std::string& name, const qpid::messaging::Address& source);
    ~ReceiverContext();
    void reset(pn_session_t* session);
    void setCapacity(uint32_t);
    uint32_t getCapacity();
    uint32_t getAvailable();
    uint32_t getUnsettled();
    void attach();
    void close();
    const std::string& getName() const;
    const std::string& getSource() const;
    void configure();
    void verify();
    Address getAddress() const;
    bool hasCurrent();
    bool getBrowse() const { return helper.getBrowse(); }

  private:
    friend class ConnectionContext;
    const std::string name;
    Address address;
    AddressHelper helper;
    pn_link_t* receiver;
    uint32_t capacity;
    uint32_t used;
    qpid::sys::AtomicCount fetching;
    void configure(pn_terminus_t*);
    bool wakeupToIssueCredit();
};
}}} // namespace qpid::messaging::amqp

#endif  /*!QPID_MESSAGING_AMQP_RECEIVERCONTEXT_H*/
