/*
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
 */

#include <qpid/messaging/Connection.h>
#include <qpid/messaging/Duration.h>

#define QMF_USE_DEPRECATED_API
#include <qmf/ConsoleSession.h>
#include <qmf/ConsoleEvent.h>
#include <qmf/Data.h>
#include <qpid/types/Variant.h>
#include <string>
#include <iostream>

using namespace std;
using namespace qmf;
using qpid::types::Variant;
using qpid::messaging::Duration;

int main(int argc, char** argv)
{
    string url("localhost");
    string connectionOptions;
    string sessionOptions;

    if (argc > 1)
        url = argv[1];
    if (argc > 2)
        connectionOptions = argv[2];
    if (argc > 3)
        sessionOptions = argv[3];

    qpid::messaging::Connection connection(url, connectionOptions);
    connection.open();

    ConsoleSession session(connection, sessionOptions);
    session.open();

    while (true) {
        ConsoleEvent event;
        if (session.nextEvent(event)) {
            if (event.getType() == CONSOLE_EVENT) {
                const Data& data(event.getData(0));
                cout << "Event: timestamp=" << event.getTimestamp() << " severity=" <<
                    event.getSeverity() << " content=" << data.getProperties() << endl;
            }
        }
    }
}

