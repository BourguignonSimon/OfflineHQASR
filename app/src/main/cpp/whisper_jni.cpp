#include <jni.h>
#include <string>
#include <vector>
#include <sstream>
#include <fstream>
#include <algorithm>
#include <iomanip>

namespace {
struct WhisperContext {
    std::string modelPath;
    std::vector<std::string> lastTokens;
};

std::string ToStdString(JNIEnv* env, jstring value) {
    if (value == nullptr) {
        return {};
    }
    const char* chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars ? chars : "");
    if (chars) {
        env->ReleaseStringUTFChars(value, chars);
    }
    return result;
}

std::vector<std::string> ToVector(JNIEnv* env, jobjectArray array) {
    std::vector<std::string> values;
    if (array == nullptr) {
        return values;
    }
    jsize length = env->GetArrayLength(array);
    values.reserve(length);
    for (jsize i = 0; i < length; ++i) {
        auto element = static_cast<jstring>(env->GetObjectArrayElement(array, i));
        values.emplace_back(ToStdString(env, element));
        env->DeleteLocalRef(element);
    }
    return values;
}

std::string EscapeJson(const std::string& input) {
    std::ostringstream escaped;
    for (char c : input) {
        switch (c) {
            case '\\': escaped << "\\\\"; break;
            case '"': escaped << "\\\""; break;
            case '\n': escaped << "\\n"; break;
            case '\r': escaped << "\\r"; break;
            case '\t': escaped << "\\t"; break;
            default:
                if (static_cast<unsigned char>(c) < 0x20) {
                    escaped << "\\u"
                            << std::uppercase << std::hex << std::setw(4) << std::setfill('0')
                            << static_cast<int>(static_cast<unsigned char>(c))
                            << std::nouppercase << std::dec;
                } else {
                    escaped << c;
                }
        }
    }
    return escaped.str();
}

bool FileExists(const std::string& path) {
    std::ifstream file(path);
    return file.good();
}

constexpr long long kOverlapMs = 5000;

std::string BuildJsonResponse(long long offsetMs,
                              long long windowMs,
                              long long totalDuration,
                              const std::vector<std::string>& context,
                              std::vector<std::string>* updatedContext) {
    long long segmentEnd = std::min(offsetMs + windowMs, totalDuration);
    if (segmentEnd < offsetMs) {
        segmentEnd = offsetMs;
    }

    std::ostringstream text;
    text << "Fenêtre " << (offsetMs / 1000) << "s→" << (segmentEnd / 1000) << "s";

    std::vector<std::string> ctx = context;
    ctx.push_back(std::to_string(segmentEnd / 1000));
    if (updatedContext) {
        *updatedContext = ctx;
    }

    bool completed = segmentEnd >= totalDuration;
    long long nextOffset = completed
            ? segmentEnd
            : std::max(0LL, segmentEnd - kOverlapMs);
    if (nextOffset <= offsetMs && !completed) {
        nextOffset = segmentEnd;
    }

    std::ostringstream json;
    json << "{";
    json << "\"text\":\"" << EscapeJson(text.str()) << "\",";
    json << "\"segments\":[{";
    json << "\"start\":" << offsetMs << ",";
    json << "\"end\":" << segmentEnd << ",";
    json << "\"text\":\"" << EscapeJson(text.str()) << "\"";
    json << "}],";
    json << "\"context\":[";
    for (size_t i = 0; i < ctx.size(); ++i) {
        if (i > 0) {
            json << ",";
        }
        json << "\"" << EscapeJson(ctx[i]) << "\"";
    }
    json << "],";
    json << "\"completed\":" << (completed ? "true" : "false") << ",";
    json << "\"next_offset_ms\":" << nextOffset;
    json << "}";

    return json.str();
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_offlinehqasr_recorder_WhisperEngine_nativeInit(
        JNIEnv* env,
        jobject /*thiz*/,
        jstring modelPath,
        jstring /*language*/,
        jboolean /*translate*/,
        jint /*threads*/) {
    std::string path = ToStdString(env, modelPath);
    if (!FileExists(path)) {
        jclass illegalState = env->FindClass("java/lang/IllegalStateException");
        env->ThrowNew(illegalState, "Le modèle Whisper est introuvable.");
        return 0;
    }
    auto* context = new WhisperContext();
    context->modelPath = std::move(path);
    return reinterpret_cast<jlong>(context);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_offlinehqasr_recorder_WhisperEngine_nativeProcess(
        JNIEnv* env,
        jobject /*thiz*/,
        jlong handle,
        jstring /*audioPath*/,
        jlong offsetMs,
        jlong windowMs,
        jlong totalDurationMs,
        jobjectArray contextArray) {
    auto* context = reinterpret_cast<WhisperContext*>(handle);
    if (context == nullptr) {
        jclass illegalState = env->FindClass("java/lang/IllegalStateException");
        env->ThrowNew(illegalState, "Contexte Whisper invalide.");
        return nullptr;
    }

    std::vector<std::string> previousTokens = ToVector(env, contextArray);
    std::vector<std::string> updatedTokens;
    std::string response = BuildJsonResponse(offsetMs, windowMs, totalDurationMs,
                                             previousTokens, &updatedTokens);
    context->lastTokens = std::move(updatedTokens);
    return env->NewStringUTF(response.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_offlinehqasr_recorder_WhisperEngine_nativeRelease(
        JNIEnv* /*env*/,
        jobject /*thiz*/,
        jlong handle) {
    auto* context = reinterpret_cast<WhisperContext*>(handle);
    delete context;
}
