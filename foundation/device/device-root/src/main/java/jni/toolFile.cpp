#include <jni.h>
#include <cstdio>

static inline int exists(const char *fname) {
    if (FILE *file = fopen(fname, "r")) {
        fclose(file);
        return 1;
    }
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_pingidentity_device_root_detector_NativeDetector_exists(JNIEnv *env, jobject, jobjectArray pathArray) {
    const int stringCount = env->GetArrayLength(pathArray);

    for (int i = 0; i < stringCount; ++i) {
        jstring string = static_cast<jstring>(env->GetObjectArrayElement(pathArray, i));
        const char *pathString = env->GetStringUTFChars(string, nullptr);

        if (exists(pathString)) {
            env->ReleaseStringUTFChars(string, pathString);
            return 1;
        }

        env->ReleaseStringUTFChars(string, pathString);
    }

    return 0;
}