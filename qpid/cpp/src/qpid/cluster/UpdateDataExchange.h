#ifndef QPID_CLUSTER_UPDATEDATAEXCHANGE_H
#define QPID_CLUSTER_UPDATEDATAEXCHANGE_H

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

#include "qpid/broker/Exchange.h"
#include "types.h"
#include <iosfwd>

namespace qpid {

namespace management {
class ManagementAgent;
}

namespace cluster {
class Cluster;

/**
 * An exchange used to send data that is to large for a control
 * during update. The routing key indicates the type of data.
 */
class UpdateDataExchange : public broker::Exchange
{
  public:
    static const std::string EXCHANGE_NAME;
    static const std::string EXCHANGE_TYPE;
    static const std::string MANAGEMENT_AGENTS_KEY;
    static const std::string MANAGEMENT_SCHEMAS_KEY;
    static const std::string MANAGEMENT_DELETED_OBJECTS_KEY;

    UpdateDataExchange(Cluster& parent);

    void route(broker::Deliverable& msg, const std::string& routingKey,
               const framing::FieldTable* args);

    // Not implemented
    std::string getType() const { return EXCHANGE_TYPE; }

    bool bind(boost::shared_ptr<broker::Queue>,
              const std::string&,
              const qpid::framing::FieldTable*)
    { return false; }

    bool unbind(boost::shared_ptr<broker::Queue>,
                const std::string&,
                const qpid::framing::FieldTable*)
    { return false; }

    bool isBound(boost::shared_ptr<broker::Queue>,
                 const std::string*,
                 const qpid::framing::FieldTable*)
    { return false; }

    void updateManagementAgent(management::ManagementAgent* agent);

  private:
    MemberId clusterId;
    std::string managementAgents;
    std::string managementSchemas;
    std::string managementDeletedObjects;
  friend std::ostream& operator<<(std::ostream&, const UpdateDataExchange&);
};

}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_UPDATEDATAEXCHANGE_H*/
