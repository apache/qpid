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

#include <qpid/client/FailoverConnection.h>
#include <qpid/client/Session.h>
#include <qpid/client/AsyncSession.h>
#include <qpid/client/Message.h>


#include <iostream>
#include <sstream>

using namespace qpid::client;
using namespace qpid::framing;

using namespace std;

int 
main ( int argc, char ** argv) 
{
    const char* host = argc>1 ? argv[1] : "127.0.0.1";
    int port = argc>2 ? atoi(argv[2]) : 5672;
    int count  = argc>3 ? atoi(argv[3]) : 30;
    int delayMs  = argc>4 ? atoi(argv[4]) : 1000;

    try {
        FailoverConnection connection;
        FailoverSession    * session;
        Message message;

        string program_name = "PRODUCER";

        connection.open ( host, port );
        session = connection.newSession();
        int sent  = 0;
        while ( sent < count ) {
            message.getDeliveryProperties().setRoutingKey("routing_key"); 
            std::cout << "sending message " 
                      << sent
                      << " of " 
                      << count 
                      << ".\n";
            stringstream message_data;
            message_data << sent;
            message.setData(message_data.str());

            /* MICK FIXME
               session.messageTransfer ( arg::content=message,  
               arg::destination="amq.direct"
               ); */
            session->messageTransfer ( "amq.direct",
                                       1,
                                       0,
                                       message
            );
            usleep ( 1000*delayMs );
            ++ sent;
        }
        message.setData ( "That's all, folks!" );

        /* MICK FIXME
           session.messageTransfer ( arg::content=message,  
           arg::destination="amq.direct"
           ); 
        */
        session->messageTransfer ( "amq.direct",
                                   1,
                                   0,
                                   message
        ); 

        session->sync();
        connection.close();
        return 0;  
    } catch(const std::exception& error) {
        std::cout << error.what() << std::endl;
    }
    return 1;
}
