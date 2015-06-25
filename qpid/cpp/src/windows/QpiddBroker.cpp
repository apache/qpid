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

#include "config.h"
#include "qpidd.h"
#include "SCM.h"
#include "qpid/Exception.h"
#include "qpid/Options.h"
#include "qpid/Plugin.h"
#include "qpid/sys/IntegerTypes.h"
#include "qpid/sys/windows/check.h"
#include "qpid/sys/Thread.h"
#include "qpid/broker/Broker.h"

#include <iostream>
#include <string>
#include <vector>
#include <windows.h>

namespace {
  // This will accept args from the command line; augmented with service args.
  std::vector<std::string> cmdline_args;
}

namespace qpid {
namespace broker {

BootstrapOptions::BootstrapOptions(const char* argv0)
  : qpid::Options("Options"),
    common("", QPIDD_CONF_FILE, QPIDC_CONF_FILE),
    module(QPIDD_MODULE_DIR),
    log(argv0)
{
    add(common);
    add(module);
    add(log);
}

void BootstrapOptions::usage() const {
    std::cout << "Usage: qpidd [OPTIONS]" << std::endl << std::endl << *this << std::endl;
}

// Local functions to set and get the pid via a LockFile.
namespace {

const std::string TCP = "tcp";

// ShutdownEvent maintains an event that can be used to ask the broker
// to stop. Analogous to sending SIGTERM/SIGINT to the posix broker.
// The signal() method signals the event.
class ShutdownEvent {
  public:
    ShutdownEvent(int port);
    ~ShutdownEvent();

    void create();
    void open();
    void signal();

  private:
    std::string eventName;

  protected:
    HANDLE event;
};

class ShutdownHandler : public ShutdownEvent, public qpid::sys::Runnable {
  public:
    ShutdownHandler(int port, const boost::intrusive_ptr<Broker>& b)
      : ShutdownEvent(port)  { broker = b; }

  private:
    virtual void run();     // Inherited from Runnable
    boost::intrusive_ptr<Broker> broker;
};

ShutdownEvent::ShutdownEvent(int port) : event(NULL) {
    std::ostringstream name;
    name << "qpidd_" << port << std::ends;
    eventName = name.str();
}

void ShutdownEvent::create() {
    // Auto-reset event in case multiple processes try to signal a
    // broker that doesn't respond for some reason. Initially not signaled.
    event = ::CreateEvent(NULL, false, false, eventName.c_str());
    QPID_WINDOWS_CHECK_NULL(event);
}

void ShutdownEvent::open() {
    // TODO: Might need to search Global\\ name if unadorned name fails
    event = ::OpenEvent(EVENT_MODIFY_STATE, false, eventName.c_str());
    QPID_WINDOWS_CHECK_NULL(event);
}

ShutdownEvent::~ShutdownEvent() {
    ::CloseHandle(event);
    event = NULL;
}

void ShutdownEvent::signal() {
    QPID_WINDOWS_CHECK_NOT(::SetEvent(event), 0);
}


void ShutdownHandler::run() {
    if (event == NULL)
        return;
    ::WaitForSingleObject(event, INFINITE);
    if (broker.get()) {
        broker->shutdown();
        broker = 0;             // Release the broker reference
    }
}

// Console control handler to properly handle ctl-c.
int ourPort;
BOOL CtrlHandler(DWORD ctl)
{
    ShutdownEvent shutter(ourPort);     // We have to have set up the port before interrupting
    shutter.open();
    shutter.signal();
    return ((ctl == CTRL_C_EVENT || ctl == CTRL_CLOSE_EVENT) ? TRUE : FALSE);
}

template <typename T>
class NamedSharedMemory {
    std::string name;
    HANDLE memory;
    T* data;

public:
    NamedSharedMemory(const std::string&);
    ~NamedSharedMemory();

