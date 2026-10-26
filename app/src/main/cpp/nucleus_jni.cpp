#include <jni.h>
#include <string>
#include <sys/syscall.h>
#include <sched.h>
#include <unistd.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

extern "C" JNIEXPORT jboolean JNICALL
Java_com_nucleusflow_native_NativeCore_setAffinity(JNIEnv *env, jobject thiz, jint pid, jint tid, jlong mask) {
    cpu_set_t set;
    CPU_ZERO(&set);
    for (int i = 0; i < 64; ++i) {
        if (mask & ((jlong)1 << i)) {
            CPU_SET(i, &set);
        }
    }
    int target = tid == 0 ? pid : tid;
    int res = sched_setaffinity(target, sizeof(cpu_set_t), &set);
    return res == 0;
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_nucleusflow_native_NativeCore_getAffinity(JNIEnv *env, jobject thiz, jint pid, jint tid) {
    cpu_set_t set;
    CPU_ZERO(&set);
    int target = tid == 0 ? pid : tid;
    if (sched_getaffinity(target, sizeof(cpu_set_t), &set) != 0) {
        return 0;
    }
    jlong mask = 0;
    for (int i = 0; i < 64; ++i) {
        if (CPU_ISSET(i, &set)) {
            mask |= ((jlong)1 << i);
        }
    }
    return mask;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_nucleusflow_native_NativeCore_readFile(JNIEnv *env, jobject thiz, jstring path) {
    const char *cpath = env->GetStringUTFChars(path, nullptr);
    FILE *f = fopen(cpath, "r");
    if (!f) {
        env->ReleaseStringUTFChars(path, cpath);
        return env->NewStringUTF("");
    }
    char buf[4096];
    std::string out;
    while (fgets(buf, sizeof(buf), f)) {
        out += buf;
    }
    fclose(f);
    env->ReleaseStringUTFChars(path, cpath);
    return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_nucleusflow_native_NativeCore_writeFile(JNIEnv *env, jobject thiz, jstring path, jstring val) {
    const char *cpath = env->GetStringUTFChars(path, nullptr);
    const char *cval = env->GetStringUTFChars(val, nullptr);
    FILE *f = fopen(cpath, "w");
    if (!f) {
        env->ReleaseStringUTFChars(path, cpath);
        env->ReleaseStringUTFChars(val, cval);
        return JNI_FALSE;
    }
    fputs(cval, f);
    fclose(f);
    env->ReleaseStringUTFChars(path, cpath);
    env->ReleaseStringUTFChars(val, cval);
    return JNI_TRUE;
}
