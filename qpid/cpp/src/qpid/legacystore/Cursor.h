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

#ifndef QPID_LEGACYSTORE_CURSOR_H
#define QPID_LEGACYSTORE_CURSOR_H

#include <boost/shared_ptr.hpp>
#include "db-inc.h"

namespace mrg{
namespace msgstore{

class Cursor
{
    Dbc* cursor;
public:
    typedef boost::shared_ptr<Db> db_ptr;

    Cursor() : cursor(0) {}
    virtual ~Cursor() { if(cursor) cursor->close(); }

    void open(db_ptr db, DbTxn* txn, u_int32_t flags = 0) { db->cursor(txn, &cursor, flags); }
    void close() { if(cursor) cursor->close(); cursor = 0; }
    Dbc* get() { return cursor; }
    Dbc* operator->() { return cursor; }
    bool next(Dbt& key, Dbt& value) { return cursor->get(&key, &value, DB_NEXT) == 0; }
    bool current(Dbt& key, Dbt& value) {  return cursor->get(&key, &value, DB_CURRENT) == 0; }
};

}}

#endif // ifndef QPID_LEGACYSTORE_CURSOR_H
