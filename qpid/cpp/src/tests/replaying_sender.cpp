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
#include <qpid/client/AsyncSession.h>
#include <qpid/client/Message.h>
#include <qpid/client/MessageReplayTracker.h>
#include <qpid/Exception.h>

#include <iostream>
#include <sstream>

using namespace qpid;
using namespace qpid::client;
using namespace qpid::framing;

using namespace std;

class Sender : public FailoverManager::Command
{
  public:
    Sender(const std::string& queue, uint count, uint reportFreq);
    void execute(AsyncSession& session, bool isRetry);
    uint getSent();

    int verbosity;

  private:
    MessageReplayTracker sender;
    const uint count;
    uint sent;
    const uint reportFrequency;
    Message message;
    
};

Sender::Sender(const std::string& queue, uint count_, uint reportFreq ) : sender(10), count(count_), sent(0), reportFrequency(reportFreq)
{
    message.getDeliveryProperties().setRoutingKey(queue);
}

void Sender::execute(AsyncSession& session, bool isRetry)
{
    if (verbosity > 0)
        std::cout << "replaying_sender " << (isRetry ? "first " : "re-") << "connect." << endl;
    if (isRetry) sender.replay(session);
    else sender.init(session);
    while (sent < count) {
        stringstream message_data;
        message_data << ++sent;
        message.setData(message_data.str());
        message.getHeaders().setInt("sn", sent);
        sender.send(message);
        if (count > reportFrequency && !(sent % reportFrequency)) {
            if ( verbosity > 0 )
                std::cout << "Sender sent " << sent << " of " << count << std::endl;
        }
    }
    message.setData("That's all, folks!");
    sender.send(message);

    if ( verbosity > 0 )
      std::cout << "SENDER COMPLETED\n";
}

uint Sender::getSent()
{
    return sent;
}

int main(int argc, char ** argv) 
{
    ConnectionSettings settings;

    if ( argc != 6 )
    {
      std::cerr << "Usage: replaying_sender host port n_messages report_frequency verbosity\n";
      return 1;
    }

    settings.host       = argv[1];
    settings.port       = atoi(argv[2]);
    int n_messages      = atoi(argv[3]);
    int reportFrequency = atoi(argv[4]);
    int verbosity       = atoi(argv[5]);

    FailoverManager connection(settings);
    Sender sender("message_queue", n_messages, reportFrequency );
    sender.verbosity = verbosity;
    try {
        connection.execute ( sender );
        if ( verbosity > 0 )
        {
            std::cout << "Sender finished.  Sent " 
                      << sender.getSent() 
                      << " messages." 
                      << endl;
        }
        connection.close();
        return 0;  
    } 
    catch(const std::exception& error) 
    {
        cerr << "Sender (host: " 
             << settings.host 
             << " port: " 
             << settings.port
             << " )  "
             << " Failed: " 
             << error.what() 
             << std::endl;
    }
    return 1;
}
