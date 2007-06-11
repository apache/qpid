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
#include "qpid/QpidError.h"
#include <libdaemon/daemon.h>
#include <errno.h>
#include <unistd.h>
#include <sys/stat.h>
#include <signal.h>

namespace qpid {
namespace broker {

using namespace std;

boost::function<std::string()> qpid::broker::Daemon::pidFileFn;

std::string Daemon::defaultPidFile(const std::string& identifier) {
    daemon_pid_file_ident=identifier.c_str();
    return daemon_pid_file_proc_default();
}

const char* Daemon::realPidFileFn() {
    static std::string str = pidFileFn();
    return str.c_str();
}

Daemon::Daemon(boost::function<std::string()> fn, int secs) : pid(-1), timeout(secs)
{
    pidFileFn = fn;
    daemon_pid_file_proc = &realPidFileFn;
}

Daemon::~Daemon() {
    if (isChild())
        daemon_pid_file_remove();
}

class Daemon::Retval {
  public:
    Retval();
    ~Retval();
    int send(int s);
    int wait(int timeout);
  private:
    bool completed;
};

pid_t Daemon::fork(Function parent, Function child) {
    retval.reset(new Retval());
    pid = daemon_fork();
    if (pid < 0)
        throw Exception("Failed to fork daemon: "+strError(errno));
    else if (pid == 0) {
        try {
            child(*this);
        } catch (const exception& e) {
            QPID_LOG(debug, "Rethrowing: " << e.what());
            failed();           // Notify parent
            throw;
        }
    }
    else
        parent(*this);
    return pid;
}

int Daemon::wait() {  // parent 
    assert(retval);
    errno = 0;                  // Clear errno.
    int ret = retval->wait(timeout); // wait for child.
    if (ret == -1) {
        if (errno)
            throw Exception("Error waiting for daemon startup:"
                            +strError(errno));
        else
            throw Exception("Error waiting for daemon startup, check logs.");
    }
    return ret;
}

void Daemon::notify(int value) { // child
    assert(retval);
    if (retval->send(value)) 
        throw Exception("Failed to notify parent: "+strError(errno));
}

void Daemon::ready(int value) { // child
    if (value==-1)
        throw Exception("Invalid value in Dameon::notify");
    errno = 0;
    if (daemon_pid_file_create() != 0)
        throw Exception(string("Failed to create PID file ") +
                        daemon_pid_file_proc()+": "+strError(errno));
    notify(value);
}

void Daemon::failed() { notify(-1); }

void  Daemon::quit() { 
    if (daemon_pid_file_kill_wait(SIGINT, timeout))
        throw Exception("Failed to stop daemon: " + strError(errno));
}

void  Daemon::kill() {
    if (daemon_pid_file_kill_wait(SIGKILL, timeout) < 0)
        throw Exception("Failed to stop daemon: " + strError(errno));
}

pid_t Daemon::check() {
    return daemon_pid_file_is_running();
}

Daemon::Retval::Retval() : completed(false) {
    daemon_retval_init();
}
Daemon::Retval::~Retval() {
    if (!completed) daemon_retval_done();
}
int Daemon::Retval::send(int s) {
    return daemon_retval_send(s);
}
int Daemon::Retval::wait(int timeout) {
    return daemon_retval_wait(timeout);
}

}} // namespace qpid::broker