    T& create();
    T& get();
};

template <typename T>
NamedSharedMemory<T>::NamedSharedMemory(const std::string& n) :
    name(n),
    memory(NULL),
    data(0)
{}

template <typename T>
NamedSharedMemory<T>::~NamedSharedMemory() {
    if (data)
        ::UnmapViewOfFile(data);
    if (memory != NULL)
        ::CloseHandle(memory);
}

template <typename T>
T& NamedSharedMemory<T>::create() {
    assert(memory == NULL);

    // Create named shared memory file
    memory = ::CreateFileMapping(INVALID_HANDLE_VALUE, NULL, PAGE_READWRITE, 0, sizeof(T), name.c_str());
    QPID_WINDOWS_CHECK_NULL(memory);

    // Map file into memory
    data = static_cast<T*>(::MapViewOfFile(memory, FILE_MAP_WRITE, 0, 0, 0));
    QPID_WINDOWS_CHECK_NULL(data);

    return *data;
}

template <typename T>
T& NamedSharedMemory<T>::get() {
    if (memory == NULL) {
        // TODO: Might need to search Global\\ name if unadorned name fails
        memory = ::OpenFileMapping(FILE_MAP_WRITE, FALSE, name.c_str());
        QPID_WINDOWS_CHECK_NULL(memory);

        data = static_cast<T*>(::MapViewOfFile(memory, FILE_MAP_WRITE, 0, 0, 0));
        QPID_WINDOWS_CHECK_NULL(data);
    }

    return *data;
}

std::string brokerInfoName(uint16_t port)
{
    std::ostringstream path;
    path << "qpidd_info_" << port;
    return path.str();
}

struct BrokerInfo {
    DWORD pid;
};

// Service-related items. Only involved when running the broker as a Windows
// service.

const std::string svcName = "qpidd";
SERVICE_STATUS svcStatus;
SERVICE_STATUS_HANDLE svcStatusHandle = 0;

// This function is only called when the broker is run as a Windows
// service. It receives control requests from Windows.
VOID WINAPI SvcCtrlHandler(DWORD control)
{
    switch(control) {
    case SERVICE_CONTROL_STOP:
        svcStatus.dwCurrentState = SERVICE_STOP_PENDING;
        svcStatus.dwControlsAccepted = 0;
        svcStatus.dwCheckPoint = 1;
        svcStatus.dwWaitHint = 5000;  // 5 secs.
        ::SetServiceStatus(svcStatusHandle, &svcStatus);
        CtrlHandler(CTRL_C_EVENT);
        break;

    case SERVICE_CONTROL_INTERROGATE:
        break;

    default:
        break;
    }
}

VOID WINAPI ServiceMain(DWORD argc, LPTSTR *argv)
{
    // The arguments can come from 2 places. Args set with the executable
    // name when the service is installed come through main() and are now
    // in cmdline_args. Arguments set in StartService come into argc/argv
    // above; if they are set, argv[0] is the service name. Make command
    // line args first; StartService args come later and can override
    // command line args.
    int all_argc = argc + cmdline_args.size();
    if (argc == 0 && !cmdline_args.empty())
      ++all_argc;    // No StartService args, so need to add prog name argv[0]
    const char **all_argv = new const char *[all_argc];
    if (all_argc > 0) {
      int i = 0;
      all_argv[i++] = argc > 0 ? argv[0] : svcName.c_str();
      for (size_t j = 0; j < cmdline_args.size(); ++j)
        all_argv[i++] = cmdline_args[j].c_str();
      for (DWORD k = 1; k < argc; ++k)
        all_argv[i++] = argv[k];
    }

    ::memset(&svcStatus, 0, sizeof(svcStatus));
    svcStatusHandle = ::RegisterServiceCtrlHandler(svcName.c_str(),
                                                   SvcCtrlHandler);
    svcStatus.dwServiceType = SERVICE_WIN32_OWN_PROCESS;
    svcStatus.dwCheckPoint = 1;
    svcStatus.dwWaitHint = 10000;  // 10 secs.
    svcStatus.dwCurrentState = SERVICE_START_PENDING;
    ::SetServiceStatus(svcStatusHandle, &svcStatus);
    // QpiddBroker class resets state to running.
    svcStatus.dwWin32ExitCode = run_broker(all_argc,
                                           const_cast<char**>(all_argv),
                                           true);
    svcStatus.dwCurrentState = SERVICE_STOPPED;
    svcStatus.dwCheckPoint = 0;
    svcStatus.dwWaitHint = 0;
    ::SetServiceStatus(svcStatusHandle, &svcStatus);
}

}  // namespace


struct ProcessControlOptions : public qpid::Options {
    bool quit;
    bool check;
    std::string transport;

