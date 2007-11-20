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
#include "TestUtils.h"
#include <string>

using namespace qpid;

/**
 * Creates a test message of the specified size. The message is filled with dummy (non-zero) data.
 *
 * @param size The size of the message to create.
 */
void qpid::createTestMessageOfSize(qpid::client::Message& message, int size)
{
    std::string MESSAGE_DATA("-- Test Message -- Test Message -- Test Message -- Test Message -- Test Message -- Test Message -- Test Message ");
    std::string data;

    if (size > 0)
    {      
        int div = MESSAGE_DATA.length() / size;
        int mod = MESSAGE_DATA.length() % size;
      
        for (int i = 0; i < div; i++)
        {
            data += MESSAGE_DATA;
        }
      
        if (mod != 0)
        {
            data += MESSAGE_DATA.substr(0, mod);
        }
    }

    message.setData(data);
}
