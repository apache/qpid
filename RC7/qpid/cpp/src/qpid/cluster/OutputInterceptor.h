#ifndef QPID_CLUSTER_OUTPUTINTERCEPTOR_H
#define QPID_CLUSTER_OUTPUTINTERCEPTOR_H

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

#include "WriteEstimate.h"
#include "qpid/sys/ConnectionOutputHandler.h"
#include "qpid/broker/ConnectionFactory.h"
#include <boost/function.hpp>

namespace qpid {
namespace framing { class AMQFrame; }
namespace cluster {

class Connection;

/**
 * Interceptor for connection OutputHandler, manages outgoing message replication.
 */
class OutputInterceptor : public sys::ConnectionOutputHandler {
  public:
    OutputInterceptor(cluster::Connection& p, sys::ConnectionOutputHandler& h);

    // sys::ConnectionOutputHandler functions
    void send(framing::AMQFrame& f);
    void activateOutput();
    void giveReadCredit(int32_t);
    void close();
    size_t getBuffered() const;

    // Delivery point for doOutput requests.
    void deliverDoOutput(size_t requested);
    // Intercept doOutput requests on Connection.
    bool doOutput();

    void setOutputHandler(sys::ConnectionOutputHandler& h);

    cluster::Connection& parent;
    
  private:
    typedef sys::Mutex::ScopedLock Locker;

    void sendDoOutput();

    mutable sys::Mutex lock;
    sys::ConnectionOutputHandler* next;
    size_t sent;
    size_t lastDoOutput;
    WriteEstimate writeEstimate;
    bool moreOutput;
    bool doingOutput;
};

}} // namespace qpid::cluster

#endif  /*!QPID_CLUSTER_OUTPUTINTERCEPTOR_H*/
