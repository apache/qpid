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
#include "Daemon.h"
#include "Exception.h"

#include <boost/iostreams/stream.hpp>
#include <boost/iostreams/device/file_descriptor.hpp>

#include <sstream>

#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

namespace qpid {
namespace broker {

using namespace std;
typedef boost::iostreams::stream<boost::iostreams::file_descriptor> fdstream;

namespace {
/** Throw an exception containing msg and strerror if throwIf is true.
 * Name is supposed to be reminiscent of perror().
 */
void terror(bool throwIf, const string& msg, int errNo=errno) {
    if (throwIf)
        throw Exception(msg + (errNo? ": "+strError(errNo) : string(".")));
}


struct LockFile : public fdstream {

    LockFile(const std::string& path_, bool create)
        : path(path_), fd(-1), created(create)
    {
        errno = 0;
        int flags=create ? O_WRONLY|O_CREAT|O_NOFOLLOW : O_RDWR;
        fd = ::open(path.c_str(), flags, 0644);
        terror(fd < 0,"Cannot open "+path);
        terror(::lockf(fd, F_TLOCK, 0) < 0, "Cannot lock "+path);
        open(boost::iostreams::file_descriptor(fd));
    }

    ~LockFile() {
        if (fd >= 0) {
            ::lockf(fd, F_ULOCK, 0);
            close();
        }
    }

    std::string path;
    int fd;
    bool created;
};

} // namespace

Daemon::Daemon() {
    pid = -1;
    pipeFds[0] = pipeFds[1] = -1;
}

string Daemon::dir() {
    return (getuid() == 0 ? "/var/run" : "/tmp");
}

string Daemon::pidFile(uint16_t port) {
    ostringstream path;
    path << dir() << "/qpidd." << port << ".pid";
    return path.str();
}

void Daemon::fork()
{
    terror(pipe(pipeFds) < 0, "Can't create pipe");
    terror((pid = ::fork()) < 0, "Daemon fork failed");
    if (pid == 0) {             // Child
        try {
            // File descriptors
            terror(::close(pipeFds[0])<0, "Cannot close read pipe");
            terror(::close(0)<0, "Cannot close stdin");
            terror(::close(1)<0, "Cannot close stdout");
            terror(::close(2)<0, "Cannot close stderr");
            int fd=::open("/dev/null",O_RDWR); // stdin
            terror(fd != 0, "Cannot re-open stdin");
            terror(::dup(fd)<0, "Cannot re-open stdout");
            terror(::dup(fd)<0, "Cannot re-open stderror");

            // Misc
            terror(setsid()<0, "Cannot set session ID");
            terror(chdir(dir().c_str()) < 0, "Cannot change directory to "+dir());
            umask(027);

            // Child behavior
            child();
        }
        catch (const exception& e) {
            fdstream pipe(pipeFds[1]);
            assert(pipe.is_open());
            pipe << "0 " << e.what() << endl;
        }
    }
    else {                      // Parent
        close(pipeFds[1]);      // Write side.
        parent();
    }
}

Daemon::~Daemon() {
    if (!lockFile.empty()) 
        unlink(lockFile.c_str());
}

uint16_t Daemon::wait(int timeout) {            // parent waits for child.
    errno = 0;                  
    struct timeval tv;
    tv.tv_sec = timeout;
    tv.tv_usec = 0;

    fd_set fds;
    FD_ZERO(&fds);
    FD_SET(pipeFds[0], &fds);
    terror(1 != select(FD_SETSIZE, &fds, 0, 0, &tv), "No response from daemon process");

    fdstream pipe(pipeFds[0]);
    uint16_t value = 0;
    pipe >> value >> skipws;
    if (value == 0) {
        string errmsg;
        getline(pipe, errmsg);
        throw Exception("Daemon startup failed"+ (errmsg.empty() ? string(".") : ": " + errmsg));
    }
    return value;
}

void Daemon::ready(uint16_t port) { // child
    lockFile = pidFile(port);
    LockFile lf(lockFile, true);
    lf << getpid() << endl;
    if (lf.fail())
        throw Exception("Cannot write lock file "+lockFile);
    fdstream pipe(pipeFds[1]);
    pipe << port << endl;;
}

pid_t Daemon::getPid(uint16_t port) {
    string name = pidFile(port);
    LockFile lockFile(name, false);
    pid_t pid;
    lockFile >> pid;
    if (lockFile.fail())
        throw Exception("Cannot read lock file "+name);
    if (kill(pid, 0) < 0 && errno != EPERM) {
        unlink(name.c_str());
        throw Exception("Removing stale lock file "+name);
    }
    return pid;
}


}} // namespace qpid::broker
