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
#ifndef _ManagementDirectExchange_
#define _ManagementDirectExchange_

#include "qpid/broker/DirectExchange.h"
#include "qpid/management/ManagementAgent.h"

namespace qpid {
namespace broker {

class ManagementDirectExchange : public virtual DirectExchange
{
  private:
    management::ManagementAgent* managementAgent;
    int qmfVersion;
 
  public:
    static const std::string typeName;

    ManagementDirectExchange(const std::string& name, Manageable* _parent = 0, Broker* broker = 0);
    ManagementDirectExchange(const std::string& _name, bool _durable, 
                             const qpid::framing::FieldTable& _args,
                             Manageable* _parent = 0, Broker* broker = 0);

    virtual std::string getType() const { return typeName; }

    virtual void route(Deliverable& msg);

    void setManagmentAgent(management::ManagementAgent* agent, int qmfVersion);

    virtual ~ManagementDirectExchange();
};


}
}

#endif
