#include <jni.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstdint>
#include <cstring>
#include <fstream>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include "llama.h"
#include "whisper.h"

static JavaVM * g_vm = nullptr;

static std::mutex g_asr_mutex;
static whisper_context * g_whisper_ctx = nullptr;
static int g_asr_threads = 4;
static std::atomic<bool> g_asr_initialized(false);

static std::mutex g_llm_mutex;
static llama_model * g_llama_model = nullptr;
static llama_context * g_llama_ctx = nullptr;
static int g_llm_threads = 4;
static std::atomic<bool> g_llm_initialized(false);
static std::atomic<bool> g_llm_running(false);
static std::atomic<bool> g_llm_cancel(false);
static jobject g_llm_callback = nullptr;
static jclass g_llm_stats_class = nullptr;
static jmethodID g_llm_stats_ctor = nullptr;
static std::thread g_llm_worker;

static long long nowMs() {
    using namespace std::chrono;
    return duration_cast<milliseconds>(steady_clock::now().time_since_epoch()).count();
}

static bool fileExists(const std::string & path) {
    std::ifstream f(path, std::ios::binary);
    return f.good();
}

static std::string jstringToStd(JNIEnv * env, jstring s) {
    if (s == nullptr) return "";
    const char * p = env->GetStringUTFChars(s, nullptr);
    if (p == nullptr) return "";
    std::string out(p);
    env->ReleaseStringUTFChars(s, p);
    return out;
}

static jstring newJStringUtf8(JNIEnv * env, const std::string & s) {
    jbyteArray bytes = env->NewByteArray(static_cast<jsize>(s.size()));
    if (bytes == nullptr) return nullptr;

    if (!s.empty()) {
        env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(s.size()), reinterpret_cast<const jbyte *>(s.data()));
    }

    jclass stringCls = env->FindClass("java/lang/String");
    if (stringCls == nullptr) {
        env->DeleteLocalRef(bytes);
        return nullptr;
    }

    jclass charsetCls = env->FindClass("java/nio/charset/StandardCharsets");
    if (charsetCls == nullptr) {
        env->DeleteLocalRef(bytes);
        return nullptr;
    }

    jfieldID utf8Field = env->GetStaticFieldID(charsetCls, "UTF_8", "Ljava/nio/charset/Charset;");
    if (utf8Field == nullptr) {
        env->DeleteLocalRef(bytes);
        return nullptr;
    }

    jobject utf8 = env->GetStaticObjectField(charsetCls, utf8Field);
    if (utf8 == nullptr) {
        env->DeleteLocalRef(bytes);
        return nullptr;
    }

    jmethodID ctor = env->GetMethodID(stringCls, "<init>", "([BLjava/nio/charset/Charset;)V");
    if (ctor == nullptr) {
        env->DeleteLocalRef(bytes);
        env->DeleteLocalRef(utf8);
        return nullptr;
    }

    auto out = static_cast<jstring>(env->NewObject(stringCls, ctor, bytes, utf8));
    env->DeleteLocalRef(bytes);
    env->DeleteLocalRef(utf8);
    return out;
}

static jobject buildAsrResult(JNIEnv * env, const std::string & text, jlong elapsedMs, jint errorCode, const std::string & errorMsg) {
    jclass cls = env->FindClass("com/edgeaivoice/jni/AsrResult");
    if (cls == nullptr) return nullptr;

    jmethodID ctor = env->GetMethodID(cls, "<init>", "(Ljava/lang/String;JILjava/lang/String;)V");
    if (ctor == nullptr) return nullptr;

    jstring jText = newJStringUtf8(env, text);
    jstring jErr = errorMsg.empty() ? nullptr : newJStringUtf8(env, errorMsg);
    return env->NewObject(cls, ctor, jText, elapsedMs, errorCode, jErr);
}

static std::vector<float> pcm16LeToFloat(const jbyte * raw, int byteLen) {
    const int samples = byteLen / 2;
    std::vector<float> out;
    out.reserve(samples);

    for (int i = 0; i < samples; ++i) {
        const int lo = static_cast<unsigned char>(raw[2 * i]);
        const int hi = static_cast<signed char>(raw[2 * i + 1]);
        int16_t v = static_cast<int16_t>((hi << 8) | lo);
        out.push_back(static_cast<float>(v) / 32768.0f);
    }

    return out;
}

