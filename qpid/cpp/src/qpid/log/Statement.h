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

#include "qpid/Msg.h"
#include "qpid/CommonImportExport.h"
#include <boost/current_function.hpp>

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
};

/** Formal message categories
 * https://issues.apache.org/jira/browse/QPID-3902
 *
 * Category    Source code directory
 * --------    ---------------------
 * Security    acl ssl gssapi sasl cyrus
 * Broker      broker
 * Management  agent console qmf
 * Protocol    amqp_0_10 framing
 * System      log sys types xml thread mutex fork pipe time ...
 * HA          cluster ha replication
 * Messaging   messaging
 * Client      client
 * Store       store
 * Network     tcp rdma AsynchIO socket epoll
 * Test
 * External_application <no directory - signifies log message from non qpid application code>
 * Model       <not related to a directory>
 * Unspecified <must be last in enum>
 */
enum Category { security, broker, management, protocol, system, ha, messaging,
    store, network, test, client, external_application, model, unspecified };
struct CategoryTraits {
    static const int COUNT=unspecified+1;

    /** Test if given name is a Category name
     */
    static bool isCategory(const std::string& name);

    /** Get category from string name
     * @exception if name invalid.
     */
    static Category category(const char* name);

    /** Get category from string name.
     * @exception if name invalid.
     */
    static  Category category(const std::string& name) {
        return category(name.c_str());
    }

    /** String name of category */
    static const char* name(Category);
};

 /** POD struct representing a logging statement in source code. */
struct Statement {
    bool enabled;
    const char* file;
    int line;
    const char* function;
    Level level;
    Category category;

    QPID_COMMON_EXTERN void log(const std::string& message);
    QPID_COMMON_EXTERN static void categorize(Statement& s);

    struct Initializer {
        QPID_COMMON_EXTERN Initializer(Statement& s);
        QPID_COMMON_EXTERN ~Initializer();
        Statement& statement;
    };
};

///@internal static initializer for a Statement.
#define QPID_LOG_STATEMENT_INIT_CAT(LEVEL, CATEGORY) \
{ 0, __FILE__, __LINE__,  BOOST_CURRENT_FUNCTION, (::qpid::log::LEVEL), \
(::qpid::log::CATEGORY) }


///@internal static initializer for a Statement with unspecified category
#define QPID_LOG_STATEMENT_INIT(LEVEL) \
QPID_LOG_STATEMENT_INIT_CAT ( LEVEL , unspecified )

/**
 * Like QPID_LOG but computes an additional boolean test expression
 * to determine if the message should be logged. Evaluation of both
 * the test and  message expressions occurs only if the requested log level
 * is enabled.
 *@param LEVEL severity Level for message, should be one of:
 * debug, info, notice, warning, error, critical. NB no qpid::log:: prefix.
 *@param TEST message is logged only if expression TEST evaluates to true.
 *@param MESSAGE any object with an @eostream operator<<, or a sequence
 * like of ostreamable objects separated by @e<<.
 */
#define QPID_LOG_IF(LEVEL, TEST, MESSAGE)                       \
    do {                                                        \
        using ::qpid::log::Statement;                           \
        static Statement stmt_= QPID_LOG_STATEMENT_INIT(LEVEL); \
        static Statement::Initializer init_(stmt_);             \
        if (stmt_.enabled && (TEST))                            \
            stmt_.log(::qpid::Msg() << MESSAGE);                \
    } while(0)

/**
 * Line QPID_LOG_IF but with the additional specification of a category.
 * @param CATEGORY message category.
 */
#define QPID_LOG_IF_CAT(LEVEL, CATEGORY, TEST, MESSAGE)         \
    do {                                                        \
        using ::qpid::log::Statement;                           \
        static Statement stmt_= QPID_LOG_STATEMENT_INIT_CAT(LEVEL, CATEGORY); \
        static Statement::Initializer init_(stmt_);             \
        if (stmt_.enabled && (TEST))                            \
            stmt_.log(::qpid::Msg() << MESSAGE);                \
    } while(0)

/**
 * FLAG must be a boolean variable. Assigns FLAG to true iff logging
 * is enabled for LEVEL in the calling context.  Use when extra
 * support code is needed to generate log messages, to ensure that it
 * is only run if the logging level is enabled.
 * e.g.
 * bool logWarning;
 * QPID_LOG_TEST(warning, logWarning);
 * if (logWarning) { do stuff needed for warning log messages }
 */
#define QPID_LOG_TEST(LEVEL, FLAG)                              \
    do {                                                        \
        using ::qpid::log::Statement;                           \
        static Statement stmt_= QPID_LOG_STATEMENT_INIT(LEVEL); \
        static Statement::Initializer init_(stmt_);             \
        FLAG = stmt_.enabled;                                   \
    } while(0)

    /**
     * FLAG must be a boolean variable. Assigns FLAG to true iff logging
     * is enabled for LEVEL in the calling context.  Use when extra
     * support code is needed to generate log messages, to ensure that it
     * is only run if the logging level is enabled.
     * e.g.
     * bool logWarning;
     * QPID_LOG_TEST_CAT(warning, System, logWarning);
     * if (logWarning) { do stuff needed for warning log messages }
     */
    #define QPID_LOG_TEST_CAT(LEVEL, CATEGORY, FLAG)                \
    do {                                                        \
        using ::qpid::log::Statement;                           \
        static Statement stmt_= QPID_LOG_STATEMENT_INIT_CAT(LEVEL, CATEGORY); \
        static Statement::Initializer init_(stmt_);             \
        FLAG = stmt_.enabled;                                   \
    } while(0)

/**
 * Macro for log statements. Example of use:
 * @code
 * QPID_LOG(debug, "There are " << foocount << " foos in the bar.");
 * QPID_LOG(error, boost::format("Dohickey %s exploded") % dohicky.name());
 * @endcode
 * Using QPID_LOG implies a category of Unspecified.
 *
 * You can subscribe to log messages by level, by component, by filename
 * or a combination @see Configuration.

 *@param LEVEL severity Level for message, should be one of:
 * debug, info, notice, warning, error, critical. NB no qpid::log:: prefix.
 *@param MESSAGE any object with an @eostream operator<<, or a sequence
 * like of ostreamable objects separated by @e<<.
 */
#define QPID_LOG(LEVEL, MESSAGE) QPID_LOG_IF(LEVEL, true, MESSAGE);

/**
 * Macro for log statements. Example of use:
 * @code
 * QPID_LOG_CAT(debug, System, "There are " << foocount << " foos in the bar.");
 * QPID_LOG_CAT(error, System, boost::format("Dohickey %s exploded") % dohicky.name());
 * @endcode
 * Using QPID_LOG_CAT requires the specification of a category.
 *
 * You can subscribe to log messages by level, by component, by filename
 * or a combination @see Configuration.
 *
 *@param LEVEL severity Level for message, should be one of:
 * debug, info, notice, warning, error, critical. NB no qpid::log:: prefix.
 *@param CATEGORY basic Category for the message.
 *@param MESSAGE any object with an @eostream operator<<, or a sequence
 * like of ostreamable objects separated by @e<<.
 */
#define QPID_LOG_CAT(LEVEL, CATEGORY, MESSAGE) QPID_LOG_IF_CAT(LEVEL, CATEGORY, true, MESSAGE);

}} // namespace qpid::log




#endif  /*!STATEMENT_H*/

