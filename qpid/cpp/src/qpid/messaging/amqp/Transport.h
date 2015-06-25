#ifndef QPID_MESSAGING_AMQP_TRANSPORT_H
#define QPID_MESSAGING_AMQP_TRANSPORT_H

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
#include "qpid/CommonImportExport.h"
#include "qpid/sys/OutputControl.h"
#include <boost/shared_ptr.hpp>

namespace qpid {
namespace sys {
class Poller;
struct SecuritySettings;
}
namespace messaging {
namespace amqp {
class TransportContext;

class Transport : public qpid::sys::OutputControl
{
  public:
    virtual ~Transport() {}
    virtual void connect(const std::string& host, const std::string& port) = 0;
    virtual void close() = 0;
    virtual void abort() = 0;
    virtual const qpid::sys::SecuritySettings* getSecuritySettings() = 0;

    typedef Transport* Factory(TransportContext&, boost::shared_ptr<qpid::sys::Poller>);
    QPID_COMMON_EXTERN static Transport* create(const std::string& name, TransportContext&, boost::shared_ptr<qpid::sys::Poller>);
    QPID_COMMON_EXTERN static void add(const std::string& name, Factory* factory);
};
}}} // namespace qpid::messaging::amqp

#endif  /*!QPID_MESSAGING_AMQP_TRANSPORT_H*/
