#ifndef QPID_IMPORTEXPORT_H
#define QPID_IMPORTEXPORT_H

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

//
// This header file defines the following macros for the control of library/DLL
// import and export:
//
// QPID_EXPORT         - Export declaration for Methods
// QPID_CLASS_EXPORT   - Export declaration for Classes
// QPID_INLINE_EXPORT  - Export declaration for Inline methods
//
// QPID_IMPORT         - Import declaration for Methods
// QPID_CLASS_IMPORT   - Import declaration for Classes
// QPID_INLINE_IMPORT  - Import declaration for Inline methods
//

#if defined(WIN32) && !defined(QPID_DECLARE_STATIC)
   //
   // Import and Export definitions for Windows:
   //
#  define QPID_EXPORT __declspec(dllexport)
#  define QPID_IMPORT __declspec(dllimport)
#  ifdef _MSC_VER
     //
     // Specific to the Microsoft compiler:
     //
#    define QPID_CLASS_EXPORT
#    define QPID_CLASS_IMPORT
#    define QPID_INLINE_EXPORT QPID_EXPORT
#    define QPID_INLINE_IMPORT QPID_IMPORT
#  else
     //
     // Specific to non-Microsoft compilers (mingw32):
     //
#    define QPID_CLASS_EXPORT QPID_EXPORT
#    define QPID_CLASS_IMPORT QPID_IMPORT
#    define QPID_INLINE_EXPORT
#    define QPID_INLINE_IMPORT
#  endif
#else
   //
   // Non-Windows (Linux, etc.) definitions:
   //
#if __GNUC__ >= 4
#  define QPID_EXPORT __attribute ((visibility ("default")))
#else
#  define QPID_EXPORT
#endif
#  define QPID_IMPORT
#  define QPID_CLASS_EXPORT QPID_EXPORT
#  define QPID_CLASS_IMPORT QPID_IMPORT
#  define QPID_INLINE_EXPORT QPID_EXPORT
#  define QPID_INLINE_IMPORT QPID_IMPORT
#endif

#endif  /*!QPID_IMPORTEXPORT_H*/
