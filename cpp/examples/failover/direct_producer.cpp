#include <qpid/client/FailoverConnection.h>
#include <qpid/client/Session.h>
#include <qpid/client/AsyncSession.h>
#include <qpid/client/Message.h>


#include <unistd.h>
#include <cstdlib>
#include <iostream>
#include <fstream>

#include <sstream>

using namespace qpid::client;
using namespace qpid::framing;

using namespace std;




int 
main ( int argc, char ** argv) 
{
    try {
        struct timeval broker_killed_time     = {0,0};
        struct timeval failover_complete_time = {0,0};
        struct timeval duration               = {0,0};


        if ( argc < 3 )
        {
            std::cerr << "Usage: ./direct_producer host cluster_port_file_name\n";
            std::cerr << "i.e. for host: 127.0.0.1\n";
            exit(1);
        }

        char const * host = argv[1];
        int  port = atoi(argv[2]);
        char const * broker_to_kill = 0;

        if ( argc > 3 )
        {
            broker_to_kill = argv[3];
            std::cerr << "main:  Broker marked for death is process ID " 
                      << broker_to_kill
                      << endl;
        }
        else
        {
            std::cerr << "PRODUCER main: there is no broker to kill.\n";
        }

        FailoverConnection connection;
        FailoverSession    * session;
        Message message;

        string program_name = "PRODUCER";


        connection.failoverCompleteTime = & failover_complete_time;
        connection.name = program_name;
        connection.open ( host, port );

        session = connection.newSession();
        session->name    = program_name;

        int send_this_many = 30,
            messages_sent  = 0;

        while ( messages_sent < send_this_many )
        {
            if ( (messages_sent == 13) && broker_to_kill )
            {
                char command[1000];
                std::cerr << program_name << " killing broker " << broker_to_kill << ".\n";
                sprintf(command, "kill -9 %s", broker_to_kill);
                system ( command );
                gettimeofday ( & broker_killed_time, 0 );
            }

            message.getDeliveryProperties().setRoutingKey("routing_key"); 

            std::cerr << "sending message " 
                      << messages_sent
                      << " of " 
                      << send_this_many 
                      << ".\n";

            stringstream message_data;
            message_data << messages_sent;
            message.setData(message_data.str());

            try
            {
                /* MICK FIXME
                   session.messageTransfer ( arg::content=message,  
                   arg::destination="amq.direct"
                   ); */
                session->messageTransfer ( "amq.direct",
                                           1,
                                           0,
                                           message
                );
            }
            catch ( const std::exception& error) 
            {
                cerr << program_name << " exception: " << error.what() << endl;
            }

            sleep ( 1 );
            ++ messages_sent;
        }

        message.setData ( "That's all, folks!" );

        /* MICK FIXME
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

        // This will be incorrect if you killed more than one...
        if ( broker_to_kill )
        {
            timersub ( & failover_complete_time, 
                       & broker_killed_time, 
                       & duration 
            );
            fprintf ( stderr, 
                      "Failover time: %ld.%.6ld\n",
                      duration.tv_sec,
                      duration.tv_usec
            );
        }
        return 0;  

    } catch(const std::exception& error) {
        std::cout << error.what() << std::endl;
    }
    return 1;
}
