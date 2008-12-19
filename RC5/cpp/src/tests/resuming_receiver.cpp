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
#include <qpid/client/Message.h>
#include <qpid/client/SubscriptionManager.h>

#include <iostream>
#include <fstream>


using namespace qpid;
using namespace qpid::client;
using namespace qpid::framing;

using namespace std;


class Listener : public MessageListener, 
                 public FailoverManager::Command, 
                 public FailoverManager::ReconnectionStrategy
{
  public:
    Listener ( int report_frequency = 1000, int verbosity = 0 );
    void received(Message& message);
    void execute(AsyncSession& session, bool isRetry);
    void check();
    void editUrlList(std::vector<Url>& urls);
  private:
    Subscription subscription;
    uint count;
    uint received_twice;
    uint lastSn;
    bool gaps;
    uint  reportFrequency;
    int  verbosity;
};


Listener::Listener(int freq, int verbosity) 
  : count(0), 
    received_twice(0), 
    lastSn(0), 
    gaps(false), 
    reportFrequency(freq),
    verbosity(verbosity)
{}


void Listener::received(Message & message) 
{
    if (message.getData() == "That's all, folks!") 
    {
        if(verbosity > 0 )
        {
            std::cout << "Shutting down listener for " 
                      << message.getDestination() << std::endl;

            std::cout << "Listener received " 
                      << count 
                      << " messages (" 
                      << received_twice 
                      << " received_twice)" 
                      << endl;
        }
        subscription.cancel();
        if ( verbosity > 0 )
          std::cout << "LISTENER COMPLETED\n";
    } else {
        uint sn = message.getHeaders().getAsInt("sn");
        if (lastSn < sn) {
            if (sn - lastSn > 1) {
                std::cerr << "Error: gap in sequence between " << lastSn << " and " << sn << std::endl;
                gaps = true;
            }
            lastSn = sn;
            ++count;
            if ( ! ( count % reportFrequency ) ) {
                if ( verbosity > 0 )
                    std::cout << "Listener has received " 
                              << count 
                              << " messages.\n";
            }
        } else {
            ++received_twice;
        }
    }
}

void Listener::check()
{
    if (gaps) throw Exception("Detected gaps in sequence; messages appear to have been lost.");
}

void Listener::execute(AsyncSession& session, bool isRetry)
{
    if (isRetry) {
        // std::cout << "Resuming from " << count << std::endl;
    }
    SubscriptionManager subs(session);
    subscription = subs.subscribe(*this, "message_queue");
    subs.run();
}

void Listener::editUrlList(std::vector<Url>& urls)
{
    /**
     * A more realistic algorithm would be to search through the list
     * for prefered hosts and ensure they come first in the list.
     */
    if (urls.size() > 1) std::rotate(urls.begin(), urls.begin() + 1, urls.end());
}

int main(int argc, char ** argv)
{
    ConnectionSettings settings;

    if ( argc != 5 )
    {
      std::cerr << "Usage: resuming_receiver host port report_frequency verbosity\n";
      return 1;
    }

    settings.host       = argv[1];
    settings.port       = atoi(argv[2]);
    int reportFrequency = atoi(argv[3]);
    int verbosity       = atoi(argv[4]);

    Listener listener(reportFrequency, verbosity);
    FailoverManager connection(settings, &listener);

    try {
        connection.execute(listener);
        connection.close();
        listener.check();
        return 0;
    } catch(const std::exception& error) {
        std::cerr << "Receiver failed: " << error.what() << std::endl;
    }
    return 1;
}



