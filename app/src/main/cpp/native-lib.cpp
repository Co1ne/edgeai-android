#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_edgeaivoice_jni_NativeBridge_nativeHello(JNIEnv* env, jobject /* this */) {
    std::string msg = "nativeHello ok | EdgeAiVoice JNI smoke test | BuildID=v1";
    return env->NewStringUTF(msg.c_str());
}

static jobject buildAsrResult(JNIEnv* env, const std::string& text, jlong elapsedMs, jint errorCode, const std::string& errorMsg) {
    jclass cls = env->FindClass("com/edgeaivoice/jni/AsrResult");
    if (cls == nullptr) {
        return nullptr;
    }

    jmethodID ctor = env->GetMethodID(
        cls,
        "<init>",
        "(Ljava/lang/String;JILjava/lang/String;)V"
    );
    if (ctor == nullptr) {
        return nullptr;
    }

    jstring jText = env->NewStringUTF(text.c_str());
    jstring jErr = errorMsg.empty() ? nullptr : env->NewStringUTF(errorMsg.c_str());
    return env->NewObject(cls, ctor, jText, elapsedMs, errorCode, jErr);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_edgeaivoice_jni_NativeBridge_asrInit(JNIEnv* /*env*/, jobject /*thiz*/, jstring /*modelPath*/, jint /*threads*/) {
    return 0;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_edgeaivoice_jni_NativeBridge_asrTranscribePcm16(JNIEnv* env, jobject /*thiz*/, jbyteArray audio, jint sampleRate) {
    if (audio == nullptr || sampleRate <= 0) {
        return buildAsrResult(env, "", 0, -1, "invalid args");
    }

    jsize len = env->GetArrayLength(audio);
    if (len <= 0) {
        return buildAsrResult(env, "", 0, -8, "empty audio");
    }

    // Milestone 1: native smoke result, verifies full Kotlin->JNI->Kotlin ASR path.
    std::string text = "[ASR-STUB] audioBytes=" + std::to_string(len) + ", sampleRate=" + std::to_string(sampleRate);
    return buildAsrResult(env, text, 25, 0, "");
}

extern "C" JNIEXPORT jint JNICALL
Java_com_edgeaivoice_jni_NativeBridge_asrRelease(JNIEnv* /*env*/, jobject /*thiz*/) {
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_edgeaivoice_jni_NativeBridge_llmInit(JNIEnv* /*env*/, jobject /*thiz*/, jstring /*modelPath*/, jint /*ctxSize*/, jint /*threads*/) {
    return -9;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_edgeaivoice_jni_NativeBridge_llmStart(JNIEnv* /*env*/, jobject /*thiz*/, jstring /*prompt*/, jobject /*config*/, jobject /*callback*/) {
    return -9;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_edgeaivoice_jni_NativeBridge_llmCancel(JNIEnv* /*env*/, jobject /*thiz*/) {
    return -9;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_edgeaivoice_jni_NativeBridge_llmRelease(JNIEnv* /*env*/, jobject /*thiz*/) {
    return -9;
}
