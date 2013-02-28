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
#include "qpid/messaging/amqp/Transport.h"
#include "qpid/messaging/amqp/TransportContext.h"
#include <map>
#include <string>

namespace qpid {
namespace messaging {
namespace amqp {
namespace {
typedef std::map<std::string, Transport::Factory*> Registry;

Registry& theRegistry()
{
    static Registry factories;
    return factories;
}
}

Transport* Transport::create(const std::string& name, TransportContext& context, boost::shared_ptr<qpid::sys::Poller> poller)
{
    Registry::const_iterator i = theRegistry().find(name);
    if (i != theRegistry().end()) return (i->second)(context, poller);
    else return 0;
}
void Transport::add(const std::string& name, Factory* factory)
{
    theRegistry()[name] = factory;
}

}}} // namespace qpid::messaging::amqp
