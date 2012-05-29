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

#include "qpid/types/Variant.h"
#include "jni.h"

#include <string>

jmap::jmap(JNIEnv*& jniEnv, qpid::types::Variant::Map*& map): env(jniEnv), varMap(map)
{
}

jmap::jobject get(const std::string key) const
{
   jobject result;
        qpid::types::Variant v = varMap[key];
        try {
            switch (v->getType()) {
            case qpid::types::VAR_VOID: {
                result = NULL;
                break;
            }
            case qpid::types::VAR_BOOL : {
                result = v->asBool() ? JNI_TRUE : JNI_FALSE;
                break;
            }
            case qpid::types::VAR_UINT8 :
            case qpid::types::VAR_UINT16 :
            case qpid::types::VAR_UINT32 : {
                result = (jnit) v->asUint32();
                break;
            }
            case qpid::types::VAR_UINT64 : {
                result = (jlong) v->asUint64();
                break;
            }
            case qpid::types::VAR_INT8 : 
            case qpid::types::VAR_INT16 :
            case qpid::types::VAR_INT32 : {
                result = (jnit) v->asUint32();
                break;
            }
            case qpid::types::VAR_INT64 : {
                result = (jlong) v->asUint64();
                break;
            }
            case qpid::types::VAR_FLOAT : {
                result = (jfloat) v->asFloat();
                break;
            }
            case qpid::types::VAR_DOUBLE : {
                result = (jdouble) v->asDouble();
                break;
            }
            case qpid::types::VAR_STRING : {
                const std::string val(v->asString());
                result = env->NewStringUTF(val.data());
                break;
            }
            }
        } catch (qpid::types::Exception& ex) {
            result = NULL;  // need to throw exception
        }

        return result;
}

jmap::void put (const jstring key, jobject object)
{

}



