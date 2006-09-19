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

#ifndef __LOGGER__
#define __LOGGER__

#include <fstream>
#include <iostream>

namespace qpid {
namespace utils {

class Logger : public std::ofstream
{
    private:
        bool echo_flag;
        bool timestamp_flag;
        bool eol_flag;
        char buff[128]; // Buffer for writing formatted strings
        
        void write_timestamp();

    public:
        Logger(const char* filename, const bool append);
        Logger(std::string& filename, const bool append);
        ~Logger();

        bool getEchoFlag() {return echo_flag;}
        bool setEchoFlag(const bool _echo_flag) {echo_flag = _echo_flag;}
        bool getTimestampFlag() {return timestamp_flag;}
        bool setTimestampFlag(const bool _timestamp_flag) {timestamp_flag = _timestamp_flag;}

        void log(const char* message);
        void log(const char* message, const bool echo);
        void log(const char* message, const bool echo, const bool timestamp);

        Logger& operator<< (bool b);
        Logger& operator<< (const short s);
        Logger& operator<< (const unsigned short us);
        Logger& operator<< (const int i);
        Logger& operator<< (const unsigned int ui);
        Logger& operator<< (const long l);
        Logger& operator<< (const unsigned long ul);
        Logger& operator<< (const long long l);
        Logger& operator<< (const unsigned long long ul);
        Logger& operator<< (const float f);
        Logger& operator<< (const double d);
        Logger& operator<< (const long double ld);
        Logger& operator<< (const char* cstr);
        Logger& operator<< (const std::string& str);
};

}
}


#endif
