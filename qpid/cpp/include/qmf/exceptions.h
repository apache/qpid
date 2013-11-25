#ifndef QMF_EXCEPTIONS_H
#define QMF_EXCEPTIONS_H

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

#include "qmf/ImportExport.h"
#include "qpid/types/Exception.h"
#include "qpid/types/Variant.h"

namespace qmf {

/** \ingroup qmf
 */

    struct QMF_CLASS_EXTERN QmfException : public qpid::types::Exception {
        QMF_EXTERN QmfException(const std::string& msg);
        QMF_EXTERN virtual ~QmfException() throw();

        qpid::types::Variant::Map detail;
    };

    struct QMF_CLASS_EXTERN KeyNotFound : public QmfException {
        QMF_EXTERN KeyNotFound(const std::string& msg);
        QMF_EXTERN virtual ~KeyNotFound() throw();
    };

    struct QMF_CLASS_EXTERN IndexOutOfRange : public QmfException {
        QMF_EXTERN IndexOutOfRange();
        QMF_EXTERN virtual ~IndexOutOfRange() throw();
    };

    struct QMF_CLASS_EXTERN OperationTimedOut : public QmfException {
        QMF_EXTERN OperationTimedOut();
        QMF_EXTERN virtual ~OperationTimedOut() throw();
    };

}

#endif

