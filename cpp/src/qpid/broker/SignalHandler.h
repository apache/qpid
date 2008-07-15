#ifndef QPID_BROKER_SIGNALHANDLER_H
#define QPID_BROKER_SIGNALHANDLER_H

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

/**
 * Handle signals e.g. to shut-down a broker.
 */
class SignalHandler
{
  public:
    /** Set the broker to be shutdown on signals */
    static void setBroker(const boost::intrusive_ptr<Broker>& broker);

  private:
    static void shutdownHandler(int);
    static boost::intrusive_ptr<Broker> broker;
};
}} // namespace qpid::broker

#endif  /*!QPID_BROKER_SIGNALHANDLER_H*/
