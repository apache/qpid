#ifndef _ManagementAgent_
#define _ManagementAgent_

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

#include "qpid/Options.h"
#include "qpid/broker/Exchange.h"
#include "qpid/broker/Timer.h"
#include "ManagementObject.h"
#include <boost/shared_ptr.hpp>

namespace qpid { 
namespace management {

class ManagementAgent
{
  private:

    ManagementAgent (uint16_t interval);

  public:

    typedef boost::shared_ptr<ManagementAgent> shared_ptr;

    static shared_ptr getAgent (void);

    void setInterval     (uint16_t _interval) { interval = _interval; }
    void setExchange     (broker::Exchange::shared_ptr  mgmtExchange,
                          broker::Exchange::shared_ptr  directExchange);
    void addObject       (ManagementObject::shared_ptr object);
    void clientAdded     (void);
    void dispatchCommand (broker::Deliverable&             msg,
                          const std::string&               routingKey,
                          const qpid::framing::FieldTable* args);
    
  private:

    struct Periodic : public broker::TimerTask
    {
        ManagementAgent& agent;

        Periodic (ManagementAgent& agent, uint32_t seconds);
        ~Periodic () {}
        void fire ();
    };

    static shared_ptr            agent;
    ManagementObjectMap          managementObjects;
    broker::Timer                timer;
    broker::Exchange::shared_ptr mExchange;
    broker::Exchange::shared_ptr dExchange;
    uint16_t                     interval;
    uint64_t                     nextObjectId;

    void PeriodicProcessing (void);
};

}}
            


#endif  /*!_ManagementAgent_*/
