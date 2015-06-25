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

#include "qpid/log/Statement.h"
#include "qpid/messaging/Connection.h"
#include "qpid/messaging/exceptions.h"
#include "qpid/messaging/Logger.h"

#include <iostream>
#include <memory>
#include <stdexcept>

#include <vector>

#include "unit_test.h"

namespace qpid {
namespace tests {

QPID_AUTO_TEST_SUITE(MessagingLoggerSuite)

class StringLogger : public qpid::messaging::LoggerOutput {
    std::string& outString;

    void log(qpid::messaging::Level /*level*/, bool user, const char* /*file*/, int /*line*/, const char* /*function*/, const std::string& message){
        if (user) outString += "User ";
        outString += message;
    }

public:
    StringLogger(std::string& os) :
        outString(os)
    {}
};

#define SETUP_LOGGING(logger, ...) \
do {\
    const char* args[]={"", __VA_ARGS__, 0};\
    qpid::messaging::Logger::configure((sizeof (args)/sizeof (char*))-1, args);\
    logOutput.clear();\
    qpid::messaging::Logger::setOutput(logger);\
} while (0)
#define LOG_LEVEL(level)\
    QPID_LOG(level, #level " level output")
#define LOG_ALL_LOGGING_LEVELS \
do { \
    LOG_LEVEL(trace); \
    LOG_LEVEL(debug); \
    LOG_LEVEL(info); \
    LOG_LEVEL(notice); \
    LOG_LEVEL(warning); \
    LOG_LEVEL(critical); \
} while (0)
#define LOG_USER_LEVEL(level)\
    qpid::messaging::Logger::log(qpid::messaging::level, __FILE__, __LINE__, __FUNCTION__, #level " message")
#define LOG_ALL_USER_LOGGING_LEVELS \
do { \
    LOG_USER_LEVEL(trace); \
    LOG_USER_LEVEL(debug); \
    LOG_USER_LEVEL(info); \
    LOG_USER_LEVEL(notice); \
    LOG_USER_LEVEL(warning); \
    LOG_USER_LEVEL(critical); \
} while (0)

std::string logOutput;

QPID_AUTO_TEST_CASE(testLoggerLevels)
{
    StringLogger logger(logOutput);

    SETUP_LOGGING(logger, "--log-enable", "debug");
    LOG_ALL_LOGGING_LEVELS;
    BOOST_CHECK_EQUAL(logOutput, "debug level output\ncritical level output\n");

    SETUP_LOGGING(logger, "--log-enable", "trace+", "--log-disable", "notice");
    LOG_ALL_LOGGING_LEVELS;
    BOOST_CHECK_EQUAL(logOutput, "trace level output\ndebug level output\ninfo level output\nwarning level output\ncritical level output\n");

    SETUP_LOGGING(logger, "--log-enable", "info-");
    LOG_ALL_LOGGING_LEVELS;
    BOOST_CHECK_EQUAL(logOutput, "trace level output\ndebug level output\ninfo level output\ncritical level output\n");

    SETUP_LOGGING(logger, "--log-enable", "trace+", "--log-disable", "notice+");
    LOG_ALL_LOGGING_LEVELS;
    BOOST_CHECK_EQUAL(logOutput, "trace level output\ndebug level output\ninfo level output\ncritical level output\n");
}

QPID_AUTO_TEST_CASE(testUserLoggerLevels)
{
    StringLogger logger(logOutput);

    SETUP_LOGGING(logger, "--log-enable", "debug");
    LOG_ALL_USER_LOGGING_LEVELS;
    BOOST_CHECK_EQUAL(logOutput, "User debug message\nUser critical message\n");

    SETUP_LOGGING(logger, "--log-enable", "trace+", "--log-disable", "notice");
    LOG_ALL_USER_LOGGING_LEVELS;
    BOOST_CHECK_EQUAL(logOutput, "User trace message\nUser debug message\nUser info message\nUser warning message\nUser critical message\n");

    SETUP_LOGGING(logger, "--log-enable", "info-");
    LOG_ALL_USER_LOGGING_LEVELS;
    BOOST_CHECK_EQUAL(logOutput, "User trace message\nUser debug message\nUser info message\nUser critical message\n");

    SETUP_LOGGING(logger, "--log-enable", "trace+", "--log-disable", "notice+");
    LOG_ALL_USER_LOGGING_LEVELS;
    BOOST_CHECK_EQUAL(logOutput, "User trace message\nUser debug message\nUser info message\nUser critical message\n");

    SETUP_LOGGING(logger, "--log-disable", "trace+");
    LOG_ALL_LOGGING_LEVELS;
    LOG_ALL_USER_LOGGING_LEVELS;
    BOOST_CHECK_EQUAL(logOutput, "critical level output\nUser critical message\n");
}

QPID_AUTO_TEST_CASE(testLoggerUsage)
{
    qpid::messaging::Logger::configure(0, 0, "blah");
    std::string u = qpid::messaging::Logger::usage();

    BOOST_CHECK(!u.empty());
    BOOST_CHECK( u.find("--blah-log-enable")!=u.npos );
}

QPID_AUTO_TEST_CASE(testLoggerException)
{
    const char* args[]={"", "--blah-log-enable", "illegal", 0};
    BOOST_CHECK_THROW(qpid::messaging::Logger::configure(3, args, "blah"), qpid::messaging::MessagingException);
}

QPID_AUTO_TEST_SUITE_END()
}}
