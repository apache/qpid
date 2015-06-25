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

#include <windows.h>
#include <string>
#include <limits>
#include <vector>
#include <iostream>
#include <assert.h>

#include "qpid/messaging/Logger.h"
#include "qpid/messaging/exceptions.h"
#include "qpid/types/Variant.h"

#include "Logger.h"
#include "QpidException.h"
#include "QpidMarshal.h"

/// <summary>
/// outputCallbackHost
/// Native class that holds reference to managed class
/// and for hosting the native callback procedure.
/// </summary>
namespace qpid {
namespace messaging {

    class outputCallbackHost : public qpid::messaging::LoggerOutput {
    private:
        // Reference to managed class
        gcroot<Org::Apache::Qpid::Messaging::LoggerOutput^> m_loggerOutput;

    public:
        outputCallbackHost(Org::Apache::Qpid::Messaging::LoggerOutput ^ _m_loggerOutput)
            : m_loggerOutput(_m_loggerOutput)
        {
        }
        ~outputCallbackHost()
        {
        }

        // Native callback entry point called by qpid Logger 
        void log(qpid::messaging::Level level, bool user, const char* file, int line, 
            const char* function, const std::string& message)
        {
            m_loggerOutput->Log(level, user, file, line, function, message);
        }
    };
}}


namespace Org {
namespace Apache {
namespace Qpid {
namespace Messaging {

///
/// Managed class with desired delegate
///
LoggerOutput::LoggerOutput( LoggerOutputCallback^ callback)
    : loggerDelegate(callback), 
    interceptor(new qpid::messaging::outputCallbackHost(this))
{
    ::qpid::messaging::Logger::setOutput(*interceptor);
}

LoggerOutput::~LoggerOutput()
{
}

// Relay log message to managed code
void LoggerOutput::Log(
    qpid::messaging::Level level,
    bool user,
    const char* file,
    int line,
    const char* function,
    const std::string& message)
{
    // create managed log args
    Messaging::Level mLevel = (Messaging::Level)level;
    System::Boolean mUser = user;
    System::String^ mFile = gcnew String( file );
    System::Int32 mLine = line;
    System::String^ mFunction = gcnew String( function );
    System::String^ mMessage = gcnew String( message.c_str() );

    // pass to delegate
    loggerDelegate( mLevel, mUser, mFile, mLine, mFunction, mMessage );
}


/// <summary>
/// Logger a managed wrapper for a ::qpid::messaging::Logger
/// </summary>

// Constructor
Logger::Logger()
{
    // Sanity check that managed == native enum values
    assert((int)Messaging::Level::trace    == (int)::qpid::messaging::trace);
    assert((int)Messaging::Level::debug    == (int)::qpid::messaging::debug);
    assert((int)Messaging::Level::info     == (int)::qpid::messaging::info);
    assert((int)Messaging::Level::notice   == (int)::qpid::messaging::notice);
    assert((int)Messaging::Level::warning  == (int)::qpid::messaging::warning);
    assert((int)Messaging::Level::error    == (int)::qpid::messaging::error);
    assert((int)Messaging::Level::critical == (int)::qpid::messaging::critical);
}


// Destructor
Logger::~Logger()
{
    this->!Logger();
}


// Finalizer
Logger::!Logger()
{
}

void Logger::Configure(array<System::String ^> ^ args)
{
    Configure(args, "");
}

void Logger::Configure(array<System::String ^> ^ theArgs, System::String ^ thePrefix)
{
    System::Exception ^ newException = nullptr;

    try
    {
        // Marshal the calling args
        int argc = theArgs->Length;

        std::vector<std::string> cppStrings;
        for (int i=0; i<argc; i++)
        {
            cppStrings.push_back ( QpidMarshal::ToNative( theArgs[i] ) );
        }

        std::vector<const char *> cStrings;
        for (int i=0; i<argc; i++)
        {
            cStrings.push_back ( cppStrings[i].c_str() );
        }

        const char** argv = &cStrings[0];

        std::string prefix = QpidMarshal::ToNative(thePrefix);

        // configure
        ::qpid::messaging::Logger::configure(argc, argv, prefix);
    }
    catch (const ::qpid::types::Exception & error)
    {
        String ^ errmsg = gcnew String(error.what());
        newException    = gcnew QpidException(errmsg);
    }
    if (newException != nullptr)
    {
        throw newException;
    }
}


System::String ^ Logger::Usage()
{
    return gcnew String(::qpid::messaging::Logger::usage().c_str());
}


// Inject a log message from managed user space into unmanaged 
// Qpid Messaging log stream.
void Logger::Log(
    Messaging::Level level,
    System::String^ file,
    System::Int32 line,
    System::String^ function,
    System::String^ message)
{
    // create unmanaged log args
    qpid::messaging::Level nLevel = (qpid::messaging::Level)level;
    std::string nFile = QpidMarshal::ToNative(file);
    int nLine = line;
    std::string nFunction = QpidMarshal::ToNative(function);
    const std::string nMessage = QpidMarshal::ToNative(message);

    System::Exception ^ newException = nullptr;

    try
    {
        // pass to native log engine
        ::qpid::messaging::Logger::log(
            nLevel, nFile.c_str(), line, nFunction.c_str(), nMessage);
    }
    catch (const ::qpid::types::Exception & error)
    {
        String ^ errmsg = gcnew String(error.what());
        newException    = gcnew QpidException(errmsg);
    }

    if (newException != nullptr)
    {
        throw newException;
    }
}
}}}}
