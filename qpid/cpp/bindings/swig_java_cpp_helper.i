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

static std::string STRING_ENCODING("utf8");

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
static jclass JAVA_RUNTIME_EXP;
static jclass JAVA_JNI_LAYER_EXP;
static jmethodID JAVA_JNI_LAYER_EXP_CTOR1; // takes a msg.
static jmethodID JAVA_JNI_LAYER_EXP_CTOR2; // takes a msg and a throwable.

// receiver
static jclass JAVA_NO_MSG_AVAILABLE_EXP;
static jclass JAVA_FETCH_EXP;

// sender
static jclass JAVA_TARGET_CAP_EXCEEDED_EXP;
static jclass JAVA_SEND_EXP;

// address
static jclass JAVA_ADDR_NOT_FOUND_EXP;
static jclass JAVA_MALFORMED_ADDR_EXP;
static jclass JAVA_ADDR_RESOLUTION_EXP;
static jclass JAVA_ADDR_ASSERTION_EXP;

// session
static jclass JAVA_TRANSACTION_EXP;
static jclass JAVA_TX_ABORTED_EXP;
static jclass JAVA_UNAUTHORIZED_EXP;

// transport
static jclass JAVA_TRANSPORT_FAILURE_EXP;

// general catch all
static jclass JAVA_MESSAGING_EXP;
static jclass JAVA_CONNECTION_EXP;
static jclass JAVA_SESSION_EXP;
static jclass JAVA_SENDER_EXP;
static jclass JAVA_RECEIVER_EXP;
static jclass JAVA_ADDR_EXP;

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

