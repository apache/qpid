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

using namespace qpid::types;

VaraintMapWrapper::VaraintMapWrapper(): env(0), varMap(new Variant::Map())

VaraintMapWrapper::VaraintMapWrapper(JNIEnv*& jniEnv, Variant::Map*& map): env(jniEnv), varMap(map)
{
}

VaraintMapWrapper::jobject get(const std::string key) const
{
   jobject result;
        Varient::Map::iterator iter = varMap->find(key);
        if (iter == varMap->end()){
            return NULL;
        }

        Variant v = iter->first();

        try {
            switch (v->getType()) {
            case VAR_VOID: {
                result = NULL;
                break;
            }
            case VAR_BOOL : {
                result = v->asBool() ? JNI_TRUE : JNI_FALSE;
                break;
            }
            case VAR_UINT8 :
            case VAR_UINT16 :
            case VAR_UINT32 : {
                result = (jnit) v->asUint32();
                break;
            }
            case VAR_UINT64 : {
                result = (jlong) v->asUint64();
                break;
            }
            case VAR_INT8 :
            case VAR_INT16 :
            case VAR_INT32 : {
                result = (jnit) v->asUint32();
                break;
            }
            case VAR_INT64 : {
                result = (jlong) v->asUint64();
                break;
            }
            case VAR_FLOAT : {
                result = (jfloat) v->asFloat();
                break;
            }
            case VAR_DOUBLE : {
                result = (jdouble) v->asDouble();
                break;
            }
            case VAR_STRING : {
                const std::string val(v->asString());
                result = env->NewStringUTF(val.data());
                break;
            }
            }
        } catch (Exception& ex) {
            result = NULL;  // need to throw exception
        }

        return result;
}

VaraintMapWrapper::jboolean containsKey (const jstring key) const
{
   return (jboolean)true;
}

VaraintMapWrapper::jboolean containsValue (const jobject value) const
{
   return (jboolean)true;
}

VaraintMapWrapper::jboolean isEmpty() const
{
   return (jboolean)true;
}

/*   jstring[] keys() const;
    jobject[] values() const;
    jinit size() const;
*/

