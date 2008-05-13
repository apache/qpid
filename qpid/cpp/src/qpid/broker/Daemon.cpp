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

#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

namespace qpid {
namespace broker {

using namespace std;

namespace {
/** Throw an exception containing msg and strerror if throwIf is true.
 * Name is supposed to be reminiscent of perror().
 */
void throwIf(bool condition, const string& msg, int errNo=errno) {
    if (condition)
        throw Exception(msg + (errNo? ": "+strError(errNo) : string(".")));
}


/*
 * Rewritten using low-level IO, for compatibility 
 * with earlier Boost versions, i.e. 103200.
 */
struct LockFile {

    LockFile(const std::string& path_, bool create)
        : path(path_), fd(-1), created(create)
    {
        errno = 0;
        int flags=create ? O_WRONLY|O_CREAT|O_NOFOLLOW : O_RDWR;
        fd = ::open(path.c_str(), flags, 0644);
        throwIf(fd < 0,"Cannot open "+path);
        throwIf(::lockf(fd, F_TLOCK, 0) < 0, "Cannot lock "+path);
    }

    ~LockFile() {
        if (fd >= 0) {
            ::lockf(fd, F_ULOCK, 0);
            ::close(fd);
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

/*
 * Rewritten using low-level IO, for compatibility 
 * with earlier Boost versions, i.e. 103200.
 */
void Daemon::fork()
{
    throwIf(::pipe(pipeFds) < 0, "Can't create pipe");  
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
            uint16_t port = 0;
            write(pipeFds[1], &port, sizeof(uint16_t));

            std::string pipeFailureMessage = e.what();
            write ( pipeFds[1], 
                    pipeFailureMessage.c_str(), 
                    strlen(pipeFailureMessage.c_str())
                  );
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

    /*
     * Rewritten using low-level IO, for compatibility 
     * with earlier Boost versions, i.e. 103200.
     */
    fd_set fds;
    FD_ZERO(&fds);
    FD_SET(pipeFds[0], &fds);
    int n=select(FD_SETSIZE, &fds, 0, 0, &tv);
    throwIf(n==0, "Timed out waiting for daemon");
    throwIf(n<0, "Error waiting for daemon");
    uint16_t port = 0;
    /*
     * Read the child's port number from the pipe.
     */
    int desired_read = sizeof(uint16_t);
    if ( desired_read > ::read(pipeFds[0], & port, desired_read) ) {
      throw Exception("Cannot write lock file "+lockFile);
    }

    /*
     * If the port number is 0, the child has put an error message
     * on the pipe.  Get it and throw it.
     */
     if ( 0 == port ) {
       // Skip whitespace
       char c = ' ';
       while ( isspace(c) ) {
         if ( 1 > ::read(pipeFds[0], &c, 1) )
           throw Exception("Child port == 0, and no error message on pipe.");
       }

       // Get Message
       string errmsg;
       do {
           errmsg += c;
       } while (::read(pipeFds[0], &c, 1));
       throw Exception("Daemon startup failed"+
                       (errmsg.empty() ? string(".") : ": " + errmsg));
     }

    return port;
}


/*
 * When the child is ready, it writes its pid to the
 * lockfile and its port number on the pipe back to
 * its parent process.  This indicates that the
 * child has successfully daemonized.  When the parent
 * hears the good news, it ill exit.
 */
void Daemon::ready(uint16_t port) { // child
    lockFile = pidFile(port);
    LockFile lf(lockFile, true);

    /*
     * Rewritten using low-level IO, for compatibility 
     * with earlier Boost versions, i.e. 103200.
     */
    /*
     * Write the PID to the lockfile.
     */
    pid_t pid = getpid();
    int desired_write = sizeof(pid_t);
    if ( desired_write > ::write(lf.fd, & pid, desired_write) ) {
      throw Exception("Cannot write lock file "+lockFile);
    }

    /*
     * Write the port number to the parent.
     */
     desired_write = sizeof(uint16_t);
     if ( desired_write > ::write(pipeFds[1], & port, desired_write) ) {
       throw Exception("Error writing to parent." );
     }

     QPID_LOG(debug, "Daemon ready on port: " << port);
}

/*
 * The parent process reads the child's pid
 * from the lockfile.
 */
pid_t Daemon::getPid(uint16_t port) {
    string name = pidFile(port);
    LockFile lf(name, false);
    pid_t pid;

    /*
     * Rewritten using low-level IO, for compatibility 
     * with earlier Boost versions, i.e. 103200.
     */
    int desired_read = sizeof(pid_t);
    if ( desired_read > ::read(lf.fd, & pid, desired_read) ) {
      throw Exception("Cannot read lock file " + name);
    }
    if (kill(pid, 0) < 0 && errno != EPERM) {
        unlink(name.c_str());
        throw Exception("Removing stale lock file "+name);
    }
    return pid;
}


}} // namespace qpid::broker
