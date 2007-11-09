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
 *  topic_config_queues.cpp
 *
 *  This program is one of three programs designed to be used
 *  together. These programs use the topic exchange.
 *  
 *  topic_config_queues.cpp (this program):
 *
 *      Creates a queue on a broker, binding a routing key to route
 *      messages to that queue.
 *
 *  topic_publisher.cpp:
 *
 *      Publishes to a broker, specifying a routing key.
 *
 *  topic_listener.cpp
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


int main() {
    Connection connection;
    Message msg;
    try {
      connection.open("127.0.0.1", 5672);
      Session session =  connection.newSession();


  //--------- Main body of program --------------------------------------------


      /*  A consumer application reads from the queue, and needs no
       *  knowledge of the exchanges used to route messages to the
       *  queue, or of the routing keys.
       *
       *  A publisher application writes to the exchange, providing a
       *  routing key, It needs no knowledge of the queues or bindings
       *  used to route messages to consumers.
       */


       /* Create queues on the broker. */

      session.queueDeclare(arg::queue="news_queue");
      session.queueDeclare(arg::queue="weather_queue");
      session.queueDeclare(arg::queue="usa_queue");
      session.queueDeclare(arg::queue="europe_queue");

      /* Bind these queues using routing keys, so messages will be
	 delivered to the right queues. */

      session.queueBind(arg::exchange="amq.topic", arg::queue="news_queue", arg::routingKey="#.news");
      session.queueBind(arg::exchange="amq.topic", arg::queue="weather_queue", arg::routingKey="#.weather");
      session.queueBind(arg::exchange="amq.topic", arg::queue="usa_queue", arg::routingKey="usa.#");
      session.queueBind(arg::exchange="amq.topic", arg::queue="europe_queue", arg::routingKey="europe.#");


      /*
       *  We use a separate 'control' routing key for control
       *  messages. All such messages are routed to each queue.  In
       *  this demo, we use a message with the content "That's all,
       *  Folks!" to signal that no more messages will be sent, and
       *  users of the queue can stop listening for messages.
       *
       *  Because wildcard matching can result in more than one match for
       *  a given message, it can place more messages on the queues than
       *  were originally received.
       *
       *  We do not use wildcard matching for control messages. We
       *  want to make sure that each such message is received once
       *  and only once.
       */


      session.queueBind(arg::exchange="amq.topic", arg::queue="news_queue", arg::routingKey="control");
      session.queueBind(arg::exchange="amq.topic", arg::queue="weather_queue", arg::routingKey="control");
      session.queueBind(arg::exchange="amq.topic", arg::queue="usa_queue", arg::routingKey="control");
      session.queueBind(arg::exchange="amq.topic", arg::queue="europe_queue", arg::routingKey="control");


  //-----------------------------------------------------------------------------

      connection.close();
      return 0;
    } catch(const std::exception& error) {
        std::cout << error.what() << std::endl;
    }
    return 1;
   
}



