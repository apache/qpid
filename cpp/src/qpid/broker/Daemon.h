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
#include <boost/function.hpp>

namespace qpid {
namespace broker {

/**
 * Tools for forking and managing a daemon process.
 * NB: Only one Daemon instance is allowed in a process.
 */
class Daemon
{
  public:
    /** Utility function to create pid file name in a standard place
     * (may require root acces) using identifier as the file name.
     */
    static std::string defaultPidFile(const std::string& identifier);
    
    /**
     * Daemon control object.
     *@param pidFileFn Function that will comupte a PID file name.
     * Called when pid file is created in ready()
     *@param timeout in seconds for any operations that wait.
     */
    Daemon(boost::function<std::string()> pidFileFn, int timeout);

    ~Daemon();

    typedef boost::function<void(Daemon&)> Function;

    /** Fork the daemon.
     *@param parent called in the parent process.
     *@param child called in the child process.
     */
    pid_t fork(Function parent, Function child);

    /** Parent only: wait for child to indicate it is ready.
     * @return value child passed to ready() */
    int wait();

    /** Child only. Notify the parent we are ready and write the
     * PID file.
     *@param value returned by parent call to wait(). -1 is reserved
     * for signalling an error.
     */
    void ready(int value);
    
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
    
    pid_t getPid() const {return pid; }

  private:
    class Retval;

    static boost::function<std::string()> pidFileFn;
    static const char* realPidFileFn();
    void notify(int);
    
    static std::string identifier;
    boost::scoped_ptr<Retval> retval;
    pid_t pid;
    int timeout;
};

}} // namespace qpid::broker

#endif  /*!_broker_Daemon_h*/
