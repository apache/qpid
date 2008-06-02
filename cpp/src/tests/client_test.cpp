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
 * This file provides a simple test (and example) of basic
 * functionality including declaring an exchange and a queue, binding
 * these together, publishing a message and receiving that message
 * asynchronously.
 */

#include <iostream>

#include "TestOptions.h"
#include "qpid/client/Connection.h"
#include "qpid/client/Message.h"
#include "qpid/client/Session.h"
#include "qpid/framing/FrameSet.h"
#include "qpid/framing/all_method_bodies.h"

using namespace qpid;
using namespace qpid::client;
using namespace qpid::framing;
using std::string;

struct Args : public TestOptions {
    uint msgSize;
    bool verbose;

    Args() : TestOptions("Simple test of Qpid c++ client; sends and receives a single message."), msgSize(26)
    {
        addOptions()            
            ("size", optValue(msgSize, "N"), "message size")
            ("verbose", optValue(verbose), "print out some status messages");
    }
};

const std::string chars("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ");

std::string generateData(uint size)
{
    if (size < chars.length()) {
        return chars.substr(0, size);
    }   
    std::string data;
    for (uint i = 0; i < (size / chars.length()); i++) {
        data += chars;
    }
    data += chars.substr(0, size % chars.length());
    return data;
}

void print(const std::string& text, const Message& msg)
{
    std::cout << text;
    if (msg.getData().size() > 16) {
        std::cout << msg.getData().substr(0, 16) << "...";
    } else {
        std::cout << msg.getData();
    }
    std::cout << std::endl;
}

int main(int argc, char** argv)
{
    try {
        Args opts;
        opts.parse(argc, argv);

        //Connect to the broker:
        Connection connection;
        opts.open(connection);
	if (opts.verbose) std::cout << "Opened connection." << std::endl;

        //Create and open a session on the connection through which
        //most functionality is exposed:
        Session session = connection.newSession();
	if (opts.verbose) std::cout << "Opened session." << std::endl;	


        //'declare' the exchange and the queue, which will create them
        //as they don't exist
	session.exchangeDeclare(arg::exchange="MyExchange", arg::type="direct");
	if (opts.verbose) std::cout << "Declared exchange." << std::endl;
	session.queueDeclare(arg::queue="MyQueue", arg::autoDelete=true, arg::exclusive=true);
	if (opts.verbose) std::cout << "Declared queue." << std::endl;

        //now bind the queue to the exchange
	session.exchangeBind(arg::exchange="MyExchange", arg::queue="MyQueue", arg::bindingKey="MyKey");
	if (opts.verbose) std::cout << "Bound queue to exchange." << std::endl;

        //create and send a message to the exchange using the routing
        //key we bound our queue with:
	Message msgOut(generateData(opts.msgSize));
        msgOut.getDeliveryProperties().setRoutingKey("MyKey");
        session.messageTransfer(arg::destination="MyExchange", arg::content=msgOut, arg::acceptMode=1);
	if (opts.verbose) print("Published message: ", msgOut);

        //subscribe to the queue, add sufficient credit and then get
        //incoming 'frameset', check that its a message transfer and
        //then convert it to a message (see Dispatcher and
        //SubscriptionManager utilties for common reusable patterns at
        //a higher level)
        session.messageSubscribe(arg::queue="MyQueue", arg::destination="MyId");
        session.messageFlow(arg::destination="MyId", arg::unit=0, arg::value=1); //credit for one message
        session.messageFlow(arg::destination="MyId", arg::unit=1, arg::value=0xFFFFFFFF); //credit for infinite bytes
	if (opts.verbose) std::cout << "Subscribed to queue." << std::endl;
        FrameSet::shared_ptr incoming = session.get();
        if (incoming->isA<MessageTransferBody>()) {
            Message msgIn(*incoming);
            if (msgIn.getData() == msgOut.getData()) {
                if (opts.verbose) std::cout << "Received the exepected message." << std::endl;
                session.messageAccept(SequenceSet(msgIn.getId()));
                session.markCompleted(msgIn.getId(), true, true);
            } else {
                print("Received an unexepected message: ", msgIn);
            }
        } else {
            throw Exception("Unexpected command received");
        }
        
        //close the session & connection
	session.close();
	if (opts.verbose) std::cout << "Closed session." << std::endl;
	connection.close();	
	if (opts.verbose) std::cout << "Closed connection." << std::endl;
    return 0;
    } catch(const std::exception& e) {
	std::cout << e.what() << std::endl;
    }
    return 1;
}
