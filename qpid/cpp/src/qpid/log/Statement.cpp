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

#include "qpid/log/Statement.h"
#include "qpid/log/Logger.h"
#include <boost/bind.hpp>
#include <stdexcept>
#include <algorithm>
#include <ctype.h>

namespace qpid {
std::string quote(const std::string& str); // Defined in Msg.cpp
namespace log {

void Statement::log(const std::string& message) {
    Logger::instance().log(*this, quote(message));
}

Statement::Initializer::Initializer(Statement& s) : statement(s) {
    Logger::instance().add(s);
}

namespace {
const char* names[LevelTraits::COUNT] = {
    "trace", "debug", "info", "notice", "warning", "error", "critical"
};

} // namespace

Level LevelTraits::level(const char* name) {
    for (int i =0; i < LevelTraits::COUNT; ++i) {
        if (strcmp(names[i], name)==0)
            return Level(i);
    }
    throw std::runtime_error(std::string("Invalid log level name: ")+name);
}

const char* LevelTraits::name(Level l) {
    return names[l];
}

}} // namespace qpid::log
