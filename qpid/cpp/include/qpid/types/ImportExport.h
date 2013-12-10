#ifndef QPID_TYPES_IMPORTEXPORT_H
#define QPID_TYPES_IMPORTEXPORT_H

/*
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
 */

#include "qpid/ImportExport.h"

#if defined(TYPES_EXPORT) || defined (qpidtypes_EXPORTS)
#  define QPID_TYPES_EXTERN QPID_EXPORT
#  define QPID_TYPES_CLASS_EXTERN QPID_CLASS_EXPORT
#  define QPID_TYPES_INLINE_EXTERN QPID_INLINE_EXPORT
#else
#  define QPID_TYPES_EXTERN QPID_IMPORT
#  define QPID_TYPES_CLASS_EXTERN QPID_CLASS_IMPORT
#  define QPID_TYPES_INLINE_EXTERN QPID_INLINE_IMPORT
#endif

#endif  /*!QPID_TYPES_IMPORTEXPORT_H*/
