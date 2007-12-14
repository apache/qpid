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
 *  client.cpp
 *
 *  This program is one of two programs that illustrate the
 *  request/response pattern.
 *  
 *    client.cpp (this program)
 *
 *      Make requests of a service, print the response.
 *
 *    service.cpp
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

#include <sstream>

using namespace qpid::client;
using namespace qpid::framing;

class Listener : public MessageListener{
private:
  Session session;
  std::string destination_name;
  Dispatcher dispatcher;
  int counter;
public:
  Listener(Session& session, string destination_name): 
    destination_name(destination_name),
    dispatcher(session),
    session(session),
    counter(0)
  {};

  virtual void listen();
  virtual void wait();
  virtual void received(Message& message);
  ~Listener() { };
};


void Listener::listen() {
  std::cout << "Activating response queue listener for: " <<destination_name << std::endl;

  session.messageSubscribe(arg::queue=destination_name, arg::destination=destination_name);

  session.messageFlow(arg::destination=destination_name, arg::unit=0, arg::value=1);//messages ### Define a constant?
  session.messageFlow(arg::destination=destination_name, arg::unit=1, arg::value=0xFFFFFFFF);//bytes ###### Define a constant?


  dispatcher.listen(destination_name, this);
}


void Listener::wait() {
  std::cout << "Waiting for all responses to arrive ..." << std::endl;
  dispatcher.run();
}


void Listener::received(Message& message) {
  std::cout << "Response: " << message.getData() << std::endl;

  ++ counter;
  if (counter > 3) {
      std::cout << "Shutting down listener for " << destination_name << std::endl;
      dispatcher.stop();
  }
}


using std::stringstream;
using std::string;

int main() {
    Connection connection;
    Message request;
    try {
        connection.open("127.0.0.1", 5672 );
        Session session =  connection.newSession();

  //--------- Main body of program --------------------------------------------

	// Create a response queue so the server can send us responses
	// to our requests. Use the client's session ID as the name
	// of the response queue.

	stringstream response_queue;
	response_queue << "client " << session.getId();

        // Use the name of the response queue as the routing key

	session.queueDeclare(arg::queue=response_queue.str());
        session.queueBind(arg::exchange="amq.direct", arg::queue=response_queue.str(), arg::routingKey=response_queue.str());

	// Create a listener for the response queue and start listening.

	Listener listener(session, response_queue.str());
	listener.listen();


	// The routing key for the request queue is simply
	// "request", and all clients use the same routing key.
	//
	// Each client sends the name of their own response queue so
	// the service knows where to route messages.

	request.getDeliveryProperties().setRoutingKey("request");
	request.getMessageProperties().setReplyTo(ReplyTo("", response_queue.str()));

	// Now send some requests ...

	string s[] = {
		"Twas brillig, and the slithy toves",
		"Did gire and gymble in the wabe.",
		"All mimsy were the borogroves,",
		"And the mome raths outgrabe."
        };


	for (int i=0; i<4; i++) {
	  request.setData(s[i]);
          session.messageTransfer(arg::content=request, arg::destination="amq.direct");
	  std::cout << "Request: " << s[i] << std::endl;
	}

	// And wait for any outstanding responses to arrive

	listener.wait();
	

  //-----------------------------------------------------------------------------

        connection.close();
        return 0;
    } catch(const std::exception& error) {
        std::cout << error.what() << std::endl;
    }
    return 1;
}


