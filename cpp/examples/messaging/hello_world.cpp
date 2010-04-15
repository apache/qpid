#include <qpid/messaging/Connection.h>
#include <qpid/messaging/Message.h>
#include <qpid/messaging/Receiver.h>
#include <qpid/messaging/Sender.h>
#include <qpid/messaging/Session.h>

#include <iostream>
#include <sstream>

using namespace qpid::messaging;

int main() {

  Connection connection("localhost:5672");
  try {
    connection.open();
    Session session = connection.createSession();

    std::string address = "message_queue";

    Sender sender = session.createSender(address);
    
    for (int i=0; i<5; i++) {
      std::stringstream content;
      content << "Message " << i;
      std::cout << "Sending " << content.str() << std::endl;
      sender.send(Message(content.str()));
    }
    sender.close();
	
    Receiver receiver = session.createReceiver(address);

    Message message;
    Duration timeout(1000); /* in milliseconds */
    while (receiver.fetch(message, timeout)) {
      std::cout << "Received " << message.getContent() << std::endl;
      session.acknowledge();
    }

    receiver.close();

    connection.close();
    return 0;
  } catch(const std::exception& error) {
    std::cerr << error.what() << std::endl;
    connection.close();
    return 1;   
  }
}
