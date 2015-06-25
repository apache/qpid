#ifndef QMF_SCHEMA_PROPERTY_H
#define QMF_SCHEMA_PROPERTY_H
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
#include "qpid/types/Uuid.h"
#include "qpid/types/Variant.h"
#include "qmf/SchemaTypes.h"
#include <string>

namespace qmf {

#ifndef SWIG
    template <class> class PrivateImplRef;
#endif

    class SchemaPropertyImpl;

    class QMF_CLASS_EXTERN SchemaProperty : public Handle<SchemaPropertyImpl> {
    public:
        QMF_EXTERN SchemaProperty(SchemaPropertyImpl* impl = 0);
        QMF_EXTERN SchemaProperty(const SchemaProperty&);
        QMF_EXTERN SchemaProperty& operator=(const SchemaProperty&);
        QMF_EXTERN ~SchemaProperty();

        QMF_EXTERN SchemaProperty(const std::string&, int, const std::string& o="");

        QMF_EXTERN void setAccess(int);
        QMF_EXTERN void setIndex(bool);
        QMF_EXTERN void setOptional(bool);
        QMF_EXTERN void setUnit(const std::string&);
        QMF_EXTERN void setDesc(const std::string&);
        QMF_EXTERN void setSubtype(const std::string&);
        QMF_EXTERN void setDirection(int);

        QMF_EXTERN const std::string& getName() const;
        QMF_EXTERN int getType() const;
        QMF_EXTERN int getAccess() const;
        QMF_EXTERN bool isIndex() const;
        QMF_EXTERN bool isOptional() const;
        QMF_EXTERN const std::string& getUnit() const;
        QMF_EXTERN const std::string& getDesc() const;
        QMF_EXTERN const std::string& getSubtype() const;
        QMF_EXTERN int getDirection() const;

#ifndef SWIG
    private:
        friend class qmf::PrivateImplRef<SchemaProperty>;
        friend struct SchemaPropertyImplAccess;
#endif
    };

}

#endif