static void destroyLlmRuntimeLocked(JNIEnv * env) {
    if (g_llama_ctx != nullptr) {
        llama_free(g_llama_ctx);
        g_llama_ctx = nullptr;
    }
    if (g_llama_model != nullptr) {
        llama_model_free(g_llama_model);
        g_llama_model = nullptr;
    }

    if (g_llm_callback != nullptr && env != nullptr) {
        env->DeleteGlobalRef(g_llm_callback);
        g_llm_callback = nullptr;
    }
    if (g_llm_stats_class != nullptr && env != nullptr) {
        env->DeleteGlobalRef(g_llm_stats_class);
        g_llm_stats_class = nullptr;
    }
    g_llm_stats_ctor = nullptr;

    g_llm_initialized.store(false);
    g_llm_running.store(false);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_edgeaivoice_jni_NativeBridge_nativeHello(JNIEnv * env, jobject /* this */) {
    std::string msg = "nativeHello ok | EdgeAiVoice JNI real backend | BuildID=v3";
    return env->NewStringUTF(msg.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_edgeaivoice_jni_NativeBridge_asrInit(JNIEnv * env, jobject /*thiz*/, jstring modelPath, jint threads) {
    if (modelPath == nullptr || threads <= 0) return -1;

    const std::string path = jstringToStd(env, modelPath);
    if (path.empty() || !fileExists(path)) return -2;

    std::lock_guard<std::mutex> lock(g_asr_mutex);

    if (g_whisper_ctx != nullptr) {
        whisper_free(g_whisper_ctx);
        g_whisper_ctx = nullptr;
    }

    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    cparams.flash_attn = false;

    g_whisper_ctx = whisper_init_from_file_with_params(path.c_str(), cparams);
    if (g_whisper_ctx == nullptr) {
        g_asr_initialized.store(false);
        return -2;
    }

    g_asr_threads = threads;
    g_asr_initialized.store(true);
    return 0;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_edgeaivoice_jni_NativeBridge_asrTranscribePcm16(JNIEnv * env, jobject /*thiz*/, jbyteArray audio, jint sampleRate) {
    if (!g_asr_initialized.load()) {
        return buildAsrResult(env, "", 0, -9, "asr not initialized");
    }
    if (audio == nullptr || sampleRate != WHISPER_SAMPLE_RATE) {
        return buildAsrResult(env, "", 0, -1, "invalid args: sampleRate must be 16000");
    }

    const jsize len = env->GetArrayLength(audio);
    if (len <= 0 || (len % 2 != 0)) {
        return buildAsrResult(env, "", 0, -8, "empty or broken pcm16 payload");
    }

    const jbyte * raw = env->GetByteArrayElements(audio, nullptr);
    if (raw == nullptr) {
        return buildAsrResult(env, "", 0, -4, "oom while reading pcm");
    }

    const long long started = nowMs();
    std::vector<float> pcmf32 = pcm16LeToFloat(raw, len);
    env->ReleaseByteArrayElements(audio, const_cast<jbyte *>(raw), JNI_ABORT);

    std::lock_guard<std::mutex> lock(g_asr_mutex);
    if (g_whisper_ctx == nullptr) {
        return buildAsrResult(env, "", 0, -9, "asr context released");
    }

    whisper_full_params wparams = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    wparams.n_threads = std::max(1, g_asr_threads);
    wparams.print_progress = false;
    wparams.print_realtime = false;
    wparams.print_timestamps = false;
    wparams.no_timestamps = true;
    wparams.single_segment = false;
    wparams.translate = false;
    wparams.language = "zh";
    wparams.detect_language = false;

    if (whisper_full(g_whisper_ctx, wparams, pcmf32.data(), static_cast<int>(pcmf32.size())) != 0) {
        return buildAsrResult(env, "", nowMs() - started, -3, "whisper_full failed");
    }

    const int nSegments = whisper_full_n_segments(g_whisper_ctx);
    std::string text;
    for (int i = 0; i < nSegments; ++i) {
        const char * seg = whisper_full_get_segment_text(g_whisper_ctx, i);
        if (seg != nullptr) text += seg;
    }

    if (text.empty()) {
        return buildAsrResult(env, "", nowMs() - started, -3, "empty asr output");
    }

    return buildAsrResult(env, text, nowMs() - started, 0, "");
}

extern "C" JNIEXPORT jint JNICALL
Java_com_edgeaivoice_jni_NativeBridge_asrRelease(JNIEnv * /*env*/, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_asr_mutex);
    if (g_whisper_ctx != nullptr) {
        whisper_free(g_whisper_ctx);
        g_whisper_ctx = nullptr;
    }
    g_asr_initialized.store(false);
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_edgeaivoice_jni_NativeBridge_llmInit(JNIEnv * env, jobject /*thiz*/, jstring modelPath, jint ctxSize, jint threads) {
    if (modelPath == nullptr || ctxSize <= 0 || threads <= 0) return -1;

    const std::string path = jstringToStd(env, modelPath);
    if (path.empty() || !fileExists(path)) return -2;

    std::lock_guard<std::mutex> lock(g_llm_mutex);

    g_llm_cancel.store(true);
    if (g_llm_worker.joinable()) {
        g_llm_worker.join();
    }
    destroyLlmRuntimeLocked(env);

    llama_backend_init();

    llama_model_params mparams = llama_model_default_params();
    mparams.n_gpu_layers = 0;
    mparams.use_mmap = true;
    mparams.use_mlock = false;

    g_llama_model = llama_model_load_from_file(path.c_str(), mparams);
    if (g_llama_model == nullptr) {
        destroyLlmRuntimeLocked(env);
        return -2;
    }

    llama_context_params cparams = llama_context_default_params();
    cparams.n_ctx = static_cast<uint32_t>(ctxSize);
    cparams.n_batch = 256;
    cparams.n_ubatch = 256;
    cparams.n_seq_max = 1;
    cparams.n_threads = threads;
    cparams.n_threads_batch = threads;

    g_llama_ctx = llama_init_from_model(g_llama_model, cparams);
    if (g_llama_ctx == nullptr) {
        destroyLlmRuntimeLocked(env);
        return -2;
    }

    llama_set_n_threads(g_llama_ctx, threads, threads);

    g_llm_threads = threads;
    g_llm_cancel.store(false);
    g_llm_initialized.store(true);
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_edgeaivoice_jni_NativeBridge_llmStart(JNIEnv * env, jobject /*thiz*/, jstring prompt, jobject config, jobject callback) {
    if (!g_llm_initialized.load()) return -9;
    if (prompt == nullptr || callback == nullptr || config == nullptr) return -1;

    if (g_llm_running.load()) {
        return -6;
    }

    const std::string promptText = jstringToStd(env, prompt);
    if (promptText.empty()) return -1;

    jclass cfgCls = env->GetObjectClass(config);
    if (cfgCls == nullptr) return -1;

    jfieldID fidTemp = env->GetFieldID(cfgCls, "temperature", "F");
    jfieldID fidTopP = env->GetFieldID(cfgCls, "topP", "F");
    jfieldID fidMaxNew = env->GetFieldID(cfgCls, "maxNewTokens", "I");
    jfieldID fidRepeatPenalty = env->GetFieldID(cfgCls, "repeatPenalty", "F");
    if (fidTemp == nullptr || fidTopP == nullptr || fidMaxNew == nullptr || fidRepeatPenalty == nullptr) {
        return -1;
    }

    const float temperature = env->GetFloatField(config, fidTemp);
    const float topP = env->GetFloatField(config, fidTopP);
    const int maxNewTokens = env->GetIntField(config, fidMaxNew);
    const float repeatPenalty = env->GetFloatField(config, fidRepeatPenalty);

    {
        std::lock_guard<std::mutex> lock(g_llm_mutex);
        g_llm_cancel.store(true);
        if (g_llm_worker.joinable()) {
            g_llm_worker.join();
        }

        if (g_llm_callback != nullptr) {
            env->DeleteGlobalRef(g_llm_callback);
            g_llm_callback = nullptr;
        }
        if (g_llm_stats_class != nullptr) {
            env->DeleteGlobalRef(g_llm_stats_class);
            g_llm_stats_class = nullptr;
            g_llm_stats_ctor = nullptr;
        }

        jclass statsLocal = env->FindClass("com/edgeaivoice/jni/LlmStats");
        if (statsLocal == nullptr) return -4;
        jmethodID statsCtorLocal = env->GetMethodID(statsLocal, "<init>", "(JJI)V");
        if (statsCtorLocal == nullptr) {
            env->DeleteLocalRef(statsLocal);
            return -4;
        }
        g_llm_stats_class = static_cast<jclass>(env->NewGlobalRef(statsLocal));
        env->DeleteLocalRef(statsLocal);
        if (g_llm_stats_class == nullptr) return -4;
        g_llm_stats_ctor = statsCtorLocal;

        g_llm_callback = env->NewGlobalRef(callback);
        if (g_llm_callback == nullptr) return -4;

        g_llm_cancel.store(false);
        g_llm_running.store(true);
    }

    g_llm_worker = std::thread([promptText, temperature, topP, maxNewTokens, repeatPenalty]() {
        JNIEnv * env = nullptr;
        bool attached = false;

        if (g_vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
            if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
                g_llm_running.store(false);
                return;
            }
            attached = true;
        }

        jobject callbackRef = nullptr;
        llama_context * ctx = nullptr;
        llama_model * model = nullptr;
        {
            std::lock_guard<std::mutex> lock(g_llm_mutex);
            callbackRef = g_llm_callback;
            ctx = g_llama_ctx;
            model = g_llama_model;
        }

        if (callbackRef == nullptr || ctx == nullptr || model == nullptr) {
            g_llm_running.store(false);
            if (attached) g_vm->DetachCurrentThread();
            return;
        }

        jclass callbackCls = env->GetObjectClass(callbackRef);
        if (callbackCls == nullptr) {
            g_llm_running.store(false);
            if (attached) g_vm->DetachCurrentThread();
            return;
        }

        const jmethodID onFirstToken = env->GetMethodID(callbackCls, "onFirstToken", "(J)V");
        const jmethodID onToken = env->GetMethodID(callbackCls, "onToken", "(Ljava/lang/String;)V");
        const jmethodID onComplete = env->GetMethodID(callbackCls, "onComplete", "(Lcom/edgeaivoice/jni/LlmStats;)V");
        const jmethodID onError = env->GetMethodID(callbackCls, "onError", "(ILjava/lang/String;)V");
        if (onFirstToken == nullptr || onToken == nullptr || onComplete == nullptr || onError == nullptr) {
            g_llm_running.store(false);
            if (attached) g_vm->DetachCurrentThread();
            return;
        }

        auto emitError = [&](int code, const std::string & msg) {
            jstring err = newJStringUtf8(env, msg);
            env->CallVoidMethod(callbackRef, onError, static_cast<jint>(code), err);
            if (err != nullptr) env->DeleteLocalRef(err);
        };

        // TTFT/total should include prompt processing, not only decode loop.
        const long long startMs = nowMs();

        const auto * vocab = llama_model_get_vocab(model);
        if (vocab == nullptr) {
            emitError(-3, "vocab is null");
            g_llm_running.store(false);
            if (attached) g_vm->DetachCurrentThread();
            return;
        }

        int nTokNeed = llama_tokenize(vocab, promptText.c_str(), static_cast<int32_t>(promptText.size()), nullptr, 0, true, true);
        if (nTokNeed == 0) {
            emitError(-1, "tokenize returned 0");
            g_llm_running.store(false);
            if (attached) g_vm->DetachCurrentThread();
            return;
        }
        if (nTokNeed < 0) nTokNeed = -nTokNeed;

        std::vector<llama_token> promptTokens(static_cast<size_t>(nTokNeed));
        const int nPrompt = llama_tokenize(
            vocab,
            promptText.c_str(),
            static_cast<int32_t>(promptText.size()),
            promptTokens.data(),
            static_cast<int32_t>(promptTokens.size()),
            true,
            true
        );
        if (nPrompt <= 0) {
            emitError(-3, "tokenize failed");
            g_llm_running.store(false);
            if (attached) g_vm->DetachCurrentThread();
            return;
        }

        llama_memory_clear(llama_get_memory(ctx), true);

        for (int i = 0; i < nPrompt; ++i) {
            if (g_llm_cancel.load()) {
                emitError(-6, "cancelled");
                g_llm_running.store(false);
                if (attached) g_vm->DetachCurrentThread();
                return;
            }
            llama_token t = promptTokens[static_cast<size_t>(i)];
            llama_batch b = llama_batch_get_one(&t, 1);
            const int decRc = llama_decode(ctx, b);
            if (decRc != 0) {
                emitError(-3, "llama_decode failed on prompt");
                g_llm_running.store(false);
                if (attached) g_vm->DetachCurrentThread();
                return;
            }
        }

        llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
        llama_sampler * sampler = llama_sampler_chain_init(sparams);
        if (sampler == nullptr) {
            emitError(-4, "sampler init failed");
            g_llm_running.store(false);
            if (attached) g_vm->DetachCurrentThread();
            return;
        }

        llama_sampler_chain_add(sampler, llama_sampler_init_penalties(64, repeatPenalty, 0.0f, 0.0f));
        llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
        llama_sampler_chain_add(sampler, llama_sampler_init_top_p(std::clamp(topP, 0.1f, 1.0f), 1));
        llama_sampler_chain_add(sampler, llama_sampler_init_temp(std::max(temperature, 0.0f)));
        llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

        long long firstTokenLatency = -1;
        int emitted = 0;

        for (int step = 0; step < std::max(1, maxNewTokens); ++step) {
            if (g_llm_cancel.load()) {
                emitError(-6, "cancelled");
                llama_sampler_free(sampler);
                g_llm_running.store(false);
                if (attached) g_vm->DetachCurrentThread();
                return;
            }

            llama_token token = llama_sampler_sample(sampler, ctx, -1);
            if (token == LLAMA_TOKEN_NULL || llama_vocab_is_eog(vocab, token)) {
                break;
            }

            char pieceBuf[512];
            int nPiece = llama_token_to_piece(vocab, token, pieceBuf, static_cast<int32_t>(sizeof(pieceBuf)), 0, true);
            std::string piece;
            if (nPiece > 0) {
                piece.assign(pieceBuf, pieceBuf + nPiece);
            } else {
                piece = " ";
            }

            if (firstTokenLatency < 0) {
                firstTokenLatency = nowMs() - startMs;
                env->CallVoidMethod(callbackRef, onFirstToken, static_cast<jlong>(firstTokenLatency));
            }

            jstring tk = newJStringUtf8(env, piece);
            env->CallVoidMethod(callbackRef, onToken, tk);
            if (tk != nullptr) env->DeleteLocalRef(tk);
            emitted++;

            llama_sampler_accept(sampler, token);
            llama_token input = token;
            llama_batch b = llama_batch_get_one(&input, 1);
            const int decRc = llama_decode(ctx, b);
            if (decRc != 0) {
                emitError(-3, "llama_decode failed on generation");
                llama_sampler_free(sampler);
                g_llm_running.store(false);
                if (attached) g_vm->DetachCurrentThread();
                return;
            }
        }

        if (firstTokenLatency < 0) {
            firstTokenLatency = nowMs() - startMs;
            env->CallVoidMethod(callbackRef, onFirstToken, static_cast<jlong>(firstTokenLatency));
        }

        jclass statsCls = nullptr;
        jmethodID statsCtor = nullptr;
        {
            std::lock_guard<std::mutex> lock(g_llm_mutex);
            statsCls = g_llm_stats_class;
            statsCtor = g_llm_stats_ctor;
        }
        if (statsCls != nullptr && statsCtor != nullptr) {
            jobject statsObj = env->NewObject(
                statsCls,
                statsCtor,
                static_cast<jlong>(firstTokenLatency),
                static_cast<jlong>(nowMs() - startMs),
                static_cast<jint>(emitted)
            );
            if (statsObj != nullptr) {
                env->CallVoidMethod(callbackRef, onComplete, statsObj);
                env->DeleteLocalRef(statsObj);
            }
        }

        llama_sampler_free(sampler);

        {
            std::lock_guard<std::mutex> lock(g_llm_mutex);
            if (g_llm_callback != nullptr) {
                env->DeleteGlobalRef(g_llm_callback);
                g_llm_callback = nullptr;
            }
        }

        g_llm_running.store(false);
        if (attached) g_vm->DetachCurrentThread();
    });

    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_edgeaivoice_jni_NativeBridge_llmCancel(JNIEnv * /*env*/, jobject /*thiz*/) {
    g_llm_cancel.store(true);
    return 0;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_edgeaivoice_jni_NativeBridge_llmRelease(JNIEnv * env, jobject /*thiz*/) {
    {
        std::lock_guard<std::mutex> lock(g_llm_mutex);
        g_llm_cancel.store(true);
    }

    if (g_llm_worker.joinable()) {
        g_llm_worker.join();
    }

    {
        std::lock_guard<std::mutex> lock(g_llm_mutex);
        destroyLlmRuntimeLocked(env);
    }

    llama_backend_free();
    return 0;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM * vm, void * /*reserved*/) {
    g_vm = vm;
    return JNI_VERSION_1_6;
}
