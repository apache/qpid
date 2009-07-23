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
package org.apache.qpid.server.logging.messages;

import java.util.List;

public class QueueMessagesTest extends AbstractTestMessages
{
    public void testMessage1001()
    {
        String owner = "guest";

        _logMessage = QueueMessages.QUE_1001(owner);
        List<Object> log = performLog();

        String[] expected = {"Create :", "Owner:", owner};

        validateLogMessage(log, "QUE-1001", expected);
    }

    public void testMessage1002()
    {
        _logMessage = QueueMessages.QUE_1002();
        List<Object> log = performLog();

        String[] expected = {"Deleted"};

        validateLogMessage(log, "QUE-1002", expected);
    }

}
