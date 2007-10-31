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
#include "Exchange.h"
#include "ManagementObject.h"
#include "Timer.h"
#include <boost/shared_ptr.hpp>

namespace qpid { 
namespace broker {


class ManagementAgent
{
  public:

    typedef boost::shared_ptr<ManagementAgent> shared_ptr;

    ManagementAgent (uint16_t interval);

    void setExchange     (Exchange::shared_ptr         exchange);
    void addObject       (ManagementObject::shared_ptr object);
    void clientAdded     (void);
    void dispatchCommand (Deliverable&      msg,
                          const string&     routingKey,
                          const FieldTable* args);
    
  private:

    struct Periodic : public TimerTask
    {
        ManagementAgent& agent;

        Periodic (ManagementAgent& agent, uint32_t seconds);
        ~Periodic () {}
        void fire ();
    };

    ManagementObjectVector managementObjects;
    Timer                  timer;
    Exchange::shared_ptr   exchange;
    uint16_t               interval;

    void PeriodicProcessing (void);
};

}}
            


#endif  /*!_ManagementAgent_*/
