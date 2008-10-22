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
#include <qpid/client/Message.h>
#include <qpid/client/SubscriptionManager.h>

#include <iostream>
#include <fstream>


using namespace qpid::client;
using namespace qpid::framing;

using namespace std;


struct Listener : public MessageListener
{
    FailoverSubscriptionManager & subscriptionManager;

    Listener ( FailoverSubscriptionManager& subs );

    void shutdown() { subscriptionManager.stop(); }
  
    virtual void received ( Message & message );

    int count;
};





Listener::Listener ( FailoverSubscriptionManager & s ) : 
    subscriptionManager(s),
    count(0)
{
}





void 
Listener::received ( Message & message ) 
{
    /*
    if(! (count%1000))
      std::cerr << "\t\tListener received: " << message.getData() << std::endl;
     * */

    ++ count;

    if (message.getData() == "That's all, folks!") 
    {
        std::cout << "Shutting down listener for " << message.getDestination()
                  << std::endl;

        std::cout << "Listener received " << count << " messages.\n";
        subscriptionManager.cancel(message.getDestination());
        shutdown ( );
    }
}







int 
main ( int argc, char ** argv ) 
{
    const char* host = argc>1 ? argv[1] : "127.0.0.1";
    int port = argc>2 ? atoi(argv[2]) : 5672;
    string program_name = "LISTENER";

    try {

        FailoverConnection connection;
        FailoverSession    * session;

        connection.open ( host, port );
        session = connection.newSession();

        FailoverSubscriptionManager subscriptions ( session );
        Listener listener ( subscriptions );
        subscriptions.subscribe ( listener, "message_queue" );
        subscriptions.run ( );

        connection.close();
        std::cout << program_name << ": " << " completed without error." << std::endl;
        return 0;

    } catch(const std::exception& error) {
        std::cout << program_name  << ": " << error.what() << std::endl;
    }

    return 0;
}