// TODO link up with log4j
static void printLog(const char* msg)
{
    std::cout << msg << std::endl;
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

   printLog("--- ** Initializing cqpid_java ** ---");
   if (!env)
   {
      printLog("JNI env ref is null!, aborting");
      printLog("--- ** cqpid_java library loading failed! ** ---");
      return JNI_ERR;
   }

   // The standard class loading should not fail :)
   JAVA_STRING_CLASS = static_cast<jclass>(createGlobalRef(env,findClass(env,"java/lang/String")));

   JAVA_BOOLEAN_CLASS = static_cast<jclass>(createGlobalRef(env,findClass(env,"java/lang/Boolean")));
   JAVA_BOOLEAN_CTOR = getMethodID(env,JAVA_BOOLEAN_CLASS, "<init>", "(Z)V");
   JAVA_BOOLEAN_VALUE_METHOD = getMethodID(env,JAVA_BOOLEAN_CLASS,"booleanValue", "()Z");

   JAVA_BYTE_CLASS = static_cast<jclass>(createGlobalRef(env,findClass(env,"java/lang/Byte")));
   JAVA_BYTE_CTOR = getMethodID(env,JAVA_BYTE_CLASS, "<init>", "(B)V");
   JAVA_BYTE_VALUE_METHOD = getMethodID(env,JAVA_BYTE_CLASS,"byteValue", "()B");

   JAVA_SHORT_CLASS = static_cast<jclass>(createGlobalRef(env,findClass(env,"java/lang/Short")));
   JAVA_SHORT_CTOR = getMethodID(env,JAVA_SHORT_CLASS, "<init>", "(S)V");
   JAVA_SHORT_VALUE_METHOD = getMethodID(env,JAVA_SHORT_CLASS,"shortValue", "()S");

   JAVA_INT_CLASS = static_cast<jclass>(createGlobalRef(env,findClass(env,"java/lang/Integer")));
   JAVA_INT_CTOR = getMethodID(env,JAVA_INT_CLASS, "<init>", "(I)V");
   JAVA_INT_VALUE_METHOD = getMethodID(env,JAVA_INT_CLASS,"intValue", "()I");

   JAVA_LONG_CLASS = static_cast<jclass>(createGlobalRef(env,findClass(env,"java/lang/Long")));
   JAVA_LONG_CTOR = getMethodID(env,JAVA_LONG_CLASS, "<init>", "(J)V");
   JAVA_LONG_VALUE_METHOD = getMethodID(env,JAVA_LONG_CLASS,"longValue", "()J");

   JAVA_DOUBLE_CLASS = static_cast<jclass>(createGlobalRef(env,findClass(env,"java/lang/Double")));
   JAVA_DOUBLE_CTOR = getMethodID(env,JAVA_DOUBLE_CLASS, "<init>", "(D)V");
   JAVA_DOUBLE_VALUE_METHOD = getMethodID(env,JAVA_DOUBLE_CLASS,"doubleValue", "()D");

   JAVA_FLOAT_CLASS = static_cast<jclass>(createGlobalRef(env,findClass(env,"java/lang/Float")));
   JAVA_FLOAT_CTOR = getMethodID(env,JAVA_FLOAT_CLASS, "<init>", "(F)V");
   JAVA_FLOAT_VALUE_METHOD = getMethodID(env,JAVA_FLOAT_CLASS,"floatValue", "()F");

   JAVA_ILLEGAL_ARGUMENT_EXP = static_cast<jclass>(createGlobalRef(env,findClass(env,"java/lang/IllegalArgumentException")));
   JAVA_RUNTIME_EXP   = static_cast<jclass>(createGlobalRef(env,findClass(env,"java/lang/RuntimeException")));
   JAVA_JNI_LAYER_EXP = static_cast<jclass>(createGlobalRef(env,findClass(env,"org/apache/qpid/messaging/cpp/JNILayerException")));
   JAVA_JNI_LAYER_EXP_CTOR1 = getMethodID(env,JAVA_JNI_LAYER_EXP, "<init>", "(Ljava/lang/String;)V");
   JAVA_JNI_LAYER_EXP_CTOR2 = getMethodID(env,JAVA_JNI_LAYER_EXP, "<init>", "(Ljava/lang/String;Ljava/lang/Throwable;)V");

   // Messaging exceptions ---------------

   // receiver
   JAVA_NO_MSG_AVAILABLE_EXP = static_cast<jclass>(createGlobalRef(env,findClass(env,"org/apache/qpid/messaging/NoMessageAvailableException")));
   JAVA_FETCH_EXP            = static_cast<jclass>(createGlobalRef(env,findClass(env,"org/apache/qpid/messaging/FetchException")));

   // sender
   JAVA_TARGET_CAP_EXCEEDED_EXP = static_cast<jclass>(createGlobalRef(env,findClass(env,"org/apache/qpid/messaging/TargetCapacityExceededException")));
   JAVA_SEND_EXP                = static_cast<jclass>(createGlobalRef(env,findClass(env,"org/apache/qpid/messaging/SendException")));

   // address
   JAVA_ADDR_NOT_FOUND_EXP   = static_cast<jclass>(createGlobalRef(env,findClass(env,"org/apache/qpid/messaging/AddressNotFoundException")));
   JAVA_MALFORMED_ADDR_EXP   = static_cast<jclass>(createGlobalRef(env,findClass(env,"org/apache/qpid/messaging/MalformedAddressException")));
   JAVA_ADDR_RESOLUTION_EXP  = static_cast<jclass>(createGlobalRef(env,findClass(env,"org/apache/qpid/messaging/AddressResolutionException")));
   JAVA_ADDR_ASSERTION_EXP   = static_cast<jclass>(createGlobalRef(env,findClass(env,"org/apache/qpid/messaging/AddressAssertionFailedException")));

   // session
   JAVA_TRANSACTION_EXP  = static_cast<jclass>(createGlobalRef(env,findClass(env,"org/apache/qpid/messaging/TransactionException")));
   JAVA_TX_ABORTED_EXP   = static_cast<jclass>(createGlobalRef(env,findClass(env,"org/apache/qpid/messaging/TransactionAbortedException")));
   JAVA_UNAUTHORIZED_EXP = static_cast<jclass>(createGlobalRef(env,findClass(env,"org/apache/qpid/messaging/UnauthorizedAccessException")));

   // transport
   JAVA_TRANSPORT_FAILURE_EXP   = static_cast<jclass>(createGlobalRef(env,findClass(env,"org/apache/qpid/messaging/TransportFailureException")));

   // general catch all
   JAVA_MESSAGING_EXP = static_cast<jclass>(createGlobalRef(env,findClass(env,"org/apache/qpid/messaging/MessagingException")));
   JAVA_CONNECTION_EXP = static_cast<jclass>(createGlobalRef(env,findClass(env,"org/apache/qpid/messaging/ConnectionException")));
   JAVA_SESSION_EXP    = static_cast<jclass>(createGlobalRef(env,findClass(env,"org/apache/qpid/messaging/SessionException")));
   JAVA_SENDER_EXP     = static_cast<jclass>(createGlobalRef(env,findClass(env,"org/apache/qpid/messaging/SenderException")));
   JAVA_RECEIVER_EXP   = static_cast<jclass>(createGlobalRef(env,findClass(env,"org/apache/qpid/messaging/ReceiverException")));
   JAVA_ADDR_EXP       = static_cast<jclass>(createGlobalRef(env,findClass(env,"org/apache/qpid/messaging/AddressException")));

   if (env->ExceptionCheck())
   {
      // The JVM will throw an exception on the Java side.
      printLog("Loading of one or more class or constructor definitions failed. See exception for details");
      printLog("--- ** cqpid_java library loading failed! ** ---");
      return JNI_ERR;
   }

   printLog("--- ** cqpid_java library was loaded successfully ** ---");
   return JNI_VERSION_1_4;
}

