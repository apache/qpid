#ifndef _broker_BrokerSingleton_h
#define _broker_BrokerSingleton_h

/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#include "Broker.h"

namespace qpid {
namespace broker {

/**
 * BrokerSingleton is a smart pointer to a process-wide singleton broker
 * started on an os-chosen port. The broker starts the first time
 * an instance of BrokerSingleton is created and runs untill the process exits.
 *
 * Useful for unit tests that want to share a broker between multiple
 * tests to reduce overhead of starting/stopping a broker for every test.
 *
 * Tests that need a new broker can create it directly.
 *
 * THREAD UNSAFE.
 */
class BrokerSingleton : public boost::intrusive_ptr<Broker>
{
  public:
    BrokerSingleton();
    ~BrokerSingleton();
  private:
    static boost::intrusive_ptr<Broker> broker;
};

}} // namespace qpid::broker



#endif  /*!_broker_BrokerSingleton_h*/
