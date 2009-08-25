#ifndef QPID_CLIENT_AMQP0_10_CONNECTIONIMPL_H
#define QPID_CLIENT_AMQP0_10_CONNECTIONIMPL_H

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
#include "qpid/messaging/ConnectionImpl.h"
#include "qpid/messaging/Variant.h"
#include "qpid/client/Connection.h"

namespace qpid {
namespace client {
namespace amqp0_10 {

class ConnectionImpl : public qpid::messaging::ConnectionImpl
{
  public:
    ConnectionImpl(const std::string& url, const qpid::messaging::Variant::Map& options);
    void close();
    qpid::messaging::Session newSession();
  private:
    qpid::client::Connection connection;
};
}}} // namespace qpid::client::amqp0_10

#endif  /*!QPID_CLIENT_AMQP0_10_CONNECTIONIMPL_H*/