static JNIEnv* getJNIEnvPointer()
{
    JNIEnv* env;
    cachedJVM->GetEnv((void **)&env, JNI_VERSION_1_4);
    return env;
}

static bool checkAndThrowJNILaylerException(JNIEnv* env, const char* msg)
{
    jthrowable cause = env->ExceptionOccurred();
    if (cause)
    {
        jthrowable ex = static_cast<jthrowable>(env->NewObject(JAVA_JNI_LAYER_EXP, JAVA_JNI_LAYER_EXP_CTOR2, msg, cause));
        env->Throw(ex);
        env->DeleteLocalRef(cause);
        env->DeleteLocalRef(ex);
        return true;
    }
    return false;
}

static void throwJNILayerException(JNIEnv* env, const char* msg)
{
    jthrowable ex = static_cast<jthrowable>(env->NewObject(JAVA_JNI_LAYER_EXP, JAVA_JNI_LAYER_EXP_CTOR1, msg));
    env->Throw(ex);
    env->DeleteLocalRef(ex);
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
       result.setEncoding(STRING_ENCODING);
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
       uint16_t v = env->CallShortMethod(obj,JAVA_SHORT_VALUE_METHOD);
       result = qpid::types::Variant(v);
   }
   else if (env->IsInstanceOf(obj,JAVA_BYTE_CLASS))
   {
       uint8_t v = env->CallByteMethod(obj,JAVA_BYTE_VALUE_METHOD);
       result = qpid::types::Variant(v);
   }
   else if (env->IsInstanceOf(obj,JAVA_BOOLEAN_CLASS))
   {
       bool v = env->CallBooleanMethod(obj,JAVA_BOOLEAN_VALUE_METHOD);
       result = qpid::types::Variant(v);
   }
   else if (env->IsInstanceOf(obj,JAVA_DOUBLE_CLASS))
   {
       double v = env->CallDoubleMethod(obj,JAVA_DOUBLE_VALUE_METHOD);
       result = qpid::types::Variant(v);
   }
   else if (env->IsInstanceOf(obj,JAVA_FLOAT_CLASS))
   {
       float v = env->CallFloatMethod(obj,JAVA_FLOAT_VALUE_METHOD);
       result = qpid::types::Variant(v);
   }
   else
   {
       env->ThrowNew(JAVA_ILLEGAL_ARGUMENT_EXP,"Only primitive types and strings are allowed");
   }

   checkAndThrowJNILaylerException(env,"Exception occured when converting Java object to Variant");

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
    ReadOnlyVariantMapWrapper(qpid::types::Variant::Map& map);
    ~ReadOnlyVariantMapWrapper();
    jobject get(const std::string& key) const;
    jboolean containsKey (const std::string& key) const;
    jboolean isEmpty() const;
    // keys_ is populated on demand (i.e if the keys() method is called).
    // since this method modifies keys_ it cannot be marked const.
    jobjectArray keys();
    jint size() const;

  private:
    qpid::types::Variant::Map varMap_;
    jobjectArray keys_;
};

/* ======================== WriteOnlyVariantMapWrapper ==================== */
/**
 * Provides a write-only Variant::Map
 * This is only intended to be used with code that is not in the critical path.
 * Ex: not be used with setting message properties.
 * This is intended to be used when setting connection properties
 * or address properties.
 */
class WriteOnlyVariantMapWrapper {

  public:
    WriteOnlyVariantMapWrapper();
    ~WriteOnlyVariantMapWrapper(){}
    void put(const std::string& key, jobject obj);
    qpid::types::Variant::Map getVariantMap() const { return varMap_; }

  private:
    qpid::types::Variant::Map varMap_;
};


ReadOnlyVariantMapWrapper::ReadOnlyVariantMapWrapper(qpid::types::Variant::Map& map): varMap_(map), keys_(0){}

