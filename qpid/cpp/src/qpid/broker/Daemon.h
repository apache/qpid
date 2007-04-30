#ifndef _broker_Daemon_h
#define _broker_Daemon_h

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

#include <string>
#include <boost/scoped_ptr.hpp>

namespace qpid {
namespace broker {

/**
 * Tools for forking and managing a daemon process.
 * NB: Only one Daemon instance is allowed in a process.
 */
class Daemon
{
  public:

    /** Extract the daemon's name from argv[0] */
    static std::string nameFromArgv0(const char* argv0);
    
    /**
     * Creating a Daemon instance forks a daemon process.
     *@param name used to create pid files etc.
     *@param timeout in seconds for all operations that wait.
     */
    Daemon(const std::string& name, int timeout);

    ~Daemon();
    
    /** Fork the daemon, wait till it signals readiness */
    pid_t fork();

    /** Child only, send ready signal so parent fork() will return. */
    void ready();
    
    /** Child only, send failed signal so parent fork() will throw. */
    void failed();
    
    /** Kill the daemon with SIGINT. */
    void quit();
    
    /** Kill the daemon with SIGKILL. */
    void kill();

    /** Check daemon is running, throw exception if not */
    pid_t check();

    bool isParent() { return pid > 0; }

    bool isChild() { return pid == 0; }
    
    std::string getName() const { return name; }

    pid_t getPid() const {return pid; }

  private:
    class Retval;

    void notify(int);
    
    static std::string name;
    static std::string pidFile;
    static const char* getPidFile();
    boost::scoped_ptr<Retval> retval;
    pid_t pid;
    int timeout;
};

}} // namespace qpid::broker



#endif  /*!_broker_Daemon_h*/
