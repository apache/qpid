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

#include <sasl/sasl.h>

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif




using namespace std;
using boost::assign::list_of;
using namespace qpid::framing;
using namespace qpid::client;


namespace qpid {
namespace tests {

vector<pid_t> brokerPids;

typedef vector<ForkedBroker *> brokerVector;





int  runSilent    = 1;
int  newbiePort   = 0;


void
makeClusterName ( string & s ) {
    stringstream ss;
    ss << "authenticationSoakCluster_" << Uuid(true).str();
    s = ss.str();
}



void
startBroker ( brokerVector & brokers , int brokerNumber, string const & clusterName ) {
    stringstream prefix, clusterArg;
    prefix << "soak-" << brokerNumber;
    clusterArg << "--cluster-name=" << clusterName;

    std::vector<std::string> argv;

    argv.push_back ("../qpidd");
    argv.push_back ("--no-module-dir");
    argv.push_back ("--load-module=../.libs/cluster.so");
    argv.push_back (clusterArg.str());
    argv.push_back ("--cluster-username=zig");
    argv.push_back ("--cluster-password=zig");
    argv.push_back ("--cluster-mechanism=PLAIN");
    argv.push_back ("--sasl-config=./sasl_config");
    argv.push_back ("--auth=yes");
    argv.push_back ("--mgmt-enable=yes");
    argv.push_back ("--log-prefix");
    argv.push_back (prefix.str());
    argv.push_back ("--log-to-file");
    argv.push_back (prefix.str()+".log");
    argv.push_back ("TMP_DATA_DIR");

    ForkedBroker * newbie = new ForkedBroker (argv);
    newbiePort = newbie->getPort();
    brokers.push_back ( newbie );
}




bool
runPerftest ( bool hangTest ) {
    stringstream portSs;
    portSs << newbiePort;
    string portStr = portSs.str();
    char const *  path = "./qpid-perftest";

    vector<char const *> argv;
    argv.push_back ( "./qpid-perftest" );
    argv.push_back ( "-p" );
    argv.push_back ( portStr.c_str() );
    argv.push_back ( "--username" );
    argv.push_back ( "zig" );
    argv.push_back ( "--password" );
    argv.push_back ( "zig" );
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
        perror ( "error running qpid-perftest: " );
        return false;
    }
    else {
	if ( hangTest ) {
	    if ( ! runSilent )
	        cerr << "Pausing perftest " << pid << endl;
	    kill ( pid, 19 );
	}

        struct timeval startTime,
                       currentTime,
                       duration;

        gettimeofday ( & startTime, 0 );

        while ( 1 ) {
          sleep ( 2 );
          int status;
          int returned_pid = waitpid ( pid, &status, WNOHANG );
          if ( returned_pid == pid ) {
              int exit_status = WEXITSTATUS(status);
              if ( exit_status ) {
                  cerr << "qpid-perftest failed. exit_status was: " << exit_status << endl;
                return false;
              }
              else {
                return true; // qpid-perftest succeeded.
              }
          }
          else  {  // qpid-perftest has not yet completed.
              gettimeofday ( & currentTime, 0 );
              timersub ( & currentTime, & startTime, & duration );
              if ( duration.tv_sec > 60 ) {
                kill ( pid, 9 );
                cerr << "qpid-perftest pid " << pid << " hanging: killed.\n";
                return false;
              }
          }
        }
                       
    }
}



bool
allBrokersAreAlive ( brokerVector & brokers ) {
    for ( unsigned int i = 0; i < brokers.size(); ++ i )
        if ( ! brokers[i]->isRunning() ) 
	    return false;

    return true;
}





void
killAllBrokers ( brokerVector & brokers ) {
    for ( unsigned int i = 0; i < brokers.size(); ++ i ) {
        brokers[i]->kill ( 9 );
    }
}




void
killOneBroker ( brokerVector & brokers ) {
    int doomedBroker = getpid() % brokers.size();
    cout << "Killing broker " << brokers[doomedBroker]->getPID() << endl;
    brokers[doomedBroker]->kill ( 9 );
    sleep ( 2 );
}




}} // namespace qpid::tests

using namespace qpid::tests;



/*
 *  Please note that this test has self-test capability.
 *  It is intended to detect 
 *    1. perftest hangs.
 *    2. broker deaths
 *  Both of these condtions can be forced when running manually
 *  to ensure that the test really does detect them.
 *  See command-line arguments 3 and 4.
 */
int
main ( int argc, char ** argv )
{
    // I need the SASL_PATH_TYPE_CONFIG feature, which did not appear until SASL 2.1.22
#if (SASL_VERSION_FULL < ((2<<16)|(1<<8)|22))
    cout << "Skipping SASL test, SASL version too low." << endl;
    return 0;
#endif

    int n_iterations = argc > 1 ? atoi(argv[1]) : 1;
        runSilent    = argc > 2 ? atoi(argv[2]) : 1;  // default to silent
    int killBroker   = argc > 3 ? atoi(argv[3]) : 0;  // Force the kill of one broker.
    int hangTest     = argc > 4 ? atoi(argv[4]) : 0;  // Force the first perftest to hang.
    int n_brokers = 3;
    brokerVector brokers;

    srand ( getpid() );
    string clusterName;
    makeClusterName ( clusterName );
    for ( int i = 0; i < n_brokers; ++ i ) {
        startBroker ( brokers, i, clusterName );
    }

    sleep ( 3 );

    /* Run all qpid-perftest iterations, and only then check for brokers
     * still being up.  If you just want a quick check for the failure 
     * mode in which a single iteration would kill all brokers except 
     * the client-connected one, just run it with the iterations arg
     * set to 1.
    */
    for ( int iteration = 0; iteration < n_iterations; ++ iteration ) {
        if ( ! runPerftest ( hangTest ) ) {
	    if ( ! runSilent )
	        cerr << "qpid-perftest " << iteration << " failed.\n";
            return 1;
        }
        if ( ! ( iteration % 10 ) ) {
	    if ( ! runSilent )
                cerr << "qpid-perftest " << iteration << " complete. -------------- \n";
        }
    }
    if ( ! runSilent )
        cerr << "\nqpid-perftest " << n_iterations << " iterations complete. -------------- \n\n";

    /* If the command-line tells us to kill a broker, do
     * it now.  Use this option to prove that this test
     * really can detect broker-deaths.
     */
    if ( killBroker ) {
        killOneBroker ( brokers );
    }

    if ( ! allBrokersAreAlive ( brokers ) ) {
        if ( ! runSilent ) 
            cerr << "not all brokers are alive.\n";
	killAllBrokers ( brokers );
        return 2;
    }

    killAllBrokers ( brokers );
    if ( ! runSilent )
        cout << "success.\n";

    return 0;
}



