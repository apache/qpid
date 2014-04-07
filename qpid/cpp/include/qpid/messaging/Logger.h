#ifndef QPID_MESSAGING_LOGGING_H
#define QPID_MESSAGING_LOGGING_H

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

#include "qpid/messaging/ImportExport.h"

#include <string>

namespace qpid {
namespace messaging {
/**
 * These log levels need to be kept in sync with the log levels
 * defined internally in qpid::log (but I don't think they are likely
 * to change anyway
 */
enum Level { trace, debug, info, notice, warning, error, critical };

/** \ingroup messaging
 * Interface class to allow redirection of log output
 */
class QPID_MESSAGING_CLASS_EXTERN LoggerOutput
{
public:
    QPID_MESSAGING_EXTERN virtual ~LoggerOutput();

    /**
     * Override this member function to receive log messages.
     *
     * log() will be called for every log message that would be output from the qpid::messaging
     * logging subsystem after applying the specified logging filters.
     *
     * The logging subsystem ensures that log() will not be called simultaneously in different threads.
     * @param level The severity of the log message can be (in order of severity)
     *                trace, debug, info, notice, warning, error, critical
     * @param user Flag which is set if the log message came from the user application ( using qpid::messaging::Logger::log() )
     *              (if not set then the message comes from the qpid library)
     * @param file The source code file name reported as the origin of the log message
     * @param line The source code line number reported as the origin of the log message
     * @param function The source code function reported as the origin of the log message
     * @param message The log message
     */
    virtual void log(Level level, bool user, const char* file, int line, const char* function, const std::string& message) = 0;
};

/** \ingroup messaging
 * A utility class to allow the application to control the logging
 * output of the qpid messaging library
 *
 * This class represents a singleton logging facility within the qpid messaging library so there are only static
 * methods in the class
 */
class QPID_MESSAGING_CLASS_EXTERN Logger
{
public:
    /**
     * Configure the logging subsystem
     *
     * This function takes an array of text options (which could easily come from a programs
     * command line) and uses them to configure the logging subsystem.
     *
     * If the prefix parameter is specified then the accepted command line options are prefixed
     * by <<prefix>>- for example if the prefix is "qpid" then the options all start "--qpid-log..."
     *
     * Accepted options are:
     * --log-enable RULE
     * --log-disable RULE
     *
     * Both --log-enable and --log-disable can be specified multiple times in a single command line.
     * The enables are acted upon and after them the disables are acted upon.
     *
     * RULE is in the form LEVEL[("+"|"-")][:PATTERN]
     * LEVEL is one of "trace", "debug", "info", "notice", "warning", "error", "critical"
     * "+" operates on the level and all higher levels
     * "-" operates on the level and all lower levels
     * PATTERN is a category name or a fragment of a fully namespace qualified function (Case sensitive).
     *
     * --log-to-stdout ("on"|"off|"0"|"1")
     * --log-to-stderr ("on"|"off|"0"|"1")
     * --log-to-file FILENAME
     *
     * These options control where the qpid logging subsystem sends log messages
     *
     * --log-time ("on"|"off|"0"|"1")
     * --log-level ("on"|"off|"0"|"1")
     * --log-source ("on"|"off|"0"|"1")
    * --log-thread ("on"|"off|"0"|"1")
     * --log-function ("on"|"off|"0"|"1")
     * --log-hires-timestamp ("on"|"off|"0"|"1")
     *
     * These options control what information is included in the logging message sent by the logging subsystem.
     *
     * @param argc count of options - identical to meaning for main().
     * @param argv array of pointers to options - identical to meaning for main().
     * @param prefix (optional) If present prefix all logging options with this string
     * @throws MessagingException if it cannot parse an option it recognises
     */
    QPID_MESSAGING_EXTERN static void configure(int argc, const char* argv[], const std::string& prefix=std::string());

    /**
     * Get a user friendly usage message.
     *
     * This returns a usage message that is suitable for outputting directly to
     * a console user. The message only contains the command line options that
     * are understood by qpid::messaging::Logger::configure().
     *
     * NB. You must call qpid::messaging::Logger::configure() before calling this
     * to populate the usage string as the usage string depends on the prefix that
     * is passed in to qpid::messaging::Logger::configure().
     *
     * @return string containing the usage message for the command line options
     */
    QPID_MESSAGING_EXTERN static std::string usage();

    /**
     * Register a custom handler for log messages
     *
     * This allows application programs to intercept the log messages coming from qpid::messaging
     * and handle them in whatever way is consonent with the applications own handling of
     * log messages.
     *
     * In order to do this create a class that inherits from qpid::messaging::LoggerOutput
     * and override the log() member function.
     */
    QPID_MESSAGING_EXTERN static void setOutput(LoggerOutput& output);

    /**
     * Output a log message. This will get sent to all the specified logging outputs including any
     * the application has registered. The message will get filtered along with the internal messages
     * according to the specified logging filters.
     *
     * When a log message output using log() is received by a LoggerOutput::log() method the "user" bool parameter will be set true.
     */
    QPID_MESSAGING_EXTERN static void log(Level level, const char* file, int line, const char* function, const std::string& message);

private:
    //This class has only one instance so no need to copy
    Logger();
    ~Logger();

    Logger(const Logger&);
    Logger operator=(const Logger&);
};
}} // namespace qpid::messaging

#endif  /*!QPID_MESSAGING_LOGGING_H*/
