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

#include <string>
#include <iostream>
#include <sstream>
#include <vector>

#include <boost/assign.hpp>

#include "qpid/framing/Uuid.h"

#include <ForkedBroker.h>





using namespace std;
using boost::assign::list_of;
using namespace qpid::framing;



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
        push_back(new child ( name, pid, type ));
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
            if ( verbosity > 0 )
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
        vector<child *>::iterator i;
        for ( i = begin(); i != end(); ++ i )
            if ( (COMPLETED == (*i)->status) && (0 != (*i)->retval) )
                return (*i)->retval;
      
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


    /* 
       Only call this if you already know there is at least 
       one child still running.  Supply a time in seconds.
       If it has been at least that long since a shild stopped
       running, we judge the system to have hung.
    */
    int
    hanging ( int hangTime )
    {
        struct timeval now,
                       duration;
        gettimeofday ( &now, 0 );

        int how_many_hanging = 0;

        vector<child *>::iterator i;
        for ( i = begin(); i != end(); ++ i )
        {
            timersub ( & now, &((*i)->startTime), & duration );

            if ( (COMPLETED != (*i)->status)     // child isn't done running
                  &&
                 ( duration.tv_sec >= hangTime ) // it's been too long
               )
            {
                std::cerr << "Child of type " 
                          << (*i)->type 
                          << " hanging.   "
                          << "PID is "
                          << (*i)->pid
                          << endl;
                ++ how_many_hanging;
            }
        }
        
        return how_many_hanging;
    }
    

    int verbosity;
};



children allMyChildren;




