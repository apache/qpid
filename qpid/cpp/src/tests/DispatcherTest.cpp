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

#include "qpid/sys/Poller.h"
#include "qpid/sys/Dispatcher.h"
#include "qpid/sys/Thread.h"

#include <sys/types.h>
#include <sys/socket.h>
#include <fcntl.h>
#include <unistd.h>
#include <errno.h>

#include <iostream>
#include <boost/bind.hpp>

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
    char buf[10240];
    
    do {
        errno = 0;
        int lastRead = ::read(fd, buf, sizeof(buf));
        if ( lastRead >= 0) {
            bytesRead += lastRead;
        } 
    } while (errno != EAGAIN);
    return bytesRead;
}

int64_t writtenBytes = 0;
int64_t readBytes = 0;

void writer(DispatchHandle& h, int fd, const string& s) {
    writtenBytes += writeALot(fd, s);
    h.rewatch();
}

void reader(DispatchHandle& h, int fd) {
    readBytes += readALot(fd);
    h.rewatch();
}

int main(int argc, char** argv)
{
    // Create poller
    Poller::shared_ptr poller(new Poller);
    
    // Create dispatcher thread
    Dispatcher d(poller);
    Dispatcher d1(poller);
    //Dispatcher d2(poller);
    //Dispatcher d3(poller);
    Thread dt(d);
    Thread dt1(d1);
    //Thread dt2(d2);
    //Thread dt3(d3);

    // Setup sender and receiver
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
    for (int i = 0; i < 8; i++)
        testString += testString;

    DispatchHandle rh(sv[0], boost::bind(reader, _1, sv[0]), 0);
    DispatchHandle wh(sv[1], 0, boost::bind(writer, _1, sv[1], testString));    

    rh.watch(poller);
    wh.watch(poller);

    // wait 2 minutes then shutdown
    sleep(60);
    
    poller->shutdown();
    dt.join();
    dt1.join();
    //dt2.join();
    //dt3.join();

    cout << "Wrote: " << writtenBytes << "\n";
    cout << "Read: " << readBytes << "\n";
    
    return 0;
}
