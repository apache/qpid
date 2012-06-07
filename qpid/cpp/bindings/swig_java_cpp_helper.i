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

/*
 * ======================== Contents on this file ===========================
 * 1. Static C++ Helper Methods.
 * 2. JNICALL JNI_OnLoad method for initializing global variables.
 * 2. Wrappers for C++ Objects (handwritten c++ classes).
 * 3. SWIG Interface definitions for the above classes.
 * ==========================================================================
 */




/*
 * ===================== Static C++ Helper Methods ==========================
 * Static helper methods in support of typemaps,
 * for converting between C++ and Java.
 * ==========================================================================
 */

%begin %{

/*
 * These are initialized during library loading via JNI_OnLoad.
 * This is done for performance reasons.
 * Thee jclass variables are marked as global references.
 * The jmethod variables remain valid until the corresponding jclass ref is valid.
 */
static JavaVM* cachedJVM=0;

static jclass JAVA_STRING_CLASS;

static jclass JAVA_BOOLEAN_CLASS;
static jmethodID JAVA_BOOLEAN_CTOR;
static jmethodID JAVA_BOOLEAN_VALUE_METHOD;

static jclass JAVA_BYTE_CLASS;
static jmethodID JAVA_BYTE_CTOR;
static jmethodID JAVA_BYTE_VALUE_METHOD;

static jclass JAVA_SHORT_CLASS;
static jmethodID JAVA_SHORT_CTOR;
static jmethodID JAVA_SHORT_VALUE_METHOD;

static jclass JAVA_INT_CLASS;
static jmethodID JAVA_INT_CTOR;
static jmethodID JAVA_INT_VALUE_METHOD;

static jclass JAVA_LONG_CLASS;
static jmethodID JAVA_LONG_CTOR;
static jmethodID JAVA_LONG_VALUE_METHOD;

static jclass JAVA_FLOAT_CLASS;
static jmethodID JAVA_FLOAT_CTOR;
static jmethodID JAVA_FLOAT_VALUE_METHOD;

static jclass JAVA_DOUBLE_CLASS;
static jmethodID JAVA_DOUBLE_CTOR;
static jmethodID JAVA_DOUBLE_VALUE_METHOD;

static jclass JAVA_ILLEGAL_ARGUMENT_EXP;
static jclass JAVA_JNI_LAYER_EXP;

static jobject createGlobalRef(JNIEnv* env,jobject obj)
{
    return env->NewGlobalRef(obj);
}

static jclass findClass(JNIEnv* env,const char* name)
{
    return env->FindClass(name);
}

static jmethodID getMethodID(JNIEnv* env, jclass clazz, const char* name, const char* sig)
{
    return env->GetMethodID(clazz,name,sig);
}

/*
 * ========================= init() for the module ==========================
 *
 * Pre loads class, method and except objects for performance.
 * WARNING *** This needs to be called immediately after loading the library.
 *
 * ==========================================================================
 */
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *jvm, void *reserved)
{
   cachedJVM = jvm;
   JNIEnv* env;
   if (jvm->GetEnv((void **)&env, JNI_VERSION_1_4)) {
         return JNI_ERR; /* JNI version not supported */
   }

   std::cout << "Initializing module" << std::endl;
   if (!env)
   {
      std::cout << "JNI env ref is null!, aborting" << std::endl;
      return JNI_ERR;
   }

   JAVA_STRING_CLASS = (jclass)createGlobalRef(env,findClass(env,"java/lang/String"));
   std::cout << "Loaded java string" << std::endl;

   JAVA_BOOLEAN_CLASS = (jclass)createGlobalRef(env,findClass(env,"java/lang/Boolean"));
   JAVA_BOOLEAN_CTOR = getMethodID(env,JAVA_BOOLEAN_CLASS, "<init>", "(Z)V");
   JAVA_BOOLEAN_VALUE_METHOD = getMethodID(env,JAVA_BOOLEAN_CLASS,"booleanValue", "()Z");
   std::cout << "Loaded java boolean"<< std::endl;

   JAVA_BYTE_CLASS = (jclass)createGlobalRef(env,findClass(env,"java/lang/Byte"));
   JAVA_BYTE_CTOR = getMethodID(env,JAVA_BYTE_CLASS, "<init>", "(B)V");
   JAVA_BYTE_VALUE_METHOD = getMethodID(env,JAVA_BYTE_CLASS,"byteValue", "()B");
   std::cout << "Loaded java byte"<< std::endl;

   JAVA_SHORT_CLASS = (jclass)createGlobalRef(env,findClass(env,"java/lang/Short"));
   JAVA_SHORT_CTOR = getMethodID(env,JAVA_SHORT_CLASS, "<init>", "(S)V");
   JAVA_SHORT_VALUE_METHOD = getMethodID(env,JAVA_SHORT_CLASS,"shortValue", "()S");
   std::cout << "Loaded java short"<< std::endl;

   JAVA_INT_CLASS = (jclass)createGlobalRef(env,findClass(env,"java/lang/Integer"));
   JAVA_INT_CTOR = getMethodID(env,JAVA_INT_CLASS, "<init>", "(I)V");
   JAVA_INT_VALUE_METHOD = getMethodID(env,JAVA_INT_CLASS,"intValue", "()I");
   std::cout << "Loaded java int"<< std::endl;

   JAVA_LONG_CLASS = (jclass)createGlobalRef(env,findClass(env,"java/lang/Long"));
   JAVA_LONG_CTOR = getMethodID(env,JAVA_LONG_CLASS, "<init>", "(J)V");
   JAVA_LONG_VALUE_METHOD = getMethodID(env,JAVA_LONG_CLASS,"longValue", "()J");
   std::cout << "Loaded java long"<< std::endl;

   JAVA_DOUBLE_CLASS = (jclass)createGlobalRef(env,findClass(env,"java/lang/Double"));
   JAVA_DOUBLE_CTOR = getMethodID(env,JAVA_DOUBLE_CLASS, "<init>", "(D)V");
   JAVA_DOUBLE_VALUE_METHOD = getMethodID(env,JAVA_DOUBLE_CLASS,"doubleValue", "()D");
   std::cout << "Loaded java double"<< std::endl;

   JAVA_FLOAT_CLASS = (jclass)createGlobalRef(env,findClass(env,"java/lang/Float"));
   JAVA_FLOAT_CTOR = getMethodID(env,JAVA_FLOAT_CLASS, "<init>", "(F)V");
   JAVA_FLOAT_VALUE_METHOD = getMethodID(env,JAVA_FLOAT_CLASS,"floatValue", "()F");
   std::cout << "Loaded java float"<< std::endl;

   JAVA_ILLEGAL_ARGUMENT_EXP = (jclass)createGlobalRef(env,findClass(env,"java/lang/IllegalArgumentException"));
   JAVA_JNI_LAYER_EXP = (jclass)createGlobalRef(env,findClass(env,"org/apache/qpid/messaging/cpp/JNILayerException"));
   std::cout << "Loaded java exceptions"<< std::endl;
   return JNI_VERSION_1_4;
}

