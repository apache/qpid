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
#include "ConnectionFactory.h"
#include "Connection.h"
#include "MultiVersionConnectionInputHandler.h"

namespace qpid {
namespace broker {


ConnectionFactory::ConnectionFactory(Broker& b) : broker(b)
{}


ConnectionFactory::~ConnectionFactory()
{

}

qpid::sys::ConnectionInputHandler*
ConnectionFactory::create(qpid::sys::ConnectionOutputHandler* out,
                          const std::string& id)
{
    return new MultiVersionConnectionInputHandler(out, broker, id);
}

}} // namespace qpid::broker
