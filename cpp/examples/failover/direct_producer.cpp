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
    string program_name = "PRODUCER";


    try {
        FailoverConnection connection;
        FailoverSession    * session;
        Message message;

        connection.open ( host, port );
        session = connection.newSession();
        bool report = true;
        int sent  = 0;
        while ( sent < count ) {

            message.getDeliveryProperties().setRoutingKey("routing_key"); 


            if ( count > 1000 )
              report = !(sent % 1000);

            if ( report )
            {
              std::cout << "sending message " 
                        << sent
                        << ".\n";
            }

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

            ++ sent;
        }
        message.setData ( "That's all, folks!" );

        /* FIXME mgoulish 16 Oct 08
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
        std::cout << program_name 
                  << " sent " 
                  << sent
                  << " messages.\n";

        std::cout << program_name << ": " << " completed without error." << std::endl;
        return 0;  
    } catch(const std::exception& error) {
        std::cout << program_name << ": " << error.what() << std::endl;
        std::cout << program_name << "Exiting.\n";
        return 1;
    }
    return 1;
}
