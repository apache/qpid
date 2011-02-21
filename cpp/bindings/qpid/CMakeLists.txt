#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#


include(FindSWIG)
include(UseSWIG)
include(FindRuby)
include(FindPythonLibs)
include(FindPerlLibs)

if (SWIG_FOUND)
    set(CMAKE_SWIG_FLAGS "-w361,362,401,467,503")

    if (PYTHONLIBS_FOUND)
        add_subdirectory(python)
    endif (PYTHONLIBS_FOUND)

    if (RUBY_FOUND)
        add_subdirectory(ruby)
    endif (RUBY_FOUND)

    if (PERLLIBS_FOUND)
        add_subdirectory(perl)
    endif (PERLLIBS_FOUND)
endif (SWIG_FOUND)
