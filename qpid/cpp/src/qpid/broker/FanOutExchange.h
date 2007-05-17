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
#ifndef _FanOutExchange_
#define _FanOutExchange_

#include <map>
#include <vector>
#include "BrokerExchange.h"
#include "qpid/framing/FieldTable.h"
#include "BrokerMessage.h"
#include "qpid/sys/Monitor.h"
#include "BrokerQueue.h"

namespace qpid {
namespace broker {

class FanOutExchange : public virtual Exchange {
    std::vector<Queue::shared_ptr> bindings;
    qpid::sys::Mutex lock;

  public:
    static const std::string typeName;
        
    FanOutExchange(const std::string& name);
    FanOutExchange(const string& _name, bool _durable, 
                   const qpid::framing::FieldTable& _args);

    virtual std::string getType() const { return typeName; }            
        
    virtual bool bind(Queue::shared_ptr queue, const std::string& routingKey, const qpid::framing::FieldTable* args);

    virtual bool unbind(Queue::shared_ptr queue, const std::string& routingKey, const qpid::framing::FieldTable* args);

    virtual void route(Deliverable& msg, const std::string& routingKey, const qpid::framing::FieldTable* args);

    virtual ~FanOutExchange();
};

}
}



#endif
