#ifndef QMF_QUERY_H
#define QMF_QUERY_H
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

#if !defined(QMF_USE_DEPRECATED_API) && !defined(qmf2_EXPORTS) && !defined(SWIG)
#  error "The API defined in this file has been DEPRECATED and will be removed in the future."
#  error "Define 'QMF_USE_DEPRECATED_API' to enable continued use of the API."
#endif

#include <qmf/ImportExport.h>
#include "qmf/Handle.h"
#include "qpid/types/Variant.h"
#include <string>

namespace qmf {

#ifndef SWIG
    template <class> class PrivateImplRef;
#endif

    class QueryImpl;
    class SchemaId;
    class DataAddr;

    enum QueryTarget {
    QUERY_OBJECT    = 1,
    QUERY_OBJECT_ID = 2,
    QUERY_SCHEMA    = 3,
    QUERY_SCHEMA_ID = 4
    };

    class QMF_CLASS_EXTERN Query : public qmf::Handle<QueryImpl> {
    public:
        QMF_EXTERN Query(QueryImpl* impl = 0);
        QMF_EXTERN Query(const Query&);
        QMF_EXTERN Query& operator=(const Query&);
        QMF_EXTERN ~Query();

        QMF_EXTERN Query(QueryTarget, const std::string& predicate="");
        QMF_EXTERN Query(QueryTarget, const std::string& className, const std::string& package, const std::string& predicate="");
        QMF_EXTERN Query(QueryTarget, const SchemaId&, const std::string& predicate="");
        QMF_EXTERN Query(const DataAddr&);

        QMF_EXTERN QueryTarget getTarget() const;
        QMF_EXTERN const DataAddr& getDataAddr() const;
        QMF_EXTERN const SchemaId& getSchemaId() const;
        QMF_EXTERN void setPredicate(const qpid::types::Variant::List&);
        QMF_EXTERN const qpid::types::Variant::List& getPredicate() const;
        QMF_EXTERN bool matchesPredicate(const qpid::types::Variant::Map& map) const;

#ifndef SWIG
    private:
        friend class qmf::PrivateImplRef<Query>;
        friend struct QueryImplAccess;
#endif
    };

}

#endif
