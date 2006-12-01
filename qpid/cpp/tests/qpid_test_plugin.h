#ifndef _qpid_test_plugin_
#define _qpid_test_plugin_

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

/**
 * Convenience to include cppunit headers needed by qpid test plugins and
 * workaround for warning from superfluous main() declaration
 * in cppunit/TestPlugIn.h
 */

#include <cppunit/TestCase.h>
#include <cppunit/TextTestRunner.h>
#include <cppunit/extensions/HelperMacros.h>
#include <cppunit/plugin/TestPlugIn.h>

// Redefine CPPUNIT_PLUGIN_IMPLEMENT_MAIN to a dummy typedef to avoid warnings.
// 
#if defined(CPPUNIT_HAVE_UNIX_DLL_LOADER) || defined(CPPUNIT_HAVE_UNIX_SHL_LOADER)
#undef CPPUNIT_PLUGIN_IMPLEMENT_MAIN 
#define CPPUNIT_PLUGIN_IMPLEMENT_MAIN() typedef char __CppUnitPlugInImplementMainDummyTypeDef
#endif

#endif  /*!_qpid_test_plugin_*/
