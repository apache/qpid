/*
 *
 * Copyright (c) 2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
#include "Daemon.h"
#include "qpid/log/Statement.h"
#include "qpid/Exception.h"

#include <boost/iostreams/stream.hpp>
#include <boost/iostreams/device/file_descriptor.hpp>

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
void throwIf(bool condition, const string& msg, int errNo=errno) {
    if (condition)
        throw Exception(msg + (errNo? ": "+strError(errNo) : string(".")));
}


struct LockFile : public fdstream {

    LockFile(const std::string& path_, bool create)
        : path(path_), fd(-1), created(create)
    {
        errno = 0;
        int flags=create ? O_WRONLY|O_CREAT|O_NOFOLLOW : O_RDWR;
        fd = ::open(path.c_str(), flags, 0644);
        throwIf(fd < 0,"Cannot open "+path);
        throwIf(::lockf(fd, F_TLOCK, 0) < 0, "Cannot lock "+path);
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
    throwIf(pipe(pipeFds) < 0, "Can't create pipe");
    throwIf((pid = ::fork()) < 0, "Daemon fork failed");
    if (pid == 0) {             // Child
        try {
            QPID_LOG(debug, "Forked daemon child process");
            
            // File descriptors
            throwIf(::close(pipeFds[0])<0, "Cannot close read pipe");
            throwIf(::close(0)<0, "Cannot close stdin");
            throwIf(::close(1)<0, "Cannot close stdout");
            throwIf(::close(2)<0, "Cannot close stderr");
            int fd=::open("/dev/null",O_RDWR); // stdin
            throwIf(fd != 0, "Cannot re-open stdin");
            throwIf(::dup(fd)<0, "Cannot re-open stdout");
            throwIf(::dup(fd)<0, "Cannot re-open stderror");

            // Misc
            throwIf(setsid()<0, "Cannot set session ID");
            throwIf(chdir(dir().c_str()) < 0, "Cannot change directory to "+dir());
            umask(027);

            // Child behavior
            child();
        }
        catch (const exception& e) {
            QPID_LOG(critical, "Daemon startup failed: " << e.what());
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
    int n=select(FD_SETSIZE, &fds, 0, 0, &tv);
    throwIf(n==0, "Timed out waiting for daemon");
    throwIf(n<0, "Error waiting for daemon");
    fdstream pipe(pipeFds[0]);
    pipe.exceptions(ios::failbit|ios::badbit|ios::eofbit);
    uint16_t port = 0;
    try {
        pipe >> port;
        if (port == 0) {
            string errmsg;
            pipe >> skipws;
            getline(pipe, errmsg);
            throw Exception("Daemon startup failed"+
                            (errmsg.empty() ? string(".") : ": " + errmsg));
        }
    }
    catch (const fdstream::failure& e) {
        throw Exception(string("Failed to read daemon port: ")+e.what());
    }
    return port;
}

void Daemon::ready(uint16_t port) { // child
    lockFile = pidFile(port);
    LockFile lf(lockFile, true);
    lf << getpid() << endl;
    if (lf.fail())
        throw Exception("Cannot write lock file "+lockFile);
    fdstream pipe(pipeFds[1]);
    QPID_LOG(debug, "Daemon ready on port: " << port);
    pipe << port << endl;
    throwIf(!pipe.good(), "Error writing to parent");
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
