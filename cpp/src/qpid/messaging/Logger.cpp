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

#include "qpid/messaging/Logger.h"

#include "qpid/log/Logger.h"
#include "qpid/log/OstreamOutput.h"
#include "qpid/messaging/exceptions.h"

#include <sstream>
#include <string>
#include <vector>

using std::string;
using std::vector;

namespace qpid {
namespace messaging {

// Proxy class to call the users output class/routine
class ProxyOutput : public qpid::log::Logger::Output {
    LoggerOutput& output;

    void log(const qpid::log::Statement& s, const string& message) {
        output.log(qpid::messaging::Level(s.level), s.category==qpid::log::external_application, s.file, s.line, s.function, message);
    }

public:
    ProxyOutput(LoggerOutput& o) :
        output(o)
    {}
};

LoggerOutput::~LoggerOutput()
{
}

inline qpid::log::Logger& logger() {
    static qpid::log::Logger& theLogger=qpid::log::Logger::instance();
    return theLogger;
}

namespace {
    std::string loggerUsage;
    qpid::log::Selector loggerSelector;
}

std::string Logger::usage()
{
    return loggerUsage;
}

void Logger::configure(int argc, const char* argv[], const string& pre)
try
{
    bool logToStdout = false;
    bool logToStderr = false;
    string logFile;
    std::vector<std::string> selectors;
    std::vector<std::string> deselectors;
    bool time = false;
    bool level = false;
    bool thread = false;
    bool source = false;
    bool function = false;
    bool hiresTs = false;

    selectors.push_back("notice+"); // Set this for the usage message default

    string prefix = pre.empty() ? pre : pre+"-";
    qpid::Options myOptions;
    myOptions.addOptions()
    ((prefix+"log-enable").c_str(), optValue(selectors, "RULE"),
     ("Enables logging for selected levels and components. "
     "RULE is in the form 'LEVEL[+-][:PATTERN]'\n"
     "LEVEL is one of: \n\t "+qpid::log::getLevels()+"\n"
     "PATTERN is a logging category name, or a namespace-qualified "
     "function name or name fragment. "
     "Logging category names are: \n\t "+qpid::log::getCategories()+"\n"
     "The category \"Application\" contains all messages logged by the application.\n"
     "For example:\n"
     "\t'--log-enable warning+'\n"
     "logs all warning, error and critical messages.\n"
     "\t'--log-enable trace+:Application --log-enable notice+'\n"
     "logs all application messages, but only notice or higher for the qpid library messages\n"
     "\t'--log-enable debug:framing'\n"
     "logs debug messages from all functions with 'framing' in the namespace or function name.\n"
     "This option can be used multiple times").c_str())
    ((prefix+"log-disable").c_str(), optValue(deselectors, "RULE"),
     ("Disables logging for selected levels and components. "
     "RULE is in the form 'LEVEL[+-][:PATTERN]'\n"
     "LEVEL is one of: \n\t "+qpid::log::getLevels()+"\n"
     "PATTERN is a logging category name, or a namespace-qualified "
     "function name or name fragment. "
     "Logging category names are: \n\t "+qpid::log::getCategories()+"\n"
     "For example:\n"
     "\t'--log-disable warning-'\n"
     "disables logging all warning, notice, info, debug, and trace messages.\n"
     "\t'--log-disable trace:Application'\n"
     "disables all application trace messages.\n"
     "\t'--log-disable debug-:qmf::'\n"
     "disables logging debug and trace messages from all functions with 'qmf::' in the namespace.\n"
     "This option can be used multiple times").c_str())
    ((prefix+"log-time").c_str(), optValue(time, "yes|no"), "Include time in log messages")
    ((prefix+"log-level").c_str(), optValue(level,"yes|no"), "Include severity level in log messages")
    ((prefix+"log-source").c_str(), optValue(source,"yes|no"), "Include source file:line in log messages")
    ((prefix+"log-thread").c_str(), optValue(thread,"yes|no"), "Include thread ID in log messages")
    ((prefix+"log-function").c_str(), optValue(function,"yes|no"), "Include function signature in log messages")
    ((prefix+"log-hires-timestamp").c_str(), optValue(hiresTs,"yes|no"), "Use hi-resolution timestamps in log messages")
    ((prefix+"log-to-stderr").c_str(), optValue(logToStderr, "yes|no"), "Send logging output to stderr")
    ((prefix+"log-to-stdout").c_str(), optValue(logToStdout, "yes|no"), "Send logging output to stdout")
    ((prefix+"log-to-file").c_str(), optValue(logFile, "FILE"), "Send log output to FILE.")
    ;

    std::ostringstream loggerSStream;
    myOptions.print(loggerSStream);

    loggerUsage=loggerSStream.str();

    selectors.clear(); // Clear to give passed in options precedence

    // Parse the command line not failing for unrecognised options
    myOptions.parse(argc, argv, std::string(), true);

    // If no passed in enable or disable log specification then go back to default
    if (selectors.empty() && deselectors.empty())
        selectors.push_back("notice+");
    // Set the logger options according to what we just parsed
    qpid::log::Options logOptions;
    logOptions.selectors = selectors;
    logOptions.deselectors = deselectors;
    logOptions.time = time;
    logOptions.level = level;
    logOptions.category = false;
    logOptions.thread = thread;
    logOptions.source = source;
    logOptions.function = function;
    logOptions.hiresTs = hiresTs;

    loggerSelector = qpid::log::Selector(logOptions);
    logger().clear(); // Need to clear before configuring as it will have been initialised statically already
    logger().format(logOptions);
    logger().select(loggerSelector);

    // Have to set up the standard output sinks manually
    if (logToStderr)
        logger().output(std::auto_ptr<qpid::log::Logger::Output>
                       (new qpid::log::OstreamOutput(std::clog)));
    if (logToStdout)
        logger().output(std::auto_ptr<qpid::log::Logger::Output>
                       (new qpid::log::OstreamOutput(std::cout)));

    if (logFile.length() > 0)
        logger().output(std::auto_ptr<qpid::log::Logger::Output>
                         (new qpid::log::OstreamOutput(logFile)));
}
catch (std::exception& e)
{
    throw MessagingException(e.what());
}

void Logger::setOutput(LoggerOutput& o)
{
    logger().output(std::auto_ptr<qpid::log::Logger::Output>(new ProxyOutput(o)));
}

void Logger::log(Level level, const char* file, int line, const char* function, const string& message)
{
    if (loggerSelector.isEnabled(qpid::log::Level(level), function, qpid::log::unspecified)) {
        qpid::log::Statement s = {
            true,
            file,
            line,
            function,
            qpid::log::Level(level),
            qpid::log::external_application,
        };
        logger().log(s, message);
    }
}

}} // namespace qpid::messaging
