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

/**
 * Use socketpair to test the poller
 */

#include "qpid/sys/Poller.h"

#include <string>
#include <iostream>
#include <memory>
#include <exception>

#include <assert.h>

#include <sys/types.h>
#include <sys/socket.h>
#include <fcntl.h>
#include <unistd.h>
#include <errno.h>

using namespace std;
using namespace qpid::sys;

int writeALot(int fd, const string& s) {
    int bytesWritten = 0;
    do {
        errno = 0;
        int lastWrite = ::write(fd, s.c_str(), s.size());
        if ( lastWrite >= 0) {
            bytesWritten += lastWrite;
        } 
    } while (errno != EAGAIN);
    return bytesWritten;
}

int readALot(int fd) {
    int bytesRead = 0;
    char buf[1024];
    
    do {
        errno = 0;
        int lastRead = ::read(fd, buf, sizeof(buf));
        if ( lastRead >= 0) {
            bytesRead += lastRead;
        } 
    } while (errno != EAGAIN);
    return bytesRead;
}

int main(int argc, char** argv)
{
    try 
    {
        int sv[2];
        int rc = ::socketpair(AF_LOCAL, SOCK_STREAM, 0, sv);
        assert(rc >= 0);
        
        // Set non-blocking
        rc = ::fcntl(sv[0], F_SETFL, O_NONBLOCK);
        assert(rc >= 0);

        rc = ::fcntl(sv[1], F_SETFL, O_NONBLOCK);
        assert(rc >= 0);
        
        // Make up a large string
        string testString = "This is only a test ... 1,2,3,4,5,6,7,8,9,10;";
        for (int i = 0; i < 6; i++)
            testString += testString;

        // Read as much as we can from socket 0
        int bytesRead = readALot(sv[0]);
        assert(bytesRead == 0);
        cout << "Read(0): " << bytesRead << " bytes\n";

        // Write as much as we can to socket 0
        int bytesWritten = writeALot(sv[0], testString);
        cout << "Wrote(0): " << bytesWritten << " bytes\n";
        
        // Read as much as we can from socket 1
        bytesRead = readALot(sv[1]);
        assert(bytesRead == bytesWritten);
        cout << "Read(1): " << bytesRead << " bytes\n";

        auto_ptr<Poller> poller(new Poller);
        
        PollerHandle h0(sv[0]);
        PollerHandle h1(sv[1]);
        
        poller->addFd(h0, Poller::INOUT);
        
        // Wait for 500ms - h0 should be writable
        Poller::Event event = poller->wait();
        assert(event.handle == &h0);
        assert(event.dir == Poller::OUT);
        
        // Write as much as we can to socket 0
        bytesWritten = writeALot(sv[0], testString);
        cout << "Wrote(0): " << bytesWritten << " bytes\n";
        
        // Wait for 500ms - h0 no longer writable
        poller->rearmFd(h0);
        event = poller->wait(500000000);
        assert(event.handle == 0);

        // Test we can read it all now
        poller->addFd(h1, Poller::INOUT);
        event = poller->wait();
        assert(event.handle == &h1);
        assert(event.dir == Poller::INOUT);
        
        bytesRead = readALot(sv[1]);
        assert(bytesRead == bytesWritten);
        cout << "Read(1): " << bytesRead << " bytes\n";
        
        // At this point h1 should have been disabled from the poller
        // (as it was just returned) and h0 can write again
        event = poller->wait();
        assert(event.handle == &h0);
        assert(event.dir == Poller::OUT);    

        // Now both the handles should be disabled
        event = poller->wait(500000000);
        assert(event.handle == 0);
        
        // Test shutdown
        poller->shutdown();
        event = poller->wait();
        assert(event.handle == 0);
        assert(event.dir == Poller::SHUTDOWN);

        event = poller->wait();
        assert(event.handle == 0);
        assert(event.dir == Poller::SHUTDOWN);

        poller->delFd(h1);
        poller->delFd(h0);
        
        return 0;
    } catch (exception& e) {
        cout << "Caught exception  " << e.what() << "\n";
    }
}


