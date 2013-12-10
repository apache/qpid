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

#include <sys/select.h>
#include <time.h>

#include <qpid/messaging/Connection.h>
#include <qpid/messaging/Duration.h>

#define QMF_USE_DEPRECATED_API
#include <qmf/Agent.h>
#include <qmf/ConsoleEvent.h>
#include <qmf/ConsoleSession.h>
#include <qpid/types/Variant.h>
#include "qmf/posix/EventNotifier.h"

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
    session.setAgentFilter("");

    posix::EventNotifier notifier(session);

    int fd(notifier.getHandle());
    time_t lastUpdate;
    bool ftl = false;

    time(&lastUpdate);

    while (true) {
        fd_set rfds;
        struct timeval tv;
        int nfds, retval;

        FD_ZERO(&rfds);
        FD_SET(fd, &rfds);
        nfds = fd + 1;
        tv.tv_sec = 10;
        tv.tv_usec = 0;

        retval = select(nfds, &rfds, NULL, NULL, &tv);

        if (retval > 0 && FD_ISSET(fd, &rfds)) {
            ConsoleEvent event;
            while (session.nextEvent(event, Duration::IMMEDIATE)) {
                string eventType = "";
                switch(event.getType()) {
                case CONSOLE_AGENT_ADD:             eventType = "Added"; break;
                case CONSOLE_AGENT_DEL:             eventType = "Deleted"; break;
                case CONSOLE_AGENT_RESTART:         eventType = "Restarted"; break;
                case CONSOLE_AGENT_SCHEMA_UPDATE:   eventType = "Schema Updated"; break;
                case CONSOLE_AGENT_SCHEMA_RESPONSE: eventType = "Schema Response"; break;
                case CONSOLE_EVENT:                 eventType = "Event"; break;
                case CONSOLE_QUERY_RESPONSE:        eventType = "Query Response"; break;
                case CONSOLE_METHOD_RESPONSE:       eventType = "Method Response"; break;
                case CONSOLE_EXCEPTION:             eventType = "Exception"; break;
                case CONSOLE_SUBSCRIBE_ADD:         eventType = "Subscription Added"; break;
                case CONSOLE_SUBSCRIBE_UPDATE:      eventType = "Subscription Updated"; break;
                case CONSOLE_SUBSCRIBE_DEL:         eventType = "Subscription Deleted" ; break;
                case CONSOLE_THREAD_FAILED:         eventType = "Thread Failure"; break;
                default:                            eventType = "[UNDEFINED]";
                }
                cout << "Agent " << eventType << ": " << event.getAgent().getName() << endl;
            }
        } else {
            cout << "No message received within waiting period." << endl;
        }
    }
}

