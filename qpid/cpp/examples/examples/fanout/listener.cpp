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
 *    direct_listener.cpp (this program):
 *
 *      Reads from a queue on the broker using a message listener.
 *
 */

#include <qpid/client/Dispatcher.h>
#include <qpid/client/Connection.h>
#include <qpid/client/Session.h>
#include <qpid/client/Message.h>
#include <qpid/client/MessageListener.h>

#include <unistd.h>
#include <cstdlib>
#include <iostream>

using namespace qpid::client;
using namespace qpid::framing;

      
class Listener : public MessageListener{
private:
  std::string destination_name;
  Dispatcher dispatcher;
public:
  Listener(Session& session, string destination_name): 
    destination_name(destination_name),
    dispatcher(session)
  {};

  virtual void listen();
  virtual void received(Message& message);
  ~Listener() { };
};


void Listener::listen() {
  std::cout << "Activating listener for: " <<destination_name << std::endl;
  dispatcher.listen(destination_name, this);

  // ### The following line gives up control - it should be possible
  // ### to listen without giving up control!

  dispatcher.run();
}


void Listener::received(Message& message) {
  std::cout << "Message: " << message.getData() << std::endl;

  if (message.getData() == "That's all, folks!") {
      std::cout << "Shutting down listener for " <<destination_name << std::endl;
      dispatcher.stop();
  }
}



int main(int argc, char** argv) {
    const char* host = argc>1 ? argv[1] : "127.0.0.1";
    int port = argc>2 ? atoi(argv[2]) : 5672;
    Connection connection;
    Message msg;
    try {
      connection.open(host, port);
      Session session =  connection.newSession();

  //--------- Main body of program --------------------------------------------


      //  Subscribe to the queue, route it to a client destination for
      //  the  listener. (The destination  name merely  identifies the
      //  destination in the listener, you can use any name as long as
      //  you use the same name for the listener).

      session.messageSubscribe(arg::queue="message_queue", arg::destination="listener_destination");

      session.messageFlow(arg::destination="listener_destination", arg::unit=0, arg::value=1);//messages ### Define a constant?
      session.messageFlow(arg::destination="listener_destination", arg::unit=1, arg::value=0xFFFFFFFF);//bytes ###### Define a constant?

      //  Tell the listener to listen to the destination we just
      //  created above.

      Listener listener(session, "listener_destination");
      listener.listen();

  //-----------------------------------------------------------------------------

      connection.close();
      return 0;
    } catch(const std::exception& error) {
        std::cout << error.what() << std::endl;
    }
    return 1;   
}


