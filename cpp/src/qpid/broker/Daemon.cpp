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
#include "qpid/QpidError.h"
#include <libdaemon/daemon.h>
#include <errno.h>
#include <unistd.h>
#include <sys/stat.h>
#include <signal.h>
#include <boost/filesystem/path.hpp>
#include <boost/filesystem/operations.hpp>

namespace qpid {
namespace broker {

using namespace std;

string Daemon::pidFile;
string Daemon::name;

string Daemon::nameFromArgv0(const char* argv0) {
    return string(daemon_ident_from_argv0(const_cast<char*>(argv0)));
}

const char* Daemon::getPidFile() {
    if (pidFile.empty()) {
        const char* home=getenv("HOME");
        if (!home)
            throw(Exception("$HOME is not set, cant create $HOME/.qpidd."));
        using namespace boost::filesystem;
        path dir = path(home,native) / path(".qpidd", native);
        create_directory(dir);
        dir /= name;
        pidFile = dir.string();
    }
    return pidFile.c_str();
}

Daemon::Daemon(const string& name_, int secs) : timeout(secs)
{
    name = name_;
    daemon_pid_file_ident = daemon_log_ident = name.c_str();
    if (getuid() != 0) {
        // For normal users put pid file under $HOME/.qpid
        daemon_pid_file_proc = getPidFile;
    }
    // For root use the libdaemon default: /var/run.    
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

pid_t Daemon::fork() {
    retval.reset(new Retval());
    pid = daemon_fork();
    if (pid < 0)
            throw Exception("Failed to fork daemon: "+strError(errno));
    else if (pid > 0) {
        int ret = retval->wait(timeout); // parent, wait for child.
        if (ret != 0) {
            string err;
            if (ret > 0)
                err = strError(ret);
            else if (ret == -1)
                err= strError(errno);
            else
                err= "unknown error";
            throw Exception("Deamon startup failed: "+err);
        }
    }
    else if (pid == 0) { // child.
        // TODO aconway 2007-04-26: Should log failures.
        if (daemon_pid_file_create())
            failed();
    }
    return pid;
}

void Daemon::notify(int i) {
    assert(retval);
    if (retval->send(i)) 
        throw Exception("Failed to notify parent: "+strError(errno));
}

void Daemon::ready() { notify(0); }

// NB: Not -1, confused with failure of fork() on the parent side.
void Daemon::failed() { notify(errno? errno:-2); }

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
