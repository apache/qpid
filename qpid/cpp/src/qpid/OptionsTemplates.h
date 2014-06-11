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

#include <boost/program_options.hpp>

#include <string>

#include "qpid/CommonImportExport.h"

namespace qpid {

namespace po=boost::program_options;

template <class T>
class OptValue : public po::typed_value<T> {
public:
  OptValue(T& val, const std::string& arg) :
      po::typed_value<T>(&val),
      argName(arg)
  {}
  std::string name() const { return argName; }

private:
  std::string argName;
};

template <class T>
po::value_semantic* create_value(T& val, const std::string& arg) {
    return new OptValue<T>(val, arg);
}

template <class T>
po::value_semantic* create_value(T& val, const std::string& arg, const T& implicit_val) {
  return (new OptValue<T>(val, arg))->implicit_value(implicit_val);
}

}
