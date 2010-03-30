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


#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/wait.h>
#include <sys/time.h>
#include <string.h>
#include <sys/types.h>
#include <signal.h>

#include <string>
#include <iostream>
#include <sstream>
#include <vector>

#include <boost/assign.hpp>

#include "qpid/framing/Uuid.h"

#include <ForkedBroker.h>
#include <qpid/client/Connection.h>





using namespace std;
using boost::assign::list_of;
using namespace qpid::framing;
using namespace qpid::client;


namespace qpid {
namespace tests {

vector<pid_t> pids;

typedef vector<ForkedBroker *> brokerVector;

typedef enum
{
    NO_STATUS,
    RUNNING,
    COMPLETED
}
childStatus;


typedef enum
{
    NO_TYPE,
    DECLARING_CLIENT,
    SENDING_CLIENT,
    RECEIVING_CLIENT
}
childType;


ostream& operator<< ( ostream& os, const childType& ct ) {
  switch ( ct ) {
      case DECLARING_CLIENT:  os << "Declaring Client"; break;
      case SENDING_CLIENT:    os << "Sending Client";   break;
      case RECEIVING_CLIENT:  os << "Receiving Client"; break;
      default:                os << "No Client";        break;
  }

  return os;
}




struct child
{
    child ( string & name, pid_t pid, childType type )
        : name(name), pid(pid), retval(-999), status(RUNNING), type(type)
    {
        gettimeofday ( & startTime, 0 );
    }


    void
    done ( int _retval )
    {
        retval = _retval;
        status = COMPLETED;
        gettimeofday ( & stopTime, 0 );
    }


    void
    setType ( childType t )
    {
        type = t;
    }


    string name;
    pid_t pid;
    int   retval;
    childStatus status;
    childType   type;
    struct timeval startTime,
                   stopTime;
};




struct children : public vector<child *>
{

    void
    add ( string & name, pid_t pid, childType type )
    {
        push_back ( new child ( name, pid, type ) );
    }


    child *
    get ( pid_t pid )
    {
        vector<child *>::iterator i;
        for ( i = begin(); i != end(); ++ i )
            if ( pid == (*i)->pid )
                return *i;

        return 0;
    }


    void
    exited ( pid_t pid, int retval  )
    {
        child * kid = get ( pid );
        if(! kid)
        {
            if ( verbosity > 1 )
            {
                cerr << "children::exited warning: Can't find child with pid "
                     << pid
                     << endl;
            }
            return;
        }

        kid->done ( retval );
    }


    int
    unfinished ( )
    {
        int count = 0;

        vector<child *>::iterator i;
        for ( i = begin(); i != end(); ++ i )
            if ( COMPLETED != (*i)->status )
                ++ count;

        return count;
    }


    int
    checkChildren ( )
    {
      for ( unsigned int i = 0; i < pids.size(); ++ i )
      {
        int pid = pids[i];
        int returned_pid;
        int status;

        child * kid = get ( pid );

        if ( kid->status != COMPLETED )
        {
          returned_pid = waitpid ( pid, &status, WNOHANG );

          if ( returned_pid == pid )
          {
            int exit_status = WEXITSTATUS(status);
            exited ( pid, exit_status );
            if ( exit_status )  // this is a child error.
              return exit_status;
          }
        }
      }

      return 0;
    }


    void
    killEverybody ( )
    {
        vector<child *>::iterator i;
        for ( i = begin(); i != end(); ++ i )
            kill ( (*i)->pid, 9 );
    }



    void
    print ( )
    {
        cout << "--- status of all children --------------\n";
        vector<child *>::iterator i;
        for ( i = begin(); i != end(); ++ i )
            cout << "child: " << (*i)->name
                 << "  status: " << (*i)->status
                 << endl;
        cout << "\n\n\n\n";
    }