static jobject newJavaBoolean(JNIEnv* env, jboolean arg)
{
    return env->NewObject(JAVA_BOOLEAN_CLASS, JAVA_BOOLEAN_CTOR,arg);
}

static jobject newJavaByte(JNIEnv* env, jbyte arg)
{
    return env->NewObject(JAVA_BYTE_CLASS, JAVA_BYTE_CTOR,arg);
}

static jobject newJavaShort(JNIEnv* env, jshort arg)
{
    return env->NewObject(JAVA_SHORT_CLASS, JAVA_SHORT_CTOR,arg);
}

static jobject newJavaInt(JNIEnv* env, jint arg)
{
    return env->NewObject(JAVA_INT_CLASS, JAVA_INT_CTOR,arg);
}

static jobject newJavaLong(JNIEnv* env, jlong arg)
{
    return env->NewObject(JAVA_LONG_CLASS, JAVA_LONG_CTOR,arg);
}

static jobject newJavaFloat(JNIEnv* env, jfloat arg)
{
    return env->NewObject(JAVA_FLOAT_CLASS, JAVA_FLOAT_CTOR,arg);
}

static jobject newJavaDouble(JNIEnv* env, jdouble arg)
{
    return env->NewObject(JAVA_DOUBLE_CLASS, JAVA_DOUBLE_CTOR,arg);
}

static jstring newJavaString(JNIEnv* env, const std::string val)
{
    return env->NewStringUTF(val.data());
}

