#ifndef QPID_BROKER_AMQP_TRANSLATION_H
#define QPID_BROKER_AMQP_TRANSLATION_H

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
#include <boost/intrusive_ptr.hpp>

namespace qpid {
namespace broker {
class Broker;
class Message;
namespace amqp_0_10 {
class MessageTransfer;
}
namespace amqp {

class OutgoingFromQueue;
/**
 *
 */
class Translation
{
  public:
    Translation(const qpid::broker::Message& message, Broker* broker = 0);

    /**
     * @returns a pointer to an AMQP 0-10 message transfer suitable
     * for sending on an 0-10 session, translating from 1.0 as
     * necessary
     */
    boost::intrusive_ptr<const qpid::broker::amqp_0_10::MessageTransfer> getTransfer();
    /**
     * Writes the AMQP 1.0 bare message and any annotations, translating from 0-10 if necessary
     */
    void write(OutgoingFromQueue&);
  private:
    const qpid::broker::Message& original;
    Broker* broker;
};
}}} // namespace qpid::broker::amqp

#endif  /*!QPID_BROKER_AMQP_TRANSLATION_H*/
