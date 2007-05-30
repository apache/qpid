#ifndef STATEMENT_H
#define STATEMENT_H

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
#include <boost/current_function.hpp>
#include <sstream>

namespace qpid {
namespace log {

/** Debugging severity levels
 * - trace: High-volume debugging messages.
 * - debug: Debugging messages.
 * - info: Informational messages.
 * - notice: Normal but significant condition.
 * - warning: Warn of a possible problem.
 * - error: A definite error has occured. 
 * - critical: System in danger of severe failure.
 */
enum Level { trace, debug, info, notice, warning, error, critical };
struct LevelTraits {
    static const int COUNT=critical+1;

    /** Get level from string name.
     *@exception if name invalid.
     */
    static Level level(const char* name); 

    /** Get level from string name.
     *@exception if name invalid.
     */
    static  Level level(const std::string& name) {
        return level(name.c_str());
    }

    /** String name of level */
    static const char* name(Level); 

    /** Syslog priority of level */
    static int priority(Level);
};
    
/** POD struct representing a logging statement in source code. */
struct Statement {
    bool enabled;
    const char* file;
    int line;        
    const char* function;
    Level level;           

    void log(const std::string& message);

    struct Initializer {
        Initializer(Statement& s);
        ~Initializer();
        Statement& statement;
    };
};

///@internal trickery to make QPID_LOG_STRINGSTREAM work.
inline std::ostream& noop(std::ostream& s) { return s; }

///@internal static initializer for a Statement.
#define QPID_LOG_STATEMENT_INIT(level) \
    { 0, __FILE__, __LINE__,  BOOST_CURRENT_FUNCTION, (::qpid::log::level) }

///@internal Stream streamable message and return a string.
#define QPID_LOG_STRINGSTREAM(message) \
    static_cast<std::ostringstream&>( \
        std::ostringstream() << qpid::log::noop << message).str()

/**
 * Macro for log statements. Example of use:
 * @code
 * QPID_LOG(debug, "There are " << foocount << " foos in the bar.");
 * QPID_LOG(error, boost::format("Dohickey %s exploded") % dohicky.name());
 * @endcode
 *
 * All code with logging statements should be built with
 *   -DQPID_COMPONENT=<component name>
 * where component name is the name of the component this file belongs to.
 * 
 * You can subscribe to log messages by level, by component, by filename
 * or a combination @see Configuration.

 *@param LEVEL severity Level for message, should be one of:
 * debug, info, notice, warning, error, critical. NB no qpid::log:: prefix.
 *@param MESSAGE any object with an @eostream operator<<, or a sequence
 * like of ostreamable objects separated by @e<<.
 */
#define QPID_LOG(level, message)                                        \
    do {                                                                \
        static ::qpid::log::Statement stmt_= QPID_LOG_STATEMENT_INIT(level); \
        static ::qpid::log::Statement::Initializer init_(stmt_);        \
        if (stmt_.enabled)                                              \
            stmt_.log(QPID_LOG_STRINGSTREAM(message));                \
    } while(0)


}} // namespace qpid::log




#endif  /*!STATEMENT_H*/
 