ReadOnlyVariantMapWrapper::~ReadOnlyVariantMapWrapper()
{
    if(keys_ != 0)
    {
        JNIEnv* env = getJNIEnvPointer();
        int size = varMap_.size(); // the map is immutable, so this is a safe assumption.
        for (int i=0; i < size; i++)
        {
            // All though this object is deleted, the string may still be referenced on the java side.
            // Therefore the object is only deleting it's reference to these strings.
            // The JVM will cleanup once the Strings goes out of scope on the Java side.
	        env->DeleteLocalRef(env->GetObjectArrayElement(keys_,size));
	    }
    }
}

jobject ReadOnlyVariantMapWrapper::get(const std::string& key) const
{
    JNIEnv* env = getJNIEnvPointer();
    using namespace qpid::types;
    Variant::Map::const_iterator iter;
    Variant v;

    iter = varMap_.find(key);
    if (iter == varMap_.end()){
        return 0;
    }

    v = iter->second;

    switch (v.getType()) {
        case VAR_VOID: {
            return 0;
        }

        case VAR_BOOL : {
            return newJavaBoolean(env,v.asBool() ? JNI_TRUE : JNI_FALSE);
        }

        case VAR_INT8 :
        case VAR_UINT8 : {
            return newJavaByte(env,(jbyte)v.asUint8());
        }

        case VAR_INT16 :
        case VAR_UINT16 : {
            return newJavaShort(env,(jshort) v.asUint16());
        }


        case VAR_INT32 :
        case VAR_UINT32 : {
            return newJavaInt(env,(jint)v.asUint32());
        }

        case VAR_INT64 :
        case VAR_UINT64 : {
            return newJavaLong(env,(jlong)v.asUint64());
        }

        case VAR_FLOAT : {
            return newJavaFloat(env,(jfloat)v.asFloat());
        }

        case VAR_DOUBLE : {
            return newJavaDouble(env,(jdouble)v.asDouble());
        }

        case VAR_STRING : {
            return newJavaString(env,v.asString());
        }

        case VAR_MAP : {
            throwJNILayerException(env,"The key maps to a Varient::Map. Unsupported type for message properties");
            break;
        }

        case VAR_LIST : {
            throwJNILayerException(env,"The key maps to a Varient::List. Unsupported type for message properties");
            break;
        }
    }
}

jboolean ReadOnlyVariantMapWrapper::containsKey (const std::string& key) const
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
        JNIEnv* env = getJNIEnvPointer();
        qpid::types::Variant::Map::const_iterator iter;
        int size = varMap_.size();
        int index = 0;
        keys_ = (jobjectArray)env->NewObjectArray(size,env->FindClass("java/lang/String"),env->NewStringUTF(""));
        for(iter = varMap_.begin(); iter != varMap_.end(); iter++)
        {
            const std::string val((iter->first));
            env->SetObjectArrayElement(keys_,index,newJavaString(env,val));
            index++;
        }
    }
    return keys_;
}

WriteOnlyVariantMapWrapper::WriteOnlyVariantMapWrapper()
{
    varMap_ = qpid::types::Variant::Map();
}

void WriteOnlyVariantMapWrapper::put(const std::string& key, jobject obj)
{
  JNIEnv* env = getJNIEnvPointer();
  if (key.empty())
  {
      env->ThrowNew(JAVA_ILLEGAL_ARGUMENT_EXP,"Key cannot be empty.");
  }

  qpid::types::Variant v = convertJavaObjectToVariant(env,obj);

  if (v)
  {
      varMap_[key] = v;
  }
  else
  {
      // There will be an exception on the java side,
      // thrown by convertJavaObjectToVariant method.
      return;
  }
}

%}

/** =================== SWIG Interface definitions ============================
  * This is for swig to generate the jni/java code
  * ===========================================================================
  */
ReadOnlyVariantMapWrapper::ReadOnlyVariantMapWrapper(qpid::types::Variant::Map& map);
ReadOnlyVariantMapWrapper::~ReadOnlyVariantMapWrapper();
jobject ReadOnlyVariantMapWrapper::get(const std::string& key) const;
jboolean ReadOnlyVariantMapWrapper::containsKey (const std::string& key) const;
jboolean ReadOnlyVariantMapWrapper::isEmpty() const;
jobjectArray ReadOnlyVariantMapWrapper::keys();
jint ReadOnlyVariantMapWrapper::size() const;

WriteOnlyVariantMapWrapper::WriteOnlyVariantMapWrapper();
WriteOnlyVariantMapWrapper::~WriteOnlyVariantMapWrapper();
void WriteOnlyVariantMapWrapper::put(const std::string& key, jobject obj);
qpid::types::Variant::Map WriteOnlyVariantMapWrapper::getVariantMap() const;