void 
childExit ( int signalNumber ) 
{
    signalNumber ++;  // Now maybe the compiler willleave me alone?
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





void
startNewBroker ( brokerVector & brokers,
                 char const * srcRoot,
                 char const * moduleDir,
                 string const clusterName,
                 int verbosity ) 
{
    static int brokerId = 0;
    stringstream path, prefix, module;
    module << moduleDir << "/cluster.so";
    path << srcRoot << "/qpidd";
    prefix << "soak-" << brokerId++;

    std::vector<std::string> argv = 
        list_of<string> ("qpidd")
                        ("--no-module-dir")
                        ("--load-module=cluster.so")
                        ("--cluster-name")
                        (clusterName)
                        ("--auth=no")
                        ("--no-data-dir")
                        ("--mgmt-enable=no")
                        ("--log-prefix")
                        (prefix.str())
                        ("--log-to-file")
                        ("/tmp/qpidd.log");

    ForkedBroker * broker = new ForkedBroker ( argv );

    if ( verbosity > 0 )
      std::cerr << "new broker created: pid == " << broker->getPID() << endl;
    brokers.push_back ( broker );
}





bool
killFrontBroker ( brokerVector & brokers, int verbosity )
{
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
        catch ( ... ) { }
}





pid_t
runDeclareQueuesClient ( brokerVector brokers, 
                            char const *  host,
                            char const *  path,
                            int verbosity
                          ) 
{
    string name("declareQueues");
    int port = brokers[0]->getPort ( );

    if ( verbosity > 0 )
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
                         char const *  host,
                         char const *  receiverPath,
                         char const *  reportFrequency,
                         int verbosity
                       ) 
{
    string name("receiver");
    int port = brokers[0]->getPort ( );

    if ( verbosity > 0 )
        cout << "startReceivingClient: port " << port << endl;
    char portStr[100];
    char verbosityStr[100];
    sprintf(portStr, "%d", port);
    sprintf(verbosityStr, "%d", verbosity);


    vector<const char*> argv;
    argv.push_back ( "resumingReceiver" );
    argv.push_back ( host );
    argv.push_back ( portStr );
    argv.push_back ( reportFrequency );
    argv.push_back ( verbosityStr );
    argv.push_back ( 0 );

    pid_t pid = fork();

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
                       char const *  host,
                       char const *  senderPath,
                       char const *  nMessages,
                       char const *  reportFrequency,
                       int verbosity
                     ) 
{
    string name("sender");
    int port = brokers[0]->getPort ( );

    if ( verbosity )
        cout << "startSenderClient: port " << port << endl;
    char portStr[100];
    char verbosityStr[100];

    sprintf ( portStr,      "%d", port);
    sprintf ( verbosityStr, "%d", verbosity);

    vector<const char*> argv;
    argv.push_back ( "replayingSender" );
    argv.push_back ( host );
    argv.push_back ( portStr );
    argv.push_back ( nMessages );
    argv.push_back ( reportFrequency );
    argv.push_back ( verbosityStr );
    argv.push_back ( 0 );

    pid_t pid = fork();

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


int
main ( int argc, char const ** argv ) 
{    
    if ( argc < 9 ) {
        cerr << "Usage: failoverSoak srcRoot moduleDir host senderPath receiverPath nMessages verbosity\n";
        cerr << "    ( argc was " << argc << " )\n";
        return BAD_ARGS;
    }

    signal ( SIGCHLD, childExit );

    char const * srcRoot            = argv[1];
    char const * moduleDir          = argv[2];
    char const * host               = argv[3];
    char const * declareQueuesPath  = argv[4];
    char const * senderPath         = argv[5];
    char const * receiverPath       = argv[6];
    char const * nMessages          = argv[7];
    char const * reportFrequency    = argv[8];
    int          verbosity          = atoi(argv[9]);

    int maxBrokers = 50;

    allMyChildren.verbosity = verbosity;

    string clusterName;

    srand ( getpid() );

    makeClusterName ( clusterName );

    brokerVector brokers;

    if ( verbosity > 0 )
        cout << "Starting initial cluster...\n";

    int nBrokers = 3;
    for ( int i = 0; i < nBrokers; ++ i ) {
        startNewBroker ( brokers,
                         srcRoot,
                         moduleDir, 
                         clusterName,
                         verbosity ); 
    }


    if ( verbosity > 0 )
        printBrokers ( brokers );

     // Run the declareQueues child.
     int childStatus;
     pid_t dqClientPid = 
     runDeclareQueuesClient ( brokers, host, declareQueuesPath, verbosity );
     if ( -1 == dqClientPid ) {
         cerr << "ERROR: START_DECLARE_1 END_OF_TEST\n";
         return CANT_FORK_DQ;
     }

     // Don't continue until declareQueues is finished.
     pid_t retval = waitpid ( dqClientPid, & childStatus, 0);
     if ( retval != dqClientPid) {
         cerr << "ERROR: START_DECLARE_2 END_OF_TEST\n";
         return DQ_FAILED;
     }
     allMyChildren.exited ( dqClientPid, childStatus );



     // Start the receiving client.
     pid_t receivingClientPid =
     startReceivingClient ( brokers, 
                              host, 
                              receiverPath,
                              reportFrequency,
                              verbosity );
     if ( -1 == receivingClientPid ) {
         cerr << "ERROR: START_RECEIVER END_OF_TEST\n";
         return CANT_FORK_RECEIVER;
     }


     // Start the sending client.
     pid_t sendingClientPid = 
     startSendingClient ( brokers, 
                            host, 
                            senderPath, 
                            nMessages,
                            reportFrequency,
                            verbosity );
     if ( -1 == sendingClientPid ) {
         cerr << "ERROR: START_SENDER END_OF_TEST\n";
         return CANT_FORK_SENDER;
     }


     int minSleep = 3,
         maxSleep = 6;


     for ( int totalBrokers = 3; 
           totalBrokers < maxBrokers; 
           ++ totalBrokers 
         ) 
     {
         if ( verbosity > 0 )
             cout << totalBrokers << " brokers have been added to the cluster.\n\n\n";

         // Sleep for a while. -------------------------
         int sleepyTime = mrand ( minSleep, maxSleep );
         if ( verbosity > 0 )
             cout << "Sleeping for " << sleepyTime << " seconds.\n";
         sleep ( sleepyTime );

         // Kill the oldest broker. --------------------------
         if ( ! killFrontBroker ( brokers, verbosity ) )
         {
           allMyChildren.killEverybody();
           std::cerr << "ERROR: BROKER END_OF_TEST\n";
           return ERROR_KILLING_BROKER;
         }

         // Sleep for a while. -------------------------
         sleepyTime = mrand ( minSleep, maxSleep );
         if ( verbosity > 0 )
             cerr << "Sleeping for " << sleepyTime << " seconds.\n";
         sleep ( sleepyTime );

         // Start a new broker. --------------------------
         if ( verbosity > 0 )
             cout << "Starting new broker.\n\n";

         startNewBroker ( brokers,
                          srcRoot,
                          moduleDir, 
                          clusterName,
                          verbosity ); 
       
         if ( verbosity > 0 )
             printBrokers ( brokers );
       
         // If all children have exited, quit.
         int unfinished = allMyChildren.unfinished();
         if ( ! unfinished ) {
             killAllBrokers ( brokers, 10 );

             if ( verbosity > 0 )
                 cout << "failoverSoak: all children have exited.\n";
           int retval = allMyChildren.checkChildren();
           if ( verbosity > 0 )
             std::cerr << "failoverSoak: checkChildren: " << retval << endl;

           if ( retval )
           {
               std::cerr << "ERROR: CLIENT END_OF_TEST\n";
               return ERROR_ON_CHILD;
           }
           else
           {
               std::cerr << "SUCCESSFUL END_OF_TEST\n";
               return HUNKY_DORY;
           }
         }

         // Even if some are still running, if there's an error, quit.
         if ( allMyChildren.checkChildren() )
         {
             if ( verbosity > 0 )
                 cout << "failoverSoak: error on child.\n";
             allMyChildren.killEverybody();
             killAllBrokers ( brokers, 10 );
             std::cerr << "ERROR: CLIENT END_OF_TEST\n";
             return ERROR_ON_CHILD;
         }

         // If one is hanging, quit.
         if ( allMyChildren.hanging ( 120 ) )
         {
             /*
              * Don't kill any processes.  Leave alive for questioning.
              * */
             std::cerr << "ERROR: HANGING END_OF_TEST\n";
             return HANGING;
         }

         if ( verbosity > 0 ) {
           std::cerr << "------- next kill-broker loop --------\n";
           allMyChildren.print();
         }
     }

     retval = allMyChildren.checkChildren();
     if ( verbosity > 0 )
       std::cerr << "failoverSoak: checkChildren: " << retval << endl;

     if ( verbosity > 0 )
         cout << "failoverSoak: maxBrokers reached.\n";

     allMyChildren.killEverybody();
     killAllBrokers ( brokers, 10 );

     std::cerr << "SUCCESSFUL END_OF_TEST\n";

     return retval ? ERROR_ON_CHILD : HUNKY_DORY;
}



