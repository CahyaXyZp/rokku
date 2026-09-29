#include "discord_bridge.h"
#include <android/log.h>

#define LOG_TAG "DiscordBridge"
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

/**
 * Returns the current user's online status as a discordpp::StatusType value, or -1 when the
 * client isn't ready or the status can't be read.
 */
int DiscordBridge::GetOnlineStatus() {
    std::lock_guard<std::mutex> lock(mutex_);
    if (!client_ || !ready_) return -1;
    try {
        auto user = client_->GetCurrentUser();
        if (!user) return -1;
        return static_cast<int>(user.Status());
    } catch (const std::exception& e) {
        LOGW("GetOnlineStatus threw exception: %s", e.what());
    } catch (...) {
        LOGW("GetOnlineStatus threw unknown exception");
    }
    return -1;
}

extern "C" {

JNIEXPORT jint JNICALL
Java_eu_kanade_tachiyomi_data_connections_discord_DiscordRpcManager_nativeGetOnlineStatus(
    JNIEnv* env, jobject thiz
) {
    return static_cast<jint>(g_discordBridge.GetOnlineStatus());
}

} // extern "C"
