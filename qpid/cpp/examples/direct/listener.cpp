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
 *  listener.cpp: 
 *
 *  This program is one of three programs designed to be used
 *  together. 
 *  
 *    create_queues.cpp:
 *
 *      Creates a queue named "message_queue" on a broker, binding the
 *      queue to the "amq.direct" exchange, using the routing key
 *      "routing_key".
 *
 *    direct_producer.cpp 
 *
 *      Publishes to the "amq.direct" exchange, specifying the routing
 *      key "routing_key"
 *
 *    listener.cpp (this program):
 *
 *      Reads  from the "message_queue"  queue on  the broker  using a
 *      message listener.
 */


#include <qpid/client/Connection.h>
#include <qpid/client/Session.h>
#include <qpid/client/Message.h>
#include <qpid/client/MessageListener.h>
#include <qpid/client/SubscriptionManager.h>

#include <cstdlib>
#include <iostream>

using namespace qpid::client;
using namespace qpid::framing;

      
class Listener : public MessageListener{
  private:
    SubscriptionManager& subscriptions;
  public:
    Listener(SubscriptionManager& subscriptions);
    virtual void received(Message& message);
};

Listener::Listener(SubscriptionManager& subs) : subscriptions(subs)
{}

void Listener::received(Message& message) {
  std::cout << "Message: " << message.getData() << std::endl;
  if (message.getData() == "That's all, folks!") {
      std::cout << "Shutting down listener for " << message.getDestination()
                << std::endl;
      subscriptions.cancel(message.getDestination());
  }
}

int main(int argc, char** argv) {
    const char* host = argc>1 ? argv[1] : "127.0.0.1";
    int port = argc>2 ? atoi(argv[2]) : 5672;

    Connection connection;

    try {
      connection.open(host, port);
      Session session =  connection.newSession();

  //--------- Main body of program --------------------------------------------

      SubscriptionManager subscriptions(session);
      // Create a listener and subscribe it to the queue named "message_queue"
      Listener listener(subscriptions);
      subscriptions.subscribe(listener, "message_queue");
      // Receive messages until the subscription is cancelled
      // by Listener::received()
      subscriptions.run();

  //---------------------------------------------------------------------------

      connection.close();
      return 0;
    } catch(const std::exception& error) {
        std::cout << error.what() << std::endl;
    }
    return 1;   
}


