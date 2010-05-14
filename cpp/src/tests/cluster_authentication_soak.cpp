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
#include <signal.h>
#include <fcntl.h>

#include <sys/wait.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/time.h>

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

vector<pid_t> brokerPids;

typedef vector<ForkedBroker *> brokerVector;





int newbiePort    = 0;




void
startBroker ( brokerVector & brokers ,
              int brokerNumber ) {
    stringstream portSS, prefix;
    prefix << "soak-" << brokerNumber;
    std::vector<std::string> argv;

    argv.push_back ("../qpidd");
    argv.push_back ("--no-module-dir");
    argv.push_back ("--load-module=../.libs/cluster.so");
    argv.push_back ("--cluster-name=micks_test_cluster");
    argv.push_back ("--cluster-username=guest");
    argv.push_back ("--cluster-password=guest");
    argv.push_back ("--cluster-mechanism=ANONYMOUS");
    argv.push_back ("TMP_DATA_DIR");
    argv.push_back ("--auth=yes");
    argv.push_back ("--mgmt-enable=yes");
    argv.push_back ("--log-prefix");
    argv.push_back (prefix.str());
    argv.push_back ("--log-to-file");
    argv.push_back (prefix.str()+".log");

    ForkedBroker * newbie = new ForkedBroker (argv);
    newbiePort = newbie->getPort();
    brokers.push_back ( newbie );
}




bool
runPerftest ( ) {
    stringstream portSs;
    portSs << newbiePort;

    char const *  path = "./perftest";

    vector<char const *> argv;
    argv.push_back ( "./perftest" );
    argv.push_back ( "-p" );
    argv.push_back ( portSs.str().c_str() );
    argv.push_back ( "--username" );
    argv.push_back ( "guest" );
    argv.push_back ( "--password" );
    argv.push_back ( "guest" );
    argv.push_back ( "--mechanism" );
    argv.push_back ( "DIGEST-MD5" );
    argv.push_back ( "--count" );
    argv.push_back ( "20000" );
    argv.push_back ( 0 );

    pid_t pid = fork();

    if ( ! pid ) {
        int i=open("/dev/null",O_RDWR);
        dup2 ( i, fileno(stdout) );
        dup2 ( i, fileno(stderr) );

        execv ( path, const_cast<char * const *>(&argv[0]) );
        // The exec failed: we are still in parent process.
        perror ( "error running perftest: " ); 
        return false;
    }
    else {
        struct timeval startTime,
                       currentTime,
                       duration;

        gettimeofday ( & startTime, 0 );

        while ( 1 ) {
          sleep ( 5 );
          int status;
          int returned_pid = waitpid ( pid, &status, WNOHANG );
          if ( returned_pid == pid ) {
              int exit_status = WEXITSTATUS(status);
              if ( exit_status ) {
                cerr << "Perftest failed. exit_status was: " << exit_status;
                return false;
              }
              else {
                return true; // perftest succeeded.
              }
          }
          else  {  // perftest has not yet completed. 
              gettimeofday ( & currentTime, 0 );
              timersub ( & currentTime, & startTime, & duration );
              if ( duration.tv_sec > 60 ) {
                kill ( pid, 9 );
                cerr << "Perftest pid " << pid << " hanging: killed.\n";
                return false;
              }
          }
        }
                       
    }
}



bool
allBrokersAreAlive ( brokerVector & brokers ) {
    for ( unsigned int i = 0; i < brokers.size(); ++ i ) {
        pid_t pid = brokers[i]->getPID();
        int status;
        int value;
        if ( (value = waitpid ( pid, &status, WNOHANG ) ) ) {
           return false; 
        }
    }

    return true;
}




void
killAllBrokers ( brokerVector & brokers ) {
    for ( unsigned int i = 0; i < brokers.size(); ++ i )
        brokers[i]->kill ( 9 );
}




}} // namespace qpid::tests

using namespace qpid::tests;



int
main ( int argc, char ** argv )
{
    int n_iterations = argc > 0 ? atoi(argv[1]) : 1;
    int n_brokers = 3;
    brokerVector brokers;

    for ( int i = 0; i < n_brokers; ++ i ) {
        startBroker ( brokers, i );
    }

    sleep ( 3 );

    /* Run all perftest iterations, and only then check for brokers 
     * still being up.  If you just want a quick check for the failure 
     * mode in which a single iteration would kill all brokers except 
     * the client-connected one, just run it with the iterations arg
     * set to 1.
    */
    for ( int iteration = 0; iteration < n_iterations; ++ iteration ) {
        if ( ! runPerftest ( ) ) {
            cerr << "Perftest " << iteration << " failed.\n";
            return 1;
        }
        if ( ! ( iteration % 10 ) ) {
            cerr << "perftest " << iteration << " complete. -------------- \n";
        }
    }
    cerr << "\nperftest " << n_iterations << " iterations complete. -------------- \n\n";

    if ( ! allBrokersAreAlive ( brokers ) ) {
        cerr << "not all brokers are alive.\n";
        return 2;
    }

    killAllBrokers ( brokers );
    return 0;
}