    ProcessControlOptions()
        : qpid::Options("Process control options"),
          quit(false),
          check(false),
          transport(TCP)
    {
        addOptions()
            ("check,c", qpid::optValue(check), "Prints the broker's process ID to stdout and returns 0 if the broker is running, otherwise returns 1")
            ("transport", qpid::optValue(transport, "TRANSPORT"), "The transport for which to return the port")
            ("quit,q", qpid::optValue(quit), "Tells the broker to shut down");
    }
};

struct ServiceOptions : public qpid::Options {
    bool install;
    bool start;
    bool stop;
    bool uninstall;
    bool daemon;
    std::string startType;
    std::string startArgs;
    std::string account;
    std::string password;
    std::string depends;

    ServiceOptions()
        : qpid::Options("Service options"),
          install(false),
          start(false),
          stop(false),
          uninstall(false),
          daemon(false),
          startType("demand"),
          startArgs(""),
          account("NT AUTHORITY\\LocalService"),
          password(""),
          depends("")
    {
        addOptions()
            ("install", qpid::optValue(install), "Install as service")
            ("start-type", qpid::optValue(startType, "auto|demand|disabled"), "Service start type\nApplied at install time only.")
            ("arguments", qpid::optValue(startArgs, "COMMAND LINE ARGS"), "Arguments to pass when service auto-starts")
            ("account", qpid::optValue(account, "(LocalService)"), "Account to run as, default is LocalService\nApplied at install time only.")
            ("password", qpid::optValue(password, "PASSWORD"), "Account password, if needed\nApplied at install time only.")
            ("depends", qpid::optValue(depends, "(comma delimited list)"), "Names of services that must start before this service\nApplied at install time only.")
            ("start", qpid::optValue(start), "Start the service.")
            ("stop", qpid::optValue(stop), "Stop the service.")
            ("uninstall", qpid::optValue(uninstall), "Uninstall the service.");
    }
};

struct QpiddWindowsOptions : public QpiddOptionsPrivate {
    ProcessControlOptions control;
    ServiceOptions service;
    QpiddWindowsOptions(QpiddOptions *parent) : QpiddOptionsPrivate(parent) {
        parent->add(service);
        parent->add(control);
    }
};

QpiddOptions::QpiddOptions(const char* argv0)
  : qpid::Options("Options"),
    common("", QPIDD_CONF_FILE, QPIDC_CONF_FILE),
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

    // If running as a service, bump the status checkpoint to let SCM know
    // we're still making progress.
    if (svcStatusHandle != 0) {
        svcStatus.dwCheckPoint++;
        ::SetServiceStatus(svcStatusHandle, &svcStatus);
    }

    // Options that affect a running daemon.
    QpiddWindowsOptions *myOptions =
        reinterpret_cast<QpiddWindowsOptions *>(options->platform.get());
    if (myOptions == 0)
        throw qpid::Exception("Internal error obtaining platform options");

    if (myOptions->service.install) {
        // Handle start type
        DWORD startType;
        if (myOptions->service.startType.compare("demand") == 0)
            startType = SERVICE_DEMAND_START;
        else if (myOptions->service.startType.compare("auto") == 0)
            startType = SERVICE_AUTO_START;
        else if (myOptions->service.startType.compare("disabled") == 0)
            startType = SERVICE_DISABLED;
        else if (!myOptions->service.startType.empty())
            throw qpid::Exception("Invalid service start type: " +
                                  myOptions->service.startType);

        // Install service and exit
        qpid::windows::SCM manager;
        manager.install(svcName,
                        "Apache Qpid Message Broker",
                        myOptions->service.startArgs,
                        startType,
                        myOptions->service.account,
                        myOptions->service.password,
                        myOptions->service.depends);
        return 0;
    }