class NativeStringCreator
{
    public:
       NativeStringCreator(JNIEnv* env, jstring javaString)
          : env_(env), javaString_(javaString), data_(0),str_()
       {
           data_ = (const char *)env_->GetStringUTFChars(javaString_, 0);
           size_t count = (size_t)env_->GetStringUTFLength(javaString_);
           str_.assign(data_,count);
       }

       std::string getStr() { return str_; }

       ~NativeStringCreator()
       {
           env_->ReleaseStringUTFChars(javaString_,data_);
       }

    private:
       JNIEnv* env_;
       jstring javaString_;
       const char* data_;
       std::string str_;
};

//Only primitive types, Strings and UUIDs are supported. No maps or lists.
static qpid::types::Variant convertJavaObjectToVariant(JNIEnv* env, jobject obj)
{
   qpid::types::Variant result;

   if (env->IsInstanceOf(obj,JAVA_STRING_CLASS))
   {
       NativeStringCreator strCreator(env,(jstring)obj);
       result = qpid::types::Variant(strCreator.getStr());
   }
   else if (env->IsInstanceOf(obj,JAVA_LONG_CLASS))
   {
       uint64_t v = env->CallLongMethod(obj,JAVA_LONG_VALUE_METHOD);
       result = qpid::types::Variant(v);
   }
   else if (env->IsInstanceOf(obj,JAVA_INT_CLASS))
   {
       uint32_t v = env->CallIntMethod(obj,JAVA_INT_VALUE_METHOD);
       result = qpid::types::Variant(v);
   }
   else if (env->IsInstanceOf(obj,JAVA_SHORT_CLASS))
   {
       uint64_t v = env->CallShortMethod(obj,JAVA_SHORT_VALUE_METHOD);
       result = qpid::types::Variant(v);
   }
   else if (env->IsInstanceOf(obj,JAVA_BYTE_CLASS))
   {
       uint64_t v = env->CallByteMethod(obj,JAVA_BYTE_VALUE_METHOD);
       result = qpid::types::Variant(v);
   }
   else if (env->IsInstanceOf(obj,JAVA_BOOLEAN_CLASS))
   {
       uint64_t v = env->CallBooleanMethod(obj,JAVA_BOOLEAN_VALUE_METHOD);
       result = qpid::types::Variant(v);
   }
   else if (env->IsInstanceOf(obj,JAVA_DOUBLE_CLASS))
   {
       uint64_t v = env->CallDoubleMethod(obj,JAVA_DOUBLE_VALUE_METHOD);
       result = qpid::types::Variant(v);
   }
   else if (env->IsInstanceOf(obj,JAVA_FLOAT_CLASS))
   {
       uint64_t v = env->CallFloatMethod(obj,JAVA_FLOAT_VALUE_METHOD);
       result = qpid::types::Variant(v);
   }
   else
   {
       env->ThrowNew(JAVA_ILLEGAL_ARGUMENT_EXP,"Only primitive types and strings are allowed");
   }

   return result;
}
%}

/*
 * ====================== Wrappers for C++ Objects ==========================
 * Defines wrappers for C++ objects in support of typemaps,
 * for converting between C++ and Java.
 *
 * Note: If you define Classes that needs a corresponding Java class, you need
 * to add the function definitions under the swig section defined
 * at the end of this file.
 * ==========================================================================
 */

