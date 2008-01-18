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
 *  direct_config_queues.cpp
 *
 *  This program is one of three programs designed to be used
 *  together. These programs use the "amq.direct" exchange.
 *  
 *  direct_config_queues.cpp (this program):
 *
 *      Creates a queue on a broker, binding a routing key to route
 *      messages to that queue.
 *
 *  direct_publisher.cpp:
 *
 *      Publishes to a broker, specifying a routing key.
 *
 *  direct_listener.cpp
 *
 *      Reads from a queue on the broker using a message listener.
 *
 */

#include <qpid/client/Connection.h>
#include <qpid/client/Session.h>

#include <unistd.h>
#include <cstdlib>
#include <iostream>

using namespace qpid::client;
using namespace qpid::framing;

using std::string;


int main(int argc, char** argv) {
    const char* host = argc>1 ? argv[1] : "127.0.0.1";
    int port = argc>2 ? atoi(argv[2]) : 5672;
    Connection connection;
    Message msg;
    try {
      connection.open(host, port);
      Session session =  connection.newSession();


  //--------- Main body of program --------------------------------------------

      // Create a queue named "message_queue", and route all messages whose
      // routing key is "routing_key to this newly created queue.

      session.queueDeclare(arg::queue="message_queue");
      session.queueBind(arg::queue="message_queue", arg::exchange="amq.fanout");


  //-----------------------------------------------------------------------------

      connection.close();
      return 0;
    } catch(const std::exception& error) {
        std::cout << error.what() << std::endl;
    }
    return 1;
   
}



