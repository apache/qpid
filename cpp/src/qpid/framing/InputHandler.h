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

#ifndef _InputHandler_
#define _InputHandler_

#include "qpid/framing/AMQFrame.h"

namespace qpid {
namespace framing {

    class InputHandler{
    public:
        virtual ~InputHandler();
	virtual void received(AMQFrame* frame) = 0;
    };

}
}


#endif