%inline %{
/* ======================== ReadOnlyVariantMapWrapper ==================== */

/**
 * Provides a read-only view of the underlying Variant::Map
 */
class ReadOnlyVariantMapWrapper {

  public:
    ReadOnlyVariantMapWrapper();
    ReadOnlyVariantMapWrapper(JNIEnv* jniEnv, qpid::types::Variant::Map& map);
    ~ReadOnlyVariantMapWrapper();
    jobject get(const std::string key) const;
    jboolean containsKey (const std::string key) const;
    jboolean isEmpty() const;
    // keys_ is populated on demand (i.e if the keys() method is called).
    // since this method modifies keys_ it cannot be marked const.
    jobjectArray keys();
    jint size() const;

  private:
    qpid::types::Variant::Map varMap_;
    JNIEnv* env_;
    jobjectArray keys_;
};

/* ======================== WriteOnlyVariantMapWrapper ==================== */
/**
 * Provides a write-only Variant::Map
 * This is only intended to be used with code that is not in the critical path.
 * Ex: not be used with setting message properties.
 * This is intneded to be used when setting connection properties
 * or address properties.
 */
class WriteOnlyVariantMapWrapper {

};


ReadOnlyVariantMapWrapper::ReadOnlyVariantMapWrapper(): env_(0), varMap_(),keys_(0){}

ReadOnlyVariantMapWrapper::ReadOnlyVariantMapWrapper(JNIEnv* jniEnv, qpid::types::Variant::Map& map): env_(jniEnv), varMap_(map), keys_(0){}

ReadOnlyVariantMapWrapper::~ReadOnlyVariantMapWrapper()
{
    if(keys_ != 0)
    {
        int size = varMap_.size(); // the map is immutable, so this is a safe assumption.
        for (int i=0; i < size; i++)
        {
            // All though this object is deleted, the string may still be referenced on the java side.
            // Therefore the object is only deleting it's reference to these strings.
            // The JVM will cleanup once the Strings goes out of scope on the Java side.
	        env_->DeleteLocalRef(env_->GetObjectArrayElement(keys_,size));
	    }
    }
}

jobject ReadOnlyVariantMapWrapper::get(const std::string key) const
{
    using namespace qpid::types;
    jobject result;
    Variant::Map::const_iterator iter;
    Variant v;

    iter = varMap_.find(key);
    if (iter == varMap_.end()){
        return NULL;
    }

    v = iter->second;

    switch (v.getType()) {
        case VAR_VOID: {
            result = NULL;
            break;
        }

        case VAR_BOOL : {
            result = newJavaBoolean(env_,v.asBool() ? JNI_TRUE : JNI_FALSE);
            break;
        }

        case VAR_INT8 :
        case VAR_UINT8 : {
            result = newJavaByte(env_,(jbyte)v.asUint8());
            break;
        }

        case VAR_INT16 :
        case VAR_UINT16 : {
            result = newJavaShort(env_,(jshort) v.asUint16());
            break;
        }


        case VAR_INT32 :
        case VAR_UINT32 : {
            result = newJavaInt(env_,(jint)v.asUint32());
            break;
        }

        case VAR_INT64 :
        case VAR_UINT64 : {
            result = newJavaLong(env_,(jlong)v.asUint64());
            break;
        }

        case VAR_FLOAT : {
            result = newJavaFloat(env_,(jfloat)v.asFloat());
            break;
        }

        case VAR_DOUBLE : {
            result = newJavaDouble(env_,(jdouble)v.asDouble());
            break;
        }

        case VAR_STRING : {
            result = newJavaString(env_,v.asString());
            break;
        }
    }

    return result;
}

jboolean ReadOnlyVariantMapWrapper::containsKey (const std::string key) const
{
    return (jboolean)(varMap_.find(key) != varMap_.end());
}

jboolean ReadOnlyVariantMapWrapper::isEmpty() const
{
    return (jboolean)varMap_.empty();
}

jint ReadOnlyVariantMapWrapper::size() const
{
    return (jint)varMap_.size();
}

jobjectArray ReadOnlyVariantMapWrapper::keys()
{
    if (keys_ == 0)
    {
        qpid::types::Variant::Map::const_iterator iter;
        int size = varMap_.size();
        keys_ = (jobjectArray)env_->NewObjectArray(size,env_->FindClass("java/lang/String"),env_->NewStringUTF(""));
        for(iter = varMap_.begin(); iter != varMap_.end(); iter++)
        {
            const std::string val((iter->first));
            env_->SetObjectArrayElement(keys_,size,newJavaString(env_,val));
            size++;
        }
    }
    return keys_;
}

%}

/** =================== SWIG Interface definitions ============================
  * This is for swig to generate the jni/java code
  * ===========================================================================
  */
ReadOnlyVariantMapWrapper::ReadOnlyVariantMapWrapper();
ReadOnlyVariantMapWrapper::ReadOnlyVariantMapWrapper(JNIEnv* jniEnv, qpid::types::Variant::Map& map);
ReadOnlyVariantMapWrapper::~ReadOnlyVariantMapWrapper();
jobject ReadOnlyVariantMapWrapper::get(const std::string key) const;
jboolean ReadOnlyVariantMapWrapper::containsKey (const std::string key) const;
jboolean ReadOnlyVariantMapWrapper::isEmpty() const;
jobjectArray ReadOnlyVariantMapWrapper::keys();
jint ReadOnlyVariantMapWrapper::size() const;
