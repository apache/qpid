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
#include "qpid/messaging/Connection.h"
#include "qpid/messaging/Receiver.h"
#include "qpid/messaging/Sender.h"
#include "qpid/messaging/Session.h"

namespace qpid {
namespace messaging {

using namespace qpid::types;

void HandleInstantiatorDoNotCall(void)
{
    // This function exists to instantiate various template Handle
    //  bool functions. The instances are then available to  
    //  the qpidmessaging DLL and subsequently exported.
    // This function must not be exported nor called called.
    // For further information refer to 
    //  https://issues.apache.org/jira/browse/QPID-2926

    Connection connection;
    if (connection.isValid()) connection.close();
    if (connection.isNull() ) connection.close();
    if (connection          ) connection.close();
    if (!connection         ) connection.close();

    Receiver receiver;
    if (receiver.isValid()) receiver.close();
    if (receiver.isNull() ) receiver.close();
    if (receiver          ) receiver.close();
    if (!receiver         ) receiver.close();

    Sender sender;
    if (sender.isValid()) sender.close();
    if (sender.isNull() ) sender.close();
    if (sender          ) sender.close();
    if (!sender         ) sender.close();

    Session session;
    if (session.isValid()) session.close();
    if (session.isNull() ) session.close();
    if (session          ) session.close();
    if (!session         ) session.close();
}
}} // namespace qpid::messaging
