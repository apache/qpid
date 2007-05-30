#ifndef QPID_COMMONOPTIONS_H
#define QPID_COMMONOPTIONS_H

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

#include <boost/program_options.hpp>
#include <boost/format.hpp>
#include <sstream>
#include <iterator>
#include <algorithm>

namespace qpid { 

/**@Qpid extensions to boost::program_options */
namespace program_options {

using namespace boost::program_options;

/** @internal Normally only constructed by optValue() */
template <class T>
class OptionValue : public typed_value<T> {
  public:
    OptionValue(T& value, const std::string& arg)
        : typed_value<T>(&value), argName(arg) {}
    std::string name() const { return argName; }
  private:
    std::string argName;
};

///@internal
std::string prettyArg(const std::string& name, const std::string& value);

/**
 * Like boost::program_options::value() with more convenient signature
 * for updating a value by reference and prettier help formatting.
 * 
 *@param value displayed as default in help, updated from options.
 * Must support ostream << operator.
 *@param arg name for arguments  in help.
 *
 *@see CommonOptions.cpp for example of use.
 */ 
template<class T>
value_semantic* optValue(T& value, const char* name) {
    std::string valstr(boost::lexical_cast<std::string>(value));
    return new OptionValue<T>(value, prettyArg(name, valstr));
}

template <class T>
value_semantic* optValue(std::vector<T>& value, const char* name) {
    using namespace std;
    ostringstream os;
    copy(value.begin(), value.end(), ostream_iterator<T>(os, " "));
    string val=os.str();
    if (!val.empty())
        val.erase(val.end()-1); // Remove trailing " "
    return (new OptionValue<vector<T> >(value, prettyArg(name, val)));
}

/** Environment-to-option name mapping.
 * Maps env variable "QPID_SOME_VAR" to option "some-var"
 */
std::string env2option(const std::string& env);

/**
 * Like boost::program_options::bool_switch but takes reference, not pointer.
 */
inline value_semantic* optValue(bool& value) { return bool_switch(&value); }

} // namespace program_options

namespace po=program_options;   // Convenience alias.

/**
 * Common options for client and broker
 */
struct CommonOptions {
    static const int DEFAULT_PORT;

    CommonOptions();

    bool trace;
    int port;

    /** Add  members to program_options to be updated */
    void addTo(po::options_description&);

};

/** Convenience function to parse an options_description.
 * Parses argc/argv, environment variables and config file.
 * Note the filename argument can reference a variable that
 * is updated by argc/argv or environment variable parsing.
 */
void parseOptions(po::options_description&,
                  int argc, char** argv,
                  const std::string& filename=std::string());
    

} // namespace qpid

#endif  /*!QPID_COMMONOPTIONS_H*/
