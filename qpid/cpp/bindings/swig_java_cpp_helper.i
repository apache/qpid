/*
 * ===================== C++ Helper Methods ==========================
 * Defines classes and/or helper functions in support of typemaps,
 * for converting between C++ and Java.
 * ===================================================================
 */

%inline %{
class VaraintMapWrapper {

  public:
    VaraintMapWrapper();
    VaraintMapWrapper(JNIEnv*& jniEnv, qpid::types::Variant::Map*& map);
    jobject get(const std::string key) const;
    jboolean containsKey (const jstring key) const;
    jboolean containsValue (const jobject value) const;
    jboolean isEmpty() const;
    jobjectArray keys() const;
    jobjectArray values() const;
    jint size() const;

  private:
    qpid::types::Variant::Map varMap;
    JNIEnv env;
};
%}

VaraintMapWrapper::VaraintMapWrapper();
VaraintMapWrapper::VaraintMapWrapper(JNIEnv*& jniEnv, qpid::types::Variant::Map*& map);
jobject VaraintMapWrapper::get(const std::string key) const;
jboolean VaraintMapWrapper::containsKey (const jstring key) const;
jboolean VaraintMapWrapper::containsValue (const jobject value) const;
jboolean VaraintMapWrapper::isEmpty() const;
jobjectArray VaraintMapWrapper::keys() const;
jobjectArray VaraintMapWrapper::values() const;
jint VaraintMapWrapper::size() const;
