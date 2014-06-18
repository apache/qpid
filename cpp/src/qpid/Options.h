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

#include "qpid/Exception.h"

// Disable warnings triggered by boost.
#ifdef _MSC_VER
#  pragma warning(push)
#  pragma warning(disable : 4251 4275)
#endif

#include <boost/lexical_cast.hpp>
#include <boost/shared_ptr.hpp>

#ifdef _MSC_VER
#  pragma warning(pop)
#endif

#include <sstream>
#include <iterator>
#include <algorithm>
#include <string>
#include <vector>
#include <iosfwd>

#include "qpid/CommonImportExport.h"

namespace boost {
namespace program_options {
class value_semantic;
class option_description;
class options_description;
}}

namespace qpid {
namespace po=boost::program_options;



///@internal
QPID_COMMON_EXTERN std::string prettyArg(const std::string&, const std::string&);

///@internal
template <class T>
po::value_semantic* create_value(T& val, const std::string& arg);

/** Create an option value.
 * name, value appear after the option name in help like this:
 * <name> (=<value>)
 * T must support operator <<.
 *@see Options for example of use.
 */
template<class T>
po::value_semantic* optValue(T& value, const char* name) {
    std::string valstr(boost::lexical_cast<std::string>(value));
    return create_value(value, prettyArg(name, valstr));
}

/** Create a vector value. Multiple occurences of the option are
 * accumulated into the vector
 */
template <class T>
po::value_semantic* optValue(std::vector<T>& value, const char* name) {
    std::ostringstream os;
    std::copy(value.begin(), value.end(), std::ostream_iterator<T>(os, " "));
    std::string val=os.str();
    if (!val.empty())
        val.erase(val.end()-1); // Remove trailing " "
    return create_value(value, prettyArg(name, val));
}

/** Create a boolean switch value. Presence of the option sets the value. */
QPID_COMMON_EXTERN po::value_semantic* optValue(bool& value);
QPID_COMMON_EXTERN po::value_semantic* pure_switch(bool& value);

/**
 * Base class for options.
 * Example of use:
 @code
 struct MySubOptions : public Options {
   int x;
   string y;
   MySubOptions() : Options("Sub options") {
     addOptions()
     ("x", optValue(x,"XUNIT"), "Option X")
     ("y", optValue(y, "YUNIT"), "Option Y");
   }
 };

 struct MyOptions : public Options {
   bool z;
   vector<string> foo;
   MySubOptions subOptions;
   MyOptions() : Options("My Options") {
    addOptions()
      ("z", boolSwitch(z), "Option Z")
      ("foo", optValue(foo), "Multiple option foo");
    add(subOptions);
 }

 main(int argc, char** argv) {
   Options opts;
   opts.parse(argc, char** argv);
   // Use values
   dosomething(opts.subOptions.x);
   if (error)
     cout << opts << end;       // Help message.
 }

 @endcode
 */


class options_description_easy_init {
public:
    QPID_COMMON_EXTERN options_description_easy_init(po::options_description* o);

    QPID_COMMON_EXTERN options_description_easy_init&
    operator()(const char* name,
               const po::value_semantic* s,
               const char* description);

private:
    po::options_description* owner;
};


struct Options {
    friend QPID_COMMON_EXTERN std::ostream& operator<<(std::ostream& os, const Options&);

    struct Exception : public qpid::Exception {
        Exception(const std::string& msg) : qpid::Exception(msg) {}
    };

    QPID_COMMON_EXTERN explicit Options(const std::string& name=std::string());

    /**
     * Parses options from argc/argv, environment variables and config file.
     * Note the filename argument can reference an options variable that
     * is updated by argc/argv or environment variable parsing.
     */
    QPID_COMMON_EXTERN void parse(int argc, char const* const* argv,
               const std::string& configfile=std::string(),
               bool  allowUnknown = false);

    /**
     * Tests for presence of argc/argv switch
     */
    QPID_COMMON_EXTERN bool findArg(int argc, char const* const* argv,
                                       const std::string& theArg);

    QPID_COMMON_EXTERN options_description_easy_init addOptions();
    QPID_COMMON_EXTERN void add(Options&);
    QPID_COMMON_EXTERN const std::vector< boost::shared_ptr<po::option_description> >& options() const;
    QPID_COMMON_EXTERN bool find_nothrow(const std::string&, bool);
    QPID_COMMON_EXTERN void print(std::ostream& os);

private:
    boost::shared_ptr<po::options_description> poOptions;
};

QPID_COMMON_EXTERN std::ostream& operator<<(std::ostream& os, const Options&);

/**
 * Standard options for configuration
 */
struct CommonOptions : public Options {
    QPID_COMMON_EXTERN CommonOptions(const std::string& name=std::string(),
                                     const std::string& configfile=std::string(),
                                     const std::string& clientConfigFile=std::string());
    bool help;
    bool version;
    std::string config;
    std::string clientConfig;
};




} // namespace qpid

#endif  /*!QPID_COMMONOPTIONS_H*/
