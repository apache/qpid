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
 * This file provides one half of a test and example of a pub-sub
 * style of interaction. See topic_listener.cpp for the other half, in
 * which the logic for subscribers is defined.
 * 
 * This file contains the publisher logic. The publisher will send a
 * number of messages to the exchange with the appropriate routing key
 * for the logical 'topic'. Once it has done this it will then send a
 * request that each subscriber report back with the number of message
 * it has received and the time that elapsed between receiving the
 * first one and receiving the report request. Once the expected
 * number of reports are received, it sends out a request that each
 * subscriber shutdown.
 */

#include "qpid/Exception.h"
#include "qpid/client/Channel.h"
#include "qpid/client/Connection.h"
#include "qpid/client/Exchange.h"
#include "qpid/client/MessageListener.h"
#include "qpid/client/Queue.h"
#include "qpid/sys/Monitor.h"
#include <unistd.h>
#include "qpid/sys/Time.h"
#include <cstdlib>
#include <iostream>

using namespace qpid::client;
using namespace qpid::sys;
using std::string;


int main() {
    Connection connection;
    Channel channel;
    Message msg;
    try {
        connection.open("127.0.0.1", 5672, "guest", "guest", "/test");
        connection.openChannel(channel);


  //--------- Main body of program --------------------------------------------

        Queue response("listener");
        channel.declareQueue(response);
        channel.bind(Exchange::STANDARD_TOPIC_EXCHANGE, response, "listener");
        
	channel.start();

  //-----------------------------------------------------------------------------

        channel.close();
        connection.close();
        return 0;
    } catch(const std::exception& error) {
        std::cout << error.what() << std::endl;
    }
    return 1;
}


