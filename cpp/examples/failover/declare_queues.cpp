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

#include <unistd.h>
#include <cstdlib>
#include <iostream>
#include <fstream>

using namespace qpid::client;
using namespace qpid::framing;


using namespace std;




int 
main ( int argc, char ** argv) 
{
  if ( argc < 3 )
  {
    std::cerr << "Usage: ./declare_queues host cluster_port_file_name\n";
    std::cerr << "i.e. for host: 127.0.0.1\n";
    exit(1);
  }

  const char * host = argv[1];
  int port = atoi(argv[2]);


  try 
  {
    FailoverConnection connection;
    FailoverSession    * session;

    connection.open ( host, port );
    session = connection.newSession();

    session->queueDeclare ( "message_queue");
    
    /*
    session->exchangeBind 
      ( arg::exchange="amq.direct", 
        arg::queue="message_queue", 
        arg::bindingKey="routing_key"
      );
     * */
    session->exchangeBind ( "message_queue",
                           "amq.direct", 
                           "routing_key"
                         );
    connection.close();
    return 0;
  }
  catch ( const std::exception& error ) 
  {
    std::cout << error.what() << std::endl;
  }

  return 1;
}





