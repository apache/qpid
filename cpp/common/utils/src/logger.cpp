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
/*********************************************************************
*
* NOTE: This is a lightweight logging class intended for debugging and
* verification purposes only.
*
* DO NOT USE FOR PRODUCT DEVELOPMENT - Rather, use an agreed upon
* established logging  class (such as Apache's log4cxx) for product
* development purposes.
*
*********************************************************************/

#include <iostream>
#include <ostream>
#include <string.h>
#include "apr_time.h"
#include "logger.h"

namespace qpid {
namespace utils {

Logger::Logger(const char* filename, const bool append):
    std::ofstream(filename, append ? std::ios::app : std::ios::out)
{
    echo_flag = false;
    timestamp_flag = true;
    eol_flag = true;
}

Logger::Logger(std::string& filename, const bool append):
    std::ofstream(filename.c_str(), append ? std::ios::app : std::ios::out)
{
    echo_flag = false;
    timestamp_flag = true;
    eol_flag = true;
}

Logger::~Logger()
{
    close();
}

void Logger::write_timestamp()
{
    int len;
    apr_time_exp_t now;
    apr_time_exp_lt(&now, apr_time_now());
    sprintf(buff, "%4d/%02d/%02d %02d:%02d:%02d.%06d : ", 1900+now.tm_year, now.tm_mon,
            now.tm_mday, now.tm_hour, now.tm_min, now.tm_sec, now.tm_usec);
    write(buff, strlen(buff));
}


void Logger::log(const char* message)
{
    if (timestamp_flag && eol_flag)
    {
        eol_flag = false;
        write_timestamp();
    }
    write(message, strlen(message));
    if (echo_flag)
        std::cout << message;
    if (strchr(message, '\n'))
        eol_flag = true;
}

void Logger::log(const char* message, const bool echo)
{
    if (timestamp_flag && eol_flag)
    {
        eol_flag = false;
        write_timestamp();
    }
    write(message, strlen(message));
    if (echo)
        std::cout << message;    
    if (strchr(message, '\n'))
        eol_flag = true;
}

void Logger::log(const char* message, const bool echo, const bool timestamp)
{
    if (timestamp && eol_flag)
    {
        eol_flag = false;
        write_timestamp();
    }
    write(message, strlen(message));
    if (echo)
        std::cout << message;
    if (strchr(message, '\n'))
        eol_flag = true;
}

Logger& Logger::operator<< (const bool b)
{
    log(b ? "true" : "false");
    return *this;
}

Logger& Logger::operator<< (const short s)
{
    sprintf(buff, "%d", s);
    log(buff);
    return *this;
}

Logger& Logger::operator<< (const unsigned short us)
{
    sprintf(buff, "%u", us);
    log(buff);
    return *this;
}

Logger& Logger::operator<< (const int i)
{
    sprintf(buff, "%d", i);
    log(buff);
    return *this;
}

Logger& Logger::operator<< (const unsigned int ui)
{
    sprintf(buff, "%u", ui);
    log(buff);
    return *this;
}

Logger& Logger::operator<< (const long l)
{
    sprintf(buff, "%ld", l);
    log(buff);
    return *this;
}

Logger& Logger::operator<< (const unsigned long ul)
{
    sprintf(buff, "%lu", ul);
    log(buff);
    return *this;
}

Logger& Logger::operator<< (const long long l)
{
    sprintf(buff, "%ld", l);
    log(buff);
    return *this;
}

Logger& Logger::operator<< (const unsigned long long ul)
{
    sprintf(buff, "%lu", ul);
    log(buff);
    return *this;
}

Logger& Logger::operator<< (const float f)
{
    sprintf(buff, "%f", f);
    log(buff);
    return *this;
}

Logger& Logger::operator<< (const double d)
{
    sprintf(buff, "%lf", d);
    log(buff);
    return *this;
}

Logger& Logger::operator<< (const long double ld)
{
    sprintf(buff, "%Lf", ld);
    log(buff);
    return *this;
}

Logger& Logger::operator<< (const char* cstr)
{
    log(cstr);
    return *this;
}

Logger& Logger::operator<< (const std::string& str)
{
    log(str.c_str());
    return *this;
}

}
}

