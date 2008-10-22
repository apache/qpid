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

#include "SinkOptions.h"
#include "qpid/log/SinkOptions.h"
#include "qpid/log/Logger.h"
#include "qpid/log/OstreamOutput.h"
#include "qpid/memory.h"
#include "qpid/Exception.h"
#include <iostream>
#include <map>
#include <string>

using std::string;
using qpid::Exception;

namespace qpid {
namespace log {
namespace windows {

class EventLogOutput : public qpid::log::Logger::Output {
public:
    EventLogOutput(const std::string& logName)
    {
    }

    virtual ~EventLogOutput() {
    }
    
    virtual void log(const Statement& s, const std::string& m)
    {
    }

private:    
};

SinkOptions::SinkOptions(const std::string& argv0)
    : qpid::log::SinkOptions(),
      logToStderr(true),
      logToStdout(false),
      logToEventLog(false)
{
    addOptions()
      ("log-to-stderr", optValue(logToStderr, "yes|no"), "Send logging output to stderr")
      ("log-to-stdout", optValue(logToStdout, "yes|no"), "Send logging output to stdout")
      ("log-to-file", optValue(logFile, "FILE"), "Send log output to FILE.")
      ("log-to-eventlog", optValue(logToEventLog, "yes|no"), "Send logging output to event log;\n\tcustomize using --syslog-name and --syslog-facility")
      ("syslog-name", optValue(syslogName, "NAME"), "Name to use in syslog messages")
      ("syslog-facility", optValue(syslogFacility,"LOG_XXX"), "Facility to use in syslog messages")
      ;

}

qpid::log::SinkOptions& SinkOptions::operator=(const qpid::log::SinkOptions& rhs) {
    const SinkOptions *prhs = dynamic_cast<const SinkOptions*>(&rhs);
    if (this != prhs) {
        logToStderr = prhs->logToStderr;
        logToStdout = prhs->logToStdout;
        logToEventLog = prhs->logToEventLog;
        logFile = prhs->logFile;
        syslogName = prhs->syslogName;
        syslogFacility.value = prhs->syslogFacility.value;
    }
    return *this;
}

void SinkOptions::detached(void) {
    if (logToStderr && !logToStdout && !logToEventLog) {
        logToStderr = false;
        logToEventLog = true;
    }
}

// The Logger acting on these options calls setup() to request any
// Sinks be set up and fed back to the logger.
void SinkOptions::setup(qpid::log::Logger *logger) {
    if (logToStderr)
        logger->output(make_auto_ptr<qpid::log::Logger::Output>
                         (new qpid::log::OstreamOutput(std::clog)));
    if (logToStdout)
        logger->output(make_auto_ptr<qpid::log::Logger::Output>
                         (new qpid::log::OstreamOutput(std::cout)));

    if (logFile.length() > 0)
        logger->output(make_auto_ptr<qpid::log::Logger::Output>
                         (new qpid::log::OstreamOutput(logFile)));

    if (logToEventLog)
        logger->output(make_auto_ptr<qpid::log::Logger::Output>
                         (new EventLogOutput(syslogName, syslogFacility)));

}

} // namespace windows

SinkOptions* SinkOptions::create(const std::string& argv0) {
    return new qpid::log::windows::SinkOptions (argv0);
}

}} // namespace qpid::log