    if (myOptions->service.start) {
        qpid::windows::SCM manager;
        manager.start(svcName);
        return 0;
    }

    if (myOptions->service.stop) {
        qpid::windows::SCM manager;
        manager.stop(svcName);
        return 0;
    }

    if (myOptions->service.uninstall) {
        qpid::windows::SCM manager;
        manager.uninstall(svcName);
        return 0;
    }

    if (myOptions->control.check || myOptions->control.quit) {
        // Relies on port number being set via --port or QPID_PORT env variable.
        NamedSharedMemory<BrokerInfo> info(brokerInfoName(options->broker.port));
        int pid = info.get().pid;
        if (pid < 0)
            return 1;
        if (myOptions->control.check)
            std::cout << pid << std::endl;
        if (myOptions->control.quit) {
            ShutdownEvent shutter(options->broker.port);
            shutter.open();
            shutter.signal();
            HANDLE brokerHandle = ::OpenProcess(SYNCHRONIZE, false, pid);
            QPID_WINDOWS_CHECK_NULL(brokerHandle);
            ::WaitForSingleObject(brokerHandle, INFINITE);
            ::CloseHandle(brokerHandle);
        }
        return 0;
    }

    boost::intrusive_ptr<Broker> brokerPtr(new Broker(options->broker));

    // Need the correct port number to use in the pid file name.
    if (options->broker.port == 0)
        options->broker.port = brokerPtr->getPort(myOptions->control.transport);

    BrokerInfo info;
    info.pid = ::GetCurrentProcessId();

    NamedSharedMemory<BrokerInfo> sharedInfo(brokerInfoName(options->broker.port));
    sharedInfo.create() = info;

    // Allow the broker to receive a shutdown request via a qpidd --quit
    // command. Note that when the broker is run as a service this operation
    // should not be allowed.
    ourPort = options->broker.port;
    ShutdownHandler waitShut(ourPort, brokerPtr);
    waitShut.create();
    qpid::sys::Thread waitThr(waitShut);   // Wait for shutdown event
    ::SetConsoleCtrlHandler((PHANDLER_ROUTINE)CtrlHandler, TRUE);
    brokerPtr->accept();
    std::cout << options->broker.port << std::endl;

    // If running as a service, tell SCM we're up. There's still a chance
    // that store recovery will drag out the time before the broker actually
    // responds to requests, but integrating that mechanism with the SCM
    // updating is probably more work than it's worth.
    if (svcStatusHandle != 0) {
        svcStatus.dwCheckPoint = 0;
        svcStatus.dwWaitHint = 0;
        svcStatus.dwControlsAccepted = SERVICE_ACCEPT_STOP;
        svcStatus.dwCurrentState = SERVICE_RUNNING;
        ::SetServiceStatus(svcStatusHandle, &svcStatus);
    }

    brokerPtr->run();
    waitShut.signal();   // In case we shut down some other way
    waitThr.join();
    return 0;
}

}} // namespace qpid::broker

int main(int argc, char* argv[])
{
    // If started as a service, notify the SCM we're up. Else just run.
    // If as a service, StartServiceControlDispatcher doesn't return until
    // the service is stopped.
    SERVICE_TABLE_ENTRY dispatchTable[] =
    {
        { "", (LPSERVICE_MAIN_FUNCTION)qpid::broker::ServiceMain },
        { NULL, NULL }
    };
    // Copy any command line args to be available in case we're started
    // as a service. Pick these back up in ServiceMain.
    for (int i = 1; i < argc; ++i)
      cmdline_args.push_back(argv[i]);

    if (!StartServiceCtrlDispatcher(dispatchTable)) {
        DWORD err = ::GetLastError();
        if (err == ERROR_FAILED_SERVICE_CONTROLLER_CONNECT) // Run as console
            return qpid::broker::run_broker(argc, argv);
        throw QPID_WINDOWS_ERROR(err);
    }
    return 0;
}
