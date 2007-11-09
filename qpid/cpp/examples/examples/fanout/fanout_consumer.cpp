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

/**
 *  direct_listener.cpp:
 *
 *  This program is one of three programs designed to be used
 *  together. These programs do not specify the exchange type - the
 *  default exchange type is the direct exchange.
 *  
 *    direct_config_queues.cpp:
 *
 *      Creates a queue on a broker, binding a routing key to route
 *      messages to that queue.
 *
 *    direct_publisher.cpp:
 *
 *      Publishes to a broker, specifying a routing key.
 *
 *    direct_consumer.cpp (this program):
 *
 *      Reads from a queue on the broker using session.get().
 *
 *      This is less efficient that direct_listener.cpp, but simpler,
 *      and can be a better approach when synchronizing messages from
 *      multiple queues.
 *
 */

#include <qpid/client/Dispatcher.h>
#include <qpid/client/Connection.h>
#include <qpid/client/Session.h>
#include <qpid/client/ClientMessage.h>

#include <unistd.h>
#include <cstdlib>
#include <iostream>

using namespace qpid::client;
using namespace qpid::framing;

      
int main() {
    Connection connection;
    Message msg;
    try {
      connection.open("127.0.0.1", 5672);
      Session session =  connection.newSession();

  //--------- Main body of program --------------------------------------------

      Listener listener(session, "destination");
      ### session.get();

  //-----------------------------------------------------------------------------

      connection.close();
      return 0;
    } catch(const std::exception& error) {
        std::cout << error.what() << std::endl;
    }
    return 1;   
}