    int verbosity;
};


children allMyChildren;


void
childExit ( int )
{
    int  childReturnCode;
    pid_t pid = waitpid ( 0, & childReturnCode, WNOHANG);

    if ( pid > 0 )
        allMyChildren.exited ( pid, childReturnCode );
}



int
mrand ( int maxDesiredVal ) {
    double zeroToOne = (double) rand() / (double) RAND_MAX;
    return (int) (zeroToOne * (double) maxDesiredVal);
}



int
mrand ( int minDesiredVal, int maxDesiredVal ) {
    int interval = maxDesiredVal - minDesiredVal;
    return minDesiredVal + mrand ( interval );
}



void
makeClusterName ( string & s ) {
    stringstream ss;
    ss << "soakTestCluster_" << Uuid(true).str();
    s = ss.str();
}





void
printBrokers ( brokerVector & brokers )
{
    cout << "Broker List ------------ size: " << brokers.size() << "\n";
    for ( brokerVector::iterator i = brokers.begin(); i != brokers.end(); ++ i) {
        cout << "pid: "
             << (*i)->getPID()
             << "   port: "
             << (*i)->getPort()
             << endl;
    }
    cout << "end Broker List ------------\n";
}




ForkedBroker * newbie = 0;
int newbie_port       = 0;



bool
wait_for_newbie ( )
{
  if ( ! newbie )
    return true;

  try
  {
    Connection connection;
    connection.open ( "127.0.0.1", newbie_port );
    connection.close();
    newbie = 0;  // He's no newbie anymore!
    return true;
  }
  catch ( const std::exception& error )
  {
    std::cerr << "wait_for_newbie error: "
              << error.what()
              << endl;
    return false;
  }
}

bool endsWith(const char* str, const char* suffix) {
    return (strlen(suffix) < strlen(str) && 0 == strcmp(str+strlen(str)-strlen(suffix), suffix));
}


void
startNewBroker ( brokerVector & brokers,
                 char const * moduleOrDir,
                 string const clusterName,
                 int verbosity,
                 int durable )
{
    static int brokerId = 0;
    stringstream path, prefix;
    prefix << "soak-" << brokerId;
    std::vector<std::string> argv = list_of<string>
        ("qpidd")
        ("--cluster-name")(clusterName)
        ("--auth=no")
        ("--mgmt-enable=no")
        ("--log-prefix")(prefix.str())
        ("--log-to-file")(prefix.str()+".log")
        ("--log-enable=info+")
        ("--log-enable=debug+:cluster")
        ("TMP_DATA_DIR");

    if (endsWith(moduleOrDir, "cluster.so")) {
        // Module path specified, load only that module.
        argv.push_back(string("--load-module=")+moduleOrDir);
        argv.push_back("--no-module-dir");
        if ( durable ) {
          std::cerr << "failover_soak warning: durable arg hass no effect.  Use \"dir\" option of \"moduleOrDir\".\n";
        }
    }
    else {
        // Module directory specified, load all modules in dir.
        argv.push_back(string("--module-dir=")+moduleOrDir);
    }

    newbie = new ForkedBroker (argv);
    newbie_port = newbie->getPort();
    ForkedBroker * broker = newbie;

    if ( verbosity > 0 )
      std::cerr << "new broker created: pid == "
                << broker->getPID()
                << " log-prefix == "
                << "soak-" << brokerId
                << endl;
    brokers.push_back ( broker );

    ++ brokerId;
}





bool
killFrontBroker ( brokerVector & brokers, int verbosity )
{
    cerr << "killFrontBroker: waiting for newbie sync...\n";
    if ( ! wait_for_newbie() )
      return false;
    cerr << "killFrontBroker: newbie synced.\n";

    if ( verbosity > 0 )
        cout << "killFrontBroker pid: " << brokers[0]->getPID() << " on port " << brokers[0]->getPort() << endl;
    try { brokers[0]->kill(9); }
    catch ( const exception& error ) {
        if ( verbosity > 0 )
        {
            cout << "error killing broker: "
                 << error.what()
                 << endl;
        }

        return false;
    }
    delete brokers[0];
    brokers.erase ( brokers.begin() );
    return true;
}





/*
 *  The optional delay is to avoid killing newbie brokers that have just
 *  been added and are still in the process of updating.  This causes
 *  spurious, test-generated errors that scare everybody.
 */
void
killAllBrokers ( brokerVector & brokers, int delay )
{
    if ( delay > 0 )
    {
        std::cerr << "Killing all brokers after delay of " << delay << endl;
        sleep ( delay );
    }

    for ( uint i = 0; i < brokers.size(); ++ i )
        try { brokers[i]->kill(9); }
        catch ( const exception& error )
        {
          std::cerr << "killAllBrokers Warning: exception during kill on broker "
                    << i
                    << " "
                    << error.what()
                    << endl;
        }
}





pid_t
runDeclareQueuesClient ( brokerVector brokers,
                            char const *  host,
                            char const *  path,
                            int verbosity,
                            int durable,
                            char const * queue_prefix,
                            int n_queues
                          )
{
    string name("declareQueues");
    int port = brokers[0]->getPort ( );

    if ( verbosity > 1 )
        cout << "startDeclareQueuesClient: host:  "
             << host
             << "  port: "
             << port
             << endl;
    stringstream portSs;
    portSs << port;

    vector<const char*> argv;
    argv.push_back ( "declareQueues" );
    argv.push_back ( host );
    argv.push_back ( portSs.str().c_str() );
    if ( durable )
      argv.push_back ( "1" );
    else
      argv.push_back ( "0" );

    argv.push_back ( queue_prefix );

    char n_queues_str[20];
    sprintf ( n_queues_str, "%d", n_queues );
    argv.push_back ( n_queues_str );

    argv.push_back ( 0 );
    pid_t pid = fork();

    if ( ! pid ) {
        execv ( path, const_cast<char * const *>(&argv[0]) );
        perror ( "error executing declareQueues: " );
        return 0;
    }

    allMyChildren.add ( name, pid, DECLARING_CLIENT );
    return pid;
}





pid_t
startReceivingClient ( brokerVector brokers,
                         char const * host,
                         char const * receiverPath,
                         char const * reportFrequency,
                         int verbosity,
                         char const * queue_name
                       )
{
    string name("receiver");
    int port = brokers[0]->getPort ( );

    if ( verbosity > 1 )
        cout << "startReceivingClient: port " << port << endl;

    // verbosity has to be > 1 to let clients talk.
    int client_verbosity = (verbosity > 1 ) ? 1 : 0;

    char portStr[100];
    char verbosityStr[100];
    sprintf(portStr, "%d", port);
    sprintf(verbosityStr, "%d", client_verbosity);


    vector<const char*> argv;
    argv.push_back ( "resumingReceiver" );
    argv.push_back ( host );
    argv.push_back ( portStr );
    argv.push_back ( reportFrequency );
    argv.push_back ( verbosityStr );
    argv.push_back ( queue_name );
    argv.push_back ( 0 );

    pid_t pid = fork();
    pids.push_back ( pid );

    if ( ! pid ) {
        execv ( receiverPath, const_cast<char * const *>(&argv[0]) );
        perror ( "error executing receiver: " );
        return 0;
    }

    allMyChildren.add ( name, pid, RECEIVING_CLIENT );
    return pid;
}





pid_t
startSendingClient ( brokerVector brokers,
                       char const * host,
                       char const * senderPath,
                       char const * nMessages,
                       char const * reportFrequency,
                       int verbosity,
                       int durability,
                       char const * queue_name
                     )
{
    string name("sender");
    int port = brokers[0]->getPort ( );

    if ( verbosity > 1)
        cout << "startSenderClient: port " << port << endl;
    char portStr[100];
    char verbosityStr[100];
    //
    // verbosity has to be > 1 to let clients talk.
    int client_verbosity = (verbosity > 1 ) ? 1 : 0;

    sprintf ( portStr,      "%d", port);
    sprintf ( verbosityStr, "%d", client_verbosity);

    vector<const char*> argv;
    argv.push_back ( "replayingSender" );
    argv.push_back ( host );
    argv.push_back ( portStr );
    argv.push_back ( nMessages );
    argv.push_back ( reportFrequency );
    argv.push_back ( verbosityStr );
    if ( durability )
      argv.push_back ( "1" );
    else
      argv.push_back ( "0" );
    argv.push_back ( queue_name );
    argv.push_back ( 0 );

    pid_t pid = fork();
    pids.push_back ( pid );

    if ( ! pid ) {
        execv ( senderPath, const_cast<char * const *>(&argv[0]) );
        perror ( "error executing sender: " );
        return 0;
    }

    allMyChildren.add ( name, pid, SENDING_CLIENT );
    return pid;
}



#define HUNKY_DORY            0
#define BAD_ARGS              1
#define CANT_FORK_DQ          2
#define CANT_FORK_RECEIVER    3
#define CANT_FORK_SENDER      4
#define DQ_FAILED             5
#define ERROR_ON_CHILD        6
#define HANGING               7
#define ERROR_KILLING_BROKER  8

}} // namespace qpid::tests

