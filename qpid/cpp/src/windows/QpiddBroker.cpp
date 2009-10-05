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

#ifdef HAVE_CONFIG_H
#  include "config.h"
#else
// These need to be made something sensible, like reading a value from
// the registry. But for now, get things going with a local definition.
namespace {
const char *QPIDD_CONF_FILE = "qpid_broker.conf";
const char *QPIDD_MODULE_DIR = ".";
}
#endif
#include "qpidd.h"
#include "qpid/Exception.h"
#include "qpid/Options.h"
#include "qpid/Plugin.h"
#include "qpid/sys/IntegerTypes.h"
#include "qpid/sys/LockFile.h"
#include "qpid/sys/windows/check.h"
#include "qpid/broker/Broker.h"

#include <iostream>
#include <windows.h>

using namespace qpid::broker;

BootstrapOptions::BootstrapOptions(const char* argv0)
  : qpid::Options("Options"),
    common("", QPIDD_CONF_FILE),
    module(QPIDD_MODULE_DIR),
    log(argv0)
{
    add(common);
    add(module);
    add(log);
}

// Local functions to set and get the pid via a LockFile.
namespace {

const std::string TCP = "tcp";

std::string brokerPidFile(std::string piddir, uint16_t port)
{
    std::ostringstream path;
    path << piddir << "\\broker_" << port << ".pid";
    return path.str();
}

// ShutdownEvent maintains an event that can be used to ask the broker
// to stop. Analogous to sending SIGTERM/SIGINT to the posix broker.
// The signal() method signals the event.
class ShutdownEvent {
  public:
    ShutdownEvent(pid_t other = 0);
    ~ShutdownEvent();

    void signal();

  protected:
    std::string eventName(pid_t pid);
    HANDLE event;
};

class ShutdownHandler : public ShutdownEvent, public qpid::sys::Runnable {
  public:
    ShutdownHandler(const boost::intrusive_ptr<Broker>& b)
      : ShutdownEvent()  { broker = b; }

  private:
    virtual void run();     // Inherited from Runnable
    boost::intrusive_ptr<Broker> broker;
};

ShutdownEvent::ShutdownEvent(pid_t other) : event(NULL) {
    // If given a pid, open an event assumedly created by that pid. If there's
    // no pid, create a new event using the current process id.
    if (other == 0) {
         std::string name = eventName(GetCurrentProcessId());
        // Auto-reset event in case multiple processes try to signal a
        // broker that doesn't respond for some reason. Initially not signaled.
        event = CreateEvent(NULL, false, false, name.c_str());
    }
    else {
        std::string name = eventName(other);
        event = OpenEvent(EVENT_MODIFY_STATE, false, name.c_str());
    }
    QPID_WINDOWS_CHECK_NULL(event);
}

ShutdownEvent::~ShutdownEvent() {
    CloseHandle(event);
    event = NULL;
}

void ShutdownEvent::signal() {
    QPID_WINDOWS_CHECK_NOT(SetEvent(event), 0);
}

std::string ShutdownEvent::eventName(pid_t pid) {
    std::ostringstream name;
    name << "qpidd_" << pid << std::ends;
    return name.str();
}


void ShutdownHandler::run() {
    if (event == NULL)
        return;
    WaitForSingleObject(event, INFINITE);
    if (broker.get()) {
        broker->shutdown();
        broker = 0;             // Release the broker reference
    }
}

// Console control handler to properly handle ctl-c.
BOOL CtrlHandler(DWORD ctl)
{
    ShutdownEvent shutter;     // no pid specified == shut me down
    shutter.signal();
    return ((ctl == CTRL_C_EVENT || ctl == CTRL_CLOSE_EVENT) ? TRUE : FALSE);
}

}

struct ProcessControlOptions : public qpid::Options {
    bool quit;
    bool check;
    std::string piddir;
    //std::string transport;   No transport options yet - TCP is it.

