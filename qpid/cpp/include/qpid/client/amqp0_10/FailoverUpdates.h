#ifndef QPID_CLIENT_AMQP0_10_FAILOVERUPDATES_H
#define QPID_CLIENT_AMQP0_10_FAILOVERUPDATES_H

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
#include "qpid/client/ClientImportExport.h"

namespace qpid {

namespace messaging {
class Connection;
}

namespace client {
namespace amqp0_10 {

struct FailoverUpdatesImpl;
/**
 * A utility to listen for updates on cluster membership - published
 * via the amq.failover exchange - and update the list of known urls
 * for a connection accordingly.
 */
class FailoverUpdates
{
  public:
    QPID_CLIENT_EXTERN FailoverUpdates(qpid::messaging::Connection& connection);
    QPID_CLIENT_EXTERN ~FailoverUpdates();
  private:
    FailoverUpdatesImpl* impl;

    //no need to copy instances of this class
    FailoverUpdates(const FailoverUpdates&);
    FailoverUpdates& operator=(const FailoverUpdates&);
};
}}} // namespace qpid::client::amqp0_10

#endif  /*!QPID_CLIENT_AMQP0_10_FAILOVERUPDATES_H*/