using namespace qpid::tests;

// If you want durability, use the "dir" option of "moduleOrDir" .
int
main ( int argc, char const ** argv )
{
  int brokerKills = 0;
    if ( argc != 11 ) {
        cerr << "Usage: "
             << argv[0]
             << "moduleOrDir declareQueuesPath senderPath receiverPath nMessages reportFrequency verbosity durable n_queues n_brokers"
             << endl;
        cerr << "\tverbosity is an integer, durable is 0 or 1\n";
        return BAD_ARGS;
    }
    signal ( SIGCHLD, childExit );

    int i = 1;
    char const * moduleOrDir        = argv[i++];
    char const * declareQueuesPath  = argv[i++];
    char const * senderPath         = argv[i++];
    char const * receiverPath       = argv[i++];
    char const * nMessages          = argv[i++];
    char const * reportFrequency    = argv[i++];
    int          verbosity          = atoi(argv[i++]);
    int          durable            = atoi(argv[i++]);
    int          n_queues           = atoi(argv[i++]);
    int          n_brokers          = atoi(argv[i++]);

    char const * host               = "127.0.0.1";

    allMyChildren.verbosity = verbosity;

    string clusterName;

    srand ( getpid() );

    makeClusterName ( clusterName );

    brokerVector brokers;

    if ( verbosity > 1 )
        cout << "Starting initial cluster...\n";

    for ( int i = 0; i < n_brokers; ++ i ) {
        startNewBroker ( brokers,
                         moduleOrDir,
                         clusterName,
                         verbosity,
                         durable );
    }


    if ( verbosity > 0 )
        printBrokers ( brokers );

     // Get prefix for each queue name.
     stringstream queue_prefix;
     queue_prefix << "failover_soak_" << getpid();


     // Run the declareQueues child.
     int childStatus;
     pid_t dqClientPid =
     runDeclareQueuesClient ( brokers, 
                              host, 
                              declareQueuesPath, 
                              verbosity, 
                              durable,
                              queue_prefix.str().c_str(),
                              n_queues
                            );
     if ( -1 == dqClientPid ) {
         cerr << "END_OF_TEST ERROR_START_DECLARE_1\n";
         return CANT_FORK_DQ;
     }

     // Don't continue until declareQueues is finished.
     pid_t retval = waitpid ( dqClientPid, & childStatus, 0);
     if ( retval != dqClientPid) {
         cerr << "END_OF_TEST ERROR_START_DECLARE_2\n";
         return DQ_FAILED;
     }
     allMyChildren.exited ( dqClientPid, childStatus );


     /*
       Start one receiving and one sending client for each queue.
     */
     for ( int i = 0; i < n_queues; ++ i ) {

         stringstream queue_name;
         queue_name << queue_prefix.str() << '_' << i;

         // Receiving client ---------------------------
         pid_t receivingClientPid =
         startReceivingClient ( brokers,
                                  host,
                                  receiverPath,
                                  reportFrequency,
                                  verbosity,
                                  queue_name.str().c_str() );
         if ( -1 == receivingClientPid ) {
             cerr << "END_OF_TEST ERROR_START_RECEIVER\n";
             return CANT_FORK_RECEIVER;
         }


         // Sending client ---------------------------
         pid_t sendingClientPid =
         startSendingClient ( brokers,
                                host,
                                senderPath,
                                nMessages,
                                reportFrequency,
                                verbosity,
                                durable,
                                queue_name.str().c_str() );
         if ( -1 == sendingClientPid ) {
             cerr << "END_OF_TEST ERROR_START_SENDER\n";
             return CANT_FORK_SENDER;
         }
     }


     int minSleep = 2,
         maxSleep = 6;

     int totalBrokers = n_brokers;

     int loop = 0;

     while ( 1 )
     {
         ++ loop;

         /*
         if ( verbosity > 1 )
           std::cerr << "------- loop " << loop << " --------\n";

         if ( verbosity > 0 )
             cout << totalBrokers << " brokers have been added to the cluster.\n\n\n";
             */

         // Sleep for a while. -------------------------
         int sleepyTime = mrand ( minSleep, maxSleep );
         sleep ( sleepyTime );

         int bullet = mrand ( 100 );
         if ( bullet >= 95 )
	 {
           fprintf ( stderr, "Killing oldest broker...\n" );

	   // Kill the oldest broker. --------------------------
	   if ( ! killFrontBroker ( brokers, verbosity ) )
	   {
	     allMyChildren.killEverybody();
	     killAllBrokers ( brokers, 5 );
	     std::cerr << "END_OF_TEST ERROR_BROKER\n";
	     return ERROR_KILLING_BROKER;
	   }
           ++ brokerKills;

	   // Start a new broker. --------------------------
	   if ( verbosity > 0 )
	       cout << "Starting new broker.\n\n";

	   startNewBroker ( brokers,
			    moduleOrDir,
			    clusterName,
			    verbosity,
			    durable );
	   ++ totalBrokers;
           printBrokers ( brokers );
           cerr << brokerKills << " brokers have been killed.\n\n\n";
	 }

         int retval = allMyChildren.checkChildren();
         if ( retval )
         {
             std::cerr << "END_OF_TEST ERROR_CLIENT\n";
             allMyChildren.killEverybody();
             killAllBrokers ( brokers, 5 );
             return ERROR_ON_CHILD;
         }

         // If all children have exited, quit.
         int unfinished = allMyChildren.unfinished();
         if ( unfinished == 0 ) {
             killAllBrokers ( brokers, 5 );

             if ( verbosity > 1 )
                 cout << "failoverSoak: all children have exited.\n";

             std::cerr << "END_OF_TEST SUCCESSFUL\n";
             return HUNKY_DORY;
         }

     }

     allMyChildren.killEverybody();
     killAllBrokers ( brokers, 5 );

     std::cerr << "END_OF_TEST SUCCESSFUL\n";

     return HUNKY_DORY;
}



