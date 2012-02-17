#ifndef QPID_HA_CONNECTIONEXCLUDER_H
#define QPID_HA_CONNECTIONEXCLUDER_H

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

#include "qpid/broker/ConnectionObserver.h"
#include <boost/function.hpp>

namespace qpid {

namespace broker {
class Connection;
}

namespace ha {

/**
 * Exclude normal connections to a backup broker.
 * Admin connections are identified by a special flag in client-properties
 * during connection negotiation.
 */
class ConnectionExcluder : public broker::ConnectionObserver
{
  public:
    typedef boost::function<bool()> PrimaryTest;

    ConnectionExcluder(PrimaryTest isPrimary_);

    void opened(broker::Connection& connection);

  private:
    static const std::string ADMIN_TAG;
    PrimaryTest isPrimary;
};

}} // namespace qpid::ha

#endif  /*!QPID_HA_CONNECTIONEXCLUDER_H*/
