#ifndef QPID_MESSAGING_AMQP_SENDERCONTEXT_H
#define QPID_MESSAGING_AMQP_SENDERCONTEXT_H

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
#include <deque>
#include <string>
#include <vector>
#include "qpid/sys/IntegerTypes.h"

struct pn_delivery_t;
struct pn_link_t;
struct pn_session_t;

namespace qpid {
namespace messaging {

class Message;

namespace amqp {

/**
 *
 */
class SenderContext
{
  public:
    class Delivery
    {
      public:
        Delivery(int32_t id, const qpid::messaging::Message& message);
        void send(pn_link_t*);
        bool accepted();
      private:
        int32_t id;
        std::vector<char> data;
        pn_delivery_t* token;
    };

    SenderContext(pn_session_t* session, const std::string& name, const std::string& target);
    ~SenderContext();
    void close();
    void setCapacity(uint32_t);
    uint32_t getCapacity();
    uint32_t getUnsettled();
    const std::string& getName() const;
    const std::wstring& getTarget() const;
    Delivery* send(const qpid::messaging::Message& message);
  private:
    friend class ConnectionContext;
    typedef std::deque<Delivery> Deliveries;

    const std::string name;
    const std::wstring target;
    pn_link_t* sender;
    int32_t nextId;
    Deliveries deliveries;
    uint32_t capacity;

    uint32_t processUnsettled();
};
}}} // namespace qpid::messaging::amqp

#endif  /*!QPID_MESSAGING_AMQP_SENDERCONTEXT_H*/
