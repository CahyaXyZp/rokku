#pragma once

#include <jni.h>
#include <mutex>
#include <string>

#include "discordpp.h"

/**
 * Wraps a single discordpp::Client and exposes it to Kotlin over JNI. One process-wide instance
 * (g_discordBridge) for now, matching the single-active-account design of DiscordRpcManager -
 * multiple concurrent accounts would mean turning this into a handle-based, per-account instance
 * instead, since discordpp::Client itself isn't a singleton and supports that.
 */
class DiscordBridge {
public:
    DiscordBridge();
    ~DiscordBridge();

    bool Init(int64_t appId);
    void Authorize();
    void SetTokenAndConnect(const char* token);
    void Connect();
    void SetActivity(
        int activityType,
        const char* name, const char* state, const char* details,
        int64_t startSecs, int64_t endSecs,
        const char* largeImage, const char* largeText,
        const char* smallImage, const char* smallText,
        const char* button1Label, const char* button1Url,
        const char* button2Label, const char* button2Url
    );
    void SetOnlineStatus(int statusType);
    void Clear();
    void Shutdown();
    void Destroy();
    void RunCallbacks();
    void SetJavaVM(JavaVM* vm);

    bool IsAuthorized() const { return authorized_; }
    bool IsReady() const { return ready_; }

    static void SetDiscordRpcManagerClass(JNIEnv* env, jclass cls) { discordRpcManagerClass_ = cls; }
    static void SetOnNativeStatusChangedMethod(jmethodID m) { onNativeStatusChangedMethod_ = m; }

private:
    void DoGetToken(std::string code, std::string redirectUri, std::string codeVerifier);
    void FireNativeStatusCallback(int statusCode, bool ready, bool authorized);
    void DestroyUnlocked();

    discordpp::Client* client_;
    bool ready_;
    bool authorized_;
    int64_t appId_;
    JavaVM* javaVm_;
    std::mutex mutex_;

    static jclass discordRpcManagerClass_;
    static jmethodID onNativeStatusChangedMethod_;
};
