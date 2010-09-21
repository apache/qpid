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

  address << "xml; {"
    " create: always, "        // This line and the next are not in docs
    " node: { type: topic, x-declare: { type: xml } }, " // Added so it works "out of the box"
    " link: { "
    "  x-bindings: [{ exchange: xml, key: weather, arguments: { xquery:\"" 
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
    Sender sender = session.createSender("xml/weather");
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
