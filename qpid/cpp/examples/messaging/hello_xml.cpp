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

#include <qpid/messaging/Connection.h>
#include <qpid/messaging/Message.h>
#include <qpid/messaging/Receiver.h>
#include <qpid/messaging/Sender.h>
#include <qpid/messaging/Session.h>

#include <iostream>
#include <sstream>
using std::stringstream;

using namespace qpid::messaging;

int main(int argc, char** argv) {
  std::string broker = argc > 1 ? argv[1] : "localhost:5672";
  std::string connectionOptions = argc > 2 ? argv[2] : "";

  std::string query = 
    "let $w := ./weather "
    "return $w/station = 'Raleigh-Durham International Airport (KRDU)' "
    "   and $w/temperature_f > 50"
    "   and $w/temperature_f - $w/dewpoint > 5"
    "   and $w/wind_speed_mph > 7"
    "   and $w/wind_speed_mph < 20";

  stringstream address;

  address << "xml-exchange; {"
    " create: always, "        // This line and the next are not in docs
    " node: { type: topic, x-declare: { type: xml } }, " // Added so it works "out of the box"
    " link: { "
    "  x-bindings: [{ exchange: xml-exchange, key: weather, arguments: { xquery:\"" 
       << query 
       << "\"} }] "
    " } "
    "}";

  Connection connection(broker, connectionOptions);
  try {
    connection.open();
    Session session = connection.createSession();

    Receiver receiver = session.createReceiver(address.str());

    Message message;
    message.setContent(
       "<weather>"
       "<station>Raleigh-Durham International Airport (KRDU)</station>"
       "<wind_speed_mph>16</wind_speed_mph>"
       "<temperature_f>70</temperature_f>"
       "<dewpoint>35</dewpoint>"
       "</weather>");
    Sender sender = session.createSender("xml-exchange/weather");
    sender.send(message);

    Message response = receiver.fetch();

    std::cout << response.getContent() << std::endl;

    connection.close();
    return 0;
  } catch(const std::exception& error) {
    std::cerr << error.what() << std::endl;
    connection.close();
    return 1;   
  }
}
