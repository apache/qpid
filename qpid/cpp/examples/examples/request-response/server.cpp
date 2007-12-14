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
 *  server.cpp
 *
 *  This program is one of two programs that illustrate the
 *  request/response pattern.
 *  
 *    client.cpp 
 *
 *      Make requests of a service, print the response.
 *
 *    server.cpp (this program)
 *
 *      Accept requests, reverse the letters in each message, and
 *      return it as a response.
 *
 */


#include <qpid/client/Connection.h>
#include <qpid/client/Dispatcher.h>
#include <qpid/client/Session.h>
#include <qpid/client/Message.h>
#include <qpid/client/MessageListener.h>


#include <unistd.h>
#include <cstdlib>
#include <iostream>
#include <algorithm>

#include <sstream>
#include <string>

using namespace qpid::client;
using namespace qpid::framing;
using std::stringstream;
using std::string;

class Listener : public MessageListener{
private:
  std::string destination_name;
  Dispatcher dispatcher;
  Session session;
public:
  Listener(Session& session, string destination_name): 
    destination_name(destination_name),
    dispatcher(session),
    session(session)
  {};

  virtual void listen();
  virtual void received(Message& message);
  virtual void wait();
  ~Listener() { };
};


void Listener::listen() {
  std::cout << "Activating request queue listener for: " <<destination_name << std::endl;

  session.messageSubscribe(arg::queue=destination_name, arg::destination=destination_name);

  // ##### Should not be needed. Sigh.
  session.messageFlow(arg::destination=destination_name, arg::unit=0, arg::value=1);//messages ### Define a constant?
  session.messageFlow(arg::destination=destination_name, arg::unit=1, arg::value=0xFFFFFFFF);//bytes ###### Define a constant?

  dispatcher.listen(destination_name, this);
}


void Listener::wait() {
  std::cout << "Waiting for requests" << std::endl;
  dispatcher.run();
}


void Listener::received(Message& request) {

  Message response;

  // Get routing key for response from the request's replyTo property

  string routingKey;

  if (request.getMessageProperties().hasReplyTo()) {
    routingKey = request.getMessageProperties().getReplyTo().getRoutingKey();
  } else {
    std::cout << "Error: " << "No routing key for request (" << request.getData() << ")" << std::endl;
    return;
  } 

  std::cout << "Request: " << request.getData() << "  (" <<routingKey << ")" << std::endl;

  // Transform message content to upper case
  std::string s = request.getData();
  std::transform (s.begin(), s.end(), s.begin(), toupper);
  response.setData(s);

  // Send it back to the user
  response.getDeliveryProperties().setRoutingKey(routingKey);
  session.messageTransfer(arg::content=response, arg::destination="amq.direct");
}


int main() {
  Connection connection;
    Message message;
    try {
        connection.open("127.0.0.1", 5672 );
        Session session =  connection.newSession();

  //--------- Main body of program --------------------------------------------

	// Create a request queue for clients to use when making
	// requests.

	string request_queue = "request";

        // Use the name of the request queue as the routing key

	session.queueDeclare(arg::queue=request_queue);
        session.queueBind(arg::exchange="amq.direct", arg::queue=request_queue, arg::routingKey=request_queue);

	// Create a listener for the request queue and start listening.

	Listener listener(session, request_queue);
	listener.listen();
	listener.wait();


  //-----------------------------------------------------------------------------

        connection.close();
        return 0;
    } catch(const std::exception& error) {
        std::cout << error.what() << std::endl;
    }
    return 1;
}


