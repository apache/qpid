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

#include <qpid/client/FailoverManager.h>
#include <qpid/client/Session.h>
#include <qpid/Exception.h>

#include <cstdlib>
#include <iostream>

using namespace qpid::client;

using namespace std;

int main(int argc, char ** argv) 
{
    ConnectionSettings settings;
    if ( argc != 3 )
    {
      cerr << "Usage: declare_queues host port\n";
      return 1;
    }

    settings.host = argv[1];
    settings.port = atoi(argv[2]);
    
    FailoverManager connection(settings);
    try {
        bool complete = false;
        while (!complete) {
            Session session = connection.connect().newSession();
            try {
                session.queueDeclare(arg::queue="message_queue");
                complete = true;
            } catch (const qpid::TransportFailure&) {}
        }
        connection.close();
        return 0;
    } catch (const exception& error) {
        cerr << "declare_queues failed:" << error.what() << endl;
        cerr << "  host: " << settings.host 
             << "  port: " << settings.port << endl;
        return 1;
    }
    
}





