#ifndef QPID_STORE_MSSQL_SQLTRANSACTION_H
#define QPID_STORE_MSSQL_SQLTRANSACTION_H

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

#include <boost/shared_ptr.hpp>
#include <string>

namespace qpid {
namespace store {
namespace ms_sql {

class DatabaseConnection;

/**
 * @class SqlTransaction
 *
 * Class representing an SQL transaction.
 * Since ADO w/ SQLOLEDB can't do nested transaction via its BeginTrans(),
 * et al, nested transactions are carried out with direct SQL commands.
 * To ensure the state of this is known, keep track of how deeply the
 * transactions are nested. This is more of a safety/sanity check since
 * AMQP doesn't provide nested transactions.
 */
class SqlTransaction {

    boost::shared_ptr<DatabaseConnection> db;

    // Since ADO w/ SQLOLEDB can't do nested transaction via its BeginTrans(),
    // et al, nested transactions are carried out with direct SQL commands.
    // To ensure the state of this is known, keep track of how deeply the
    // transactions are nested.
    unsigned int transDepth;

public:
    SqlTransaction(const boost::shared_ptr<DatabaseConnection>& _db);
    ~SqlTransaction();

    DatabaseConnection *dbConn() { return db.get(); }

    void begin();
    void commit();
    void abort();
};

}}}  // namespace qpid::store::ms_sql

#endif /* QPID_STORE_MSSQL_SQLTRANSACTION_H */
