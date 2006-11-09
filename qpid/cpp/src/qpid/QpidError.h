#ifndef __QpidError__
#define __QpidError__
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
#include <string>
#include <qpid/Exception.h>

namespace qpid {

    class QpidError : public Exception { 
      public:
        const int code;
        const std::string msg;
        const std::string file;
        const int line;

        QpidError(int _code, const std::string& _msg, const std::string& _file, int _line) throw();
        ~QpidError() throw();
    };

#define THROW_QPID_ERROR(A, B) throw QpidError(A, B, __FILE__, __LINE__)

}

#define PROTOCOL_ERROR 10000
#define APR_ERROR 20000
#define FRAMING_ERROR 30000
#define CLIENT_ERROR 40000
#define INTERNAL_ERROR 50000

#endif
