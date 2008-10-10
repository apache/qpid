
#include <qpid/client/FailoverConnection.h>
#include <qpid/client/Session.h>
#include <qpid/client/Message.h>
#include <qpid/client/SubscriptionManager.h>

#include <unistd.h>
#include <cstdlib>
#include <iostream>
#include <fstream>


using namespace qpid::client;
using namespace qpid::framing;

using namespace std;




struct Recorder 
{
  unsigned int max_messages;
  unsigned int * messages_received;

  Recorder ( )
  {
    max_messages = 1000;
    messages_received = new unsigned int [ max_messages ];
    memset ( messages_received, 0, max_messages * sizeof(int) );
  }


  void
  received ( int i )
  {
    messages_received[i] ++;
  }



  void
  report ( )
  {
    int i;

    int last_received_message = 0;

    vector<unsigned int> missed_messages,
                         multiple_messages;

    /*----------------------------------------------------
       Collect indices of missed and multiple messages.
    ----------------------------------------------------*/
    bool seen_first_message = false;
    for ( i = max_messages - 1; i >= 0; -- i )
    {
      if ( ! seen_first_message )
      {
        if ( messages_received [i] > 0 )
        {
          seen_first_message = true;
          last_received_message = i;
        }
      }
      else
      {
        if ( messages_received [i] == 0 )
          missed_messages.push_back ( i );
        else
        if ( messages_received [i] > 1 )
        {
          multiple_messages.push_back ( i );
        }
      }
    }

    /*--------------------------------------------
       Report missed messages.
    --------------------------------------------*/
    char const * verb = ( missed_messages.size() == 1 ) 
                        ? " was "
                        : " were ";

    char const * plural = ( missed_messages.size() == 1 ) 
                          ? "."
                          : "s.";

    std::cerr << "Listener::shutdown: There"
              << verb
              << missed_messages.size()
              << " missed message"
              << plural
              << endl;

    for ( i = 0; i < int(missed_messages.size()); ++ i )
    {
      std::cerr << "    " << i << " was missed.\n";
    }


    /*--------------------------------------------
       Report multiple messages.
    --------------------------------------------*/
    verb = ( multiple_messages.size() == 1 ) 
           ? " was "
           : " were ";

    plural = ( multiple_messages.size() == 1 ) 
           ? "."
           : "s.";

    std::cerr << "Listener::shutdown: There"
              << verb
              << multiple_messages.size()
              << " multiple message"
              << plural
              << endl;

    for ( i = 0; i < int(multiple_messages.size()); ++ i )
    {
      std::cerr << "    " 
                << multiple_messages[i] 
                << " was received " 
                << messages_received [ multiple_messages[i] ]
                << " times.\n";
    }
    
    /*
    for ( i = 0; i < last_received_message; ++ i )
    {
      std::cerr << "Message " << i << ": " << messages_received[i] << std::endl;
    }
    */
  }

};


      

struct Listener : public MessageListener
{
  FailoverSubscriptionManager & subscriptionManager;
  Recorder & recorder;


  Listener ( FailoverSubscriptionManager& subs, 
             Recorder & recorder
           );

  void shutdown() { recorder.report(); }
  void parse_message ( std::string const & msg );
  
  virtual void received ( Message & message );
};





Listener::Listener ( FailoverSubscriptionManager & s, Recorder & r ) : 
  subscriptionManager(s),
  recorder(r)
{
}





void 
Listener::received ( Message & message ) 
{
  std::cerr << "Listener received: " << message.getData() << std::endl;
  if (message.getData() == "That's all, folks!") 
  {
    std::cout << "Shutting down listener for " << message.getDestination()
              << std::endl;
    subscriptionManager.cancel(message.getDestination());

    shutdown();
  }
  else
  {
    parse_message ( message.getData() );
  }
}





void
Listener::parse_message ( const std::string & msg )
{
  int msg_number;
  if(1 != sscanf ( msg.c_str(), "%d", & msg_number ) ) 
  {
    std::cerr << "Listener::parse_message error: Can't read message number from this message: |" << msg_number << "|\n";
    return;
  }
  recorder.received ( msg_number );
}






int 
main ( int argc, char ** argv ) 
{
  string program_name = "LISTENER";

  if ( argc < 3 )
  {
    std::cerr << "Usage: ./listener host cluster_port_file_name\n";
    std::cerr << "i.e. for host: 127.0.0.1\n";
    exit(1);
  }

  char const * host = argv[1];
  int port = atoi(argv[2]);

  FailoverConnection connection;
  FailoverSession    * session;
  Recorder recorder;

  connection.name = program_name; 

  connection.open ( host, port );
  session = connection.newSession();
  session->name = program_name; 

  FailoverSubscriptionManager subscriptions ( session );
  subscriptions.name = program_name;
  Listener listener ( subscriptions, recorder );
  subscriptions.subscribe ( listener, "message_queue" );
  subscriptions.run ( );

  connection.close();

  return 1;   
}




