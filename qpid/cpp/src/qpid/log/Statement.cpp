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

#include "Statement.h"
#include "Logger.h"
#include <stdexcept>
#include <syslog.h>

namespace qpid {
namespace log {

void Statement::log(const std::string& message) {
    Logger::instance().log(*this,message);
}

Statement::Initializer::Initializer(Statement& s) : statement(s) {
    Logger::instance().add(s);
}

namespace {
const char* names[LevelTraits::COUNT] = {
    "trace", "debug", "info", "notice", "warning", "error", "critical"
};

int priorities[LevelTraits::COUNT] = {
    LOG_DEBUG, LOG_DEBUG, LOG_INFO, LOG_NOTICE,
    LOG_WARNING, LOG_ERR, LOG_CRIT
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

int LevelTraits::priority(Level l) {
    return priorities[l];
}

}} // namespace qpid::log
