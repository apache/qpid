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

/**
 * Like boost::program_options::value() with more convenient signature
 * for updating a value by reference and nicer help formatting.
 * 
 *@param value displayed as default in help, updated from options.
 * Must support ostream << operator.
 *@param arg name for arguments  in help.
 *
 *@see CommonOptions.cpp for example of use.
 */ 
template<class T>
value_semantic* optValue(T& value, const char* arg) {
    std::string val(boost::lexical_cast<std::string>(value));
    std::string argName(
        val.empty() ? std::string(arg) :
        (boost::format("%s (=%s) ") % arg % val).str());
    return new OptionValue<T>(value, argName);
}

/** Environment-to-option name mapping.
 * Maps env variable "QPID_SOME_VAR" to option "some-var"
 * Ignores env vars that dont match known options._
 */
struct EnvMapper {
    EnvMapper(const options_description& o) : opts(o) {}
    std::string operator()(const std::string& env);
    const options_description& opts;
};

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

} // namespace qpid

#endif  /*!QPID_COMMONOPTIONS_H*/
