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



struct child
{
    child ( string & name, pid_t pid ) 
        : name(name), pid(pid), retval(-999), status(RUNNING)
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


    string name;
    pid_t pid;
    int   retval;
    childStatus status;
    struct timeval startTime,
                   stopTime;
};




struct children : public vector<child *>
{ 
    void
    add ( string & name, pid_t pid )
    {
        push_back(new child ( name, pid ));
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
    bool
    hanging ( int hangTime )
    {
        struct timeval now,
                       duration;
        gettimeofday ( &now, 0 );

        vector<child *>::iterator i;
        for ( i = begin(); i != end(); ++ i )
        {
            timersub ( & now, &((*i)->startTime), & duration );
            if ( duration.tv_sec >= hangTime )
                return true;
        }
        
        return false;
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
                 string const clusterName ) 
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

    brokers.push_back ( new ForkedBroker ( argv ) );
}





void
killFrontBroker ( brokerVector & brokers, int verbosity )
{
    if ( verbosity > 0 )
        cout << "killFrontBroker pid: " << brokers[0]->getPID() << " on port " << brokers[0]->getPort() << endl;
    try { brokers[0]->kill(9); }
    catch ( const exception& error ) {
        if ( verbosity > 0 )
            cout << "error killing broker: " << error.what() << endl;
    }
    delete brokers[0];
    brokers.erase ( brokers.begin() );
}





void
killAllBrokers ( brokerVector & brokers )
{
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
        perror ( "error executing dq: " );
        return 0;
    }

    allMyChildren.add ( name, pid );
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

    allMyChildren.add ( name, pid );
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

    allMyChildren.add ( name, pid );
    return pid;
}



#define HUNKY_DORY          0
#define BAD_ARGS            1
#define CANT_FORK_DQ        2
#define CANT_FORK_RECEIVER  3
#define DQ_FAILED           4
#define ERROR_ON_CHILD      5
#define HANGING             6


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
                         clusterName ); 
    }


    if ( verbosity > 0 )
        printBrokers ( brokers );

     // Run the declareQueues child.
     int childStatus;
     pid_t dqClientPid = 
     runDeclareQueuesClient ( brokers, host, declareQueuesPath, verbosity );
     if ( -1 == dqClientPid ) {
         cerr << "failoverSoak error: Couldn't fork declareQueues.\n";
         return CANT_FORK_DQ;
     }

     // Don't continue until declareQueues is finished.
     pid_t retval = waitpid ( dqClientPid, & childStatus, 0);
     if ( retval != dqClientPid) {
         cerr << "failoverSoak error:  waitpid on declareQueues returned value " <<  retval << endl;
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
         cerr << "failoverSoak error: Couldn't fork receiver.\n";
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
         cerr << "failoverSoak error: Couldn't fork sender.\n";
         return CANT_FORK_RECEIVER;
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
         killFrontBroker ( brokers, verbosity );

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
                          clusterName ); 
       
         if ( verbosity > 0 )
             printBrokers ( brokers );
       
         // If all children have exited, quit.
         int unfinished = allMyChildren.unfinished();
         if ( ! unfinished ) {
             killAllBrokers ( brokers );

             if ( verbosity > 0 )
                 cout << "failoverSoak: all children have exited.\n";
           int retval = allMyChildren.checkChildren();
           if ( verbosity > 0 )
             std::cerr << "failoverSoak: checkChildren: " << retval << endl;
           return retval ? ERROR_ON_CHILD : HUNKY_DORY;
         }

         // Even if some are still running, if there's an error, quit.
         if ( allMyChildren.checkChildren() )
         {
             if ( verbosity > 0 )
                 cout << "failoverSoak: error on child.\n";
             allMyChildren.killEverybody();
             killAllBrokers ( brokers );
             return ERROR_ON_CHILD;
         }

         // If one is hanging, quit.
         if ( allMyChildren.hanging ( 120 ) )
         {
             if ( verbosity > 0 )
                 cout << "failoverSoak: child hanging.\n";
             allMyChildren.killEverybody();
             killAllBrokers ( brokers );
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
     killAllBrokers ( brokers );

     return retval ? ERROR_ON_CHILD : HUNKY_DORY;
}