    ProcessControlOptions()
        : qpid::Options("Process control options"),
          quit(false),
          check(false) //, transport(TCP)
    {
        const DWORD pathLen = MAX_PATH + 1;
        char tempDir[pathLen];
        if (GetTempPath(pathLen, tempDir) == 0)
            piddir = "C:\\WINDOWS\\TEMP\\";
        else
            piddir = tempDir;
        piddir += "qpidd";

        // Only have TCP for now, so don't need this...
        //            ("transport", optValue(transport, "TRANSPORT"), "The transport for which to return the port")
        addOptions()
            ("pid-dir", qpid::optValue(piddir, "DIR"), "Directory where port-specific PID file is stored")
            ("check,c", qpid::optValue(check), "Prints the broker's process ID to stdout and returns 0 if the broker is running, otherwise returns 1")
            ("quit,q", qpid::optValue(quit), "Tells the broker to shut down");
    }
};

struct QpiddWindowsOptions : public QpiddOptionsPrivate {
    ProcessControlOptions control;
    QpiddWindowsOptions(QpiddOptions *parent) : QpiddOptionsPrivate(parent) {
        parent->add(control);
    }
};

QpiddOptions::QpiddOptions(const char* argv0)
  : qpid::Options("Options"),
    common("", QPIDD_CONF_FILE),
    module(QPIDD_MODULE_DIR),
    log(argv0)
{
    add(common);
    add(module);
    add(broker);
    add(log);

    platform.reset(new QpiddWindowsOptions(this));
    qpid::Plugin::addOptions(*this);
}

void QpiddOptions::usage() const {
    std::cout << "Usage: qpidd [OPTIONS]" << std::endl << std::endl
              << *this << std::endl;
}

int QpiddBroker::execute (QpiddOptions *options) {
    // Options that affect a running daemon.
    QpiddWindowsOptions *myOptions =
      reinterpret_cast<QpiddWindowsOptions *>(options->platform.get());
    if (myOptions == 0)
        throw qpid::Exception("Internal error obtaining platform options");

    if (myOptions->control.check || myOptions->control.quit) {
        // Relies on port number being set via --port or QPID_PORT env variable.
        qpid::sys::LockFile getPid (brokerPidFile(myOptions->control.piddir,
                                                  options->broker.port),
                                    false);
        pid_t pid = getPid.readPid();
        if (pid < 0) 
            return 1;
        if (myOptions->control.check)
            std::cout << pid << std::endl;
        if (myOptions->control.quit) {
            ShutdownEvent shutter(pid);
            HANDLE brokerHandle = OpenProcess(SYNCHRONIZE, false, pid);
            QPID_WINDOWS_CHECK_NULL(brokerHandle);
            shutter.signal();
            WaitForSingleObject(brokerHandle, INFINITE);
            CloseHandle(brokerHandle);
        }
        return 0;
    }

    boost::intrusive_ptr<Broker> brokerPtr(new Broker(options->broker));
    if (options->broker.port == 0)
        options->broker.port = brokerPtr->getPort("");
    std::cout << options->broker.port << std::endl;

    // Make sure the pid directory exists, creating if needed. LockFile
    // will throw an exception that makes little sense if it can't create
    // the file.
    if (!CreateDirectory(myOptions->control.piddir.c_str(), 0)) {
        DWORD err = GetLastError();
        if (err != ERROR_ALREADY_EXISTS)
            throw qpid::Exception(QPID_MSG("Can't create pid-dir " +
                                           myOptions->control.piddir +
                                           ": " +
                                           qpid::sys::strError(err)));
    }
    qpid::sys::LockFile myPid(brokerPidFile(myOptions->control.piddir,
                                            options->broker.port),
                              true);
    myPid.writePid();

    // Allow the broker to receive a shutdown request via a qpidd --quit
    // command. Note that when the broker is run as a service this operation
    // should not be allowed.

    ShutdownHandler waitShut(brokerPtr);
    qpid::sys::Thread waitThr(waitShut);   // Wait for shutdown event
    SetConsoleCtrlHandler((PHANDLER_ROUTINE)CtrlHandler, TRUE);
    brokerPtr->run();
    waitShut.signal();   // In case we shut down some other way
    waitThr.join();
    return 0;
}
