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

#include "Options.h"
#include "Statement.h"
#include "qpid/Options.h"
#include <map>
#include <string>
#include <algorithm>
#include <syslog.h>

namespace qpid {
namespace log {

using namespace std;

namespace {

class SyslogFacilities {
  public:
    typedef map<string, int> ByName;
    typedef map<int, string> ByValue;

    SyslogFacilities() {
        struct NameValue { const char* name; int value; };
        NameValue nameValue[] = {
            { "AUTH", LOG_AUTH },
            { "AUTHPRIV", LOG_AUTHPRIV },
            { "CRON", LOG_CRON },
            { "DAEMON", LOG_DAEMON },
            { "FTP", LOG_FTP },
            { "KERN", LOG_KERN },
            { "LOCAL0", LOG_LOCAL0 },
            { "LOCAL1", LOG_LOCAL1 },
            { "LOCAL2", LOG_LOCAL2 },
            { "LOCAL3", LOG_LOCAL3 },
            { "LOCAL4", LOG_LOCAL4 },
            { "LOCAL5", LOG_LOCAL5 },
            { "LOCAL6", LOG_LOCAL6 },
            { "LOCAL7", LOG_LOCAL7 },
            { "LPR", LOG_LPR },
            { "MAIL", LOG_MAIL },
            { "NEWS", LOG_NEWS },
            { "SYSLOG", LOG_SYSLOG },
            { "USER", LOG_USER },
            { "UUCP", LOG_UUCP }
        };
        for (size_t i = 0; i < sizeof(nameValue)/sizeof(nameValue[0]); ++i) {
            byName.insert(ByName::value_type(nameValue[i].name, nameValue[i].value));
            // Recognise with and without LOG_ prefix e.g.: AUTH and LOG_AUTH
            byName.insert(ByName::value_type(string("LOG_")+nameValue[i].name, nameValue[i].value));
            byValue.insert(ByValue::value_type(nameValue[i].value, string("LOG_")+nameValue[i].name));
        }
    };
    
    int value(const string& name) const {
        string key(name);
        transform(key.begin(), key.end(), key.begin(), ::toupper);        
        ByName::const_iterator i = byName.find(key);
        if (i == byName.end())
            throw Exception("Not a valid syslog facility: " + name);
        return i->second;
    }

    string name(int value) const {
        ByValue::const_iterator i = byValue.find(value);
        if (i == byValue.end())
            throw Exception("Not a valid syslog value: " + value);
        return i->second;
    }

  private:
    ByName byName;
    ByValue byValue;
};

}

ostream& operator<<(ostream& o, const SyslogFacility& f) {
    return o << SyslogFacilities().name(f.value);
}

istream& operator>>(istream& i, SyslogFacility& f) {
    std::string name;
    i >> name;
    f.value = SyslogFacilities().value(name);
    return i;
}

namespace {
std::string basename(const std::string path) {
    size_t i = path.find_last_of('/');
    return path.substr((i == std::string::npos) ? 0 : i+1);
}
}

Options::Options(const std::string& argv0, const std::string& name) :
    qpid::Options(name),
    time(true), level(true), thread(false), source(false), function(false), trace(false),
    syslogName(basename(argv0)), syslogFacility(LOG_DAEMON)
{
    outputs.push_back("stderr");
    selectors.push_back("error+");

    ostringstream levels;
    levels << LevelTraits::name(Level(0));
    for (int i = 1; i < LevelTraits::COUNT; ++i)
        levels << " " << LevelTraits::name(Level(i));
    
    addOptions()
        ("log-output", optValue(outputs, "FILE"), "Send log output to FILE. "
         "FILE can be a file name or one of the special values:\n"
         "stderr, stdout, syslog")
        ("trace,t", optValue(trace), "Enables all logging" )
        ("log-enable", optValue(selectors, "RULE"),
         ("Enables logging for selected levels and components. " 
          "RULE is in the form 'LEVEL[+][:PATTERN]' "
          "Levels are one of: \n\t "+levels.str()+"\n"
          "For example:\n"
          "\t'--log-enable warning+' "
          "logs all warning, error and critical messages.\n"
          "\t'--log-enable debug:framing' "
          "logs debug messages from the framing namespace. "
          "This option can be used multiple times").c_str())
        ("log-time", optValue(time, "yes|no"), "Include time in log messages")
        ("log-level", optValue(level,"yes|no"), "Include severity level in log messages")
        ("log-source", optValue(source,"yes|no"), "Include source file:line in log messages")
        ("log-thread", optValue(thread,"yes|no"), "Include thread ID in log messages")
        ("log-function", optValue(function,"yes|no"), "Include function signature in log messages")
        ("log-prefix", optValue(prefix,"STRING"), "Prefix to append to all log messages")
        ("syslog-name", optValue(syslogName, "NAME"), "Name to use in syslog messages")
        ("syslog-facility", optValue(syslogFacility,"LOG_XXX"), "Facility to use in syslog messages")
        ;
}        
        
}} // namespace qpid::log
