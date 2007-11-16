#ifndef _P2PMessageSizeTest_
#define _P2PMessageSizeTest_
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

#include <memory>
#include <sstream>

#include <ClientChannel.h>
#include <ClientMessage.h>
#include <Connection.h>
#include <Exception.h>
#include <MessageListener.h>
#include "SimpleTestCaseBase.h"

namespace qpid {

/**
 * P2PMessageSizeTest implements test case 4, P2P messages with message size. Sends/received a specified number of messages to a 
 * specified route on the default exchange, of a specified size. Produces reports on the actual number of messages sent/received.
 */    
class P2PMessageSizeTest : public SimpleTestCaseBase
{
    class Receiver;

public:

    /**
     * Assigns the role to be played by this test case. The test parameters are fully specified in the
     * assignment messages filed table.
     *
     * @param role              The role to be played; sender or receiver.
     * @param assignRoleMessage The role assingment messages field table, contains the full test parameters.
     * @param options           Additional test options.
     */
    void assign(const std::string& role, framing::FieldTable& params, TestOptions& options);
};
}

#endif
