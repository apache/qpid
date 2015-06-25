/*
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
*/
#pragma once

#include <windows.h>
#include <msclr\lock.h>
#include <oletx2xa.h>
#include <string>
#include <limits>

#include "qpid/messaging/Logger.h"

//
// Logger is implemented in two classes that roughly correspond
// to the Logger and LoggerOutput classes defined by the native
// C++ classes in qpid/messaging/Logger.h. Please refer to the
// native Logger.h file for more detailed usage information.
//
// Logger is a control and status interface to configure logging
// and to inject log messages.
//
// LoggerOutput defines a delegate to accept the Qpid Messaging
// log message stream. LoggerOutput uses native class 
// outputCallbackHost to receive the native callbacks and forward
// the log on to the delegate.
//

/// <summary>
/// outputCallbackHost
/// Native class that holds reference to managed class
/// and for hosting the native callback procedure.
/// </summary>
namespace qpid {
namespace messaging {
    class outputCallbackHost;
}}

namespace Org {
namespace Apache {
namespace Qpid {
namespace Messaging {

    /// <summary>
    /// Level constants
    /// These correspond exactly to qpid::messaging::Level
    /// </summary>
    public enum class Level { trace, debug, info, notice, warning, error, critical };

    /// <summary>
    /// LoggerOutput relays messages to the managed delegate
    /// </summary>

    // The managed delegate signature
    public delegate void LoggerOutputCallback(
        Messaging::Level level,
        System::Boolean user,
        System::String^ file,
        System::Int32 line,
        System::String^ function,
        System::String^ message);

    // Managed class with desired delegate
    public ref class LoggerOutput
    {
    private:
        // private destructor
        ~LoggerOutput();

        // delegate
        LoggerOutputCallback^ loggerDelegate;

        // native class to host native callback
        qpid::messaging::outputCallbackHost * interceptor;

    public:
        // function to receive unmanaged log and relay it 
        // to managed delegate
        void Log( 
            qpid::messaging::Level level,
            bool user,
            const char* file,
            int line, 
            const char* function,
            const std::string& message);

        // constructor - create with reference to log message delegate
        LoggerOutput( LoggerOutputCallback^ callback);
    };

    /// <summary>
    /// Logger is a managed wrapper for native ::qpid::messaging::Logger
    /// </summary>

    public ref class Logger
    {
    private:

    public:
        Logger();
        ~Logger();
        !Logger();

        // Set logging in qpid messaging
        void Configure(array<System::String ^> ^ args);
        void Configure(array<System::String ^> ^ args, System::String ^ prefix);

        // Help string available after calling Configue
        System::String ^ Usage();

        // Inject a 'user' log message into qpid messaging log stream
        void Log(
            Messaging::Level level,
            System::String^ file,
            System::Int32 line,
            System::String^ function,
            System::String^ message);
    };
}}}}
