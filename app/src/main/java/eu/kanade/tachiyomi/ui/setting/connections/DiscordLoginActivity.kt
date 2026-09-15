package eu.kanade.tachiyomi.ui.setting.connections

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.lifecycle.lifecycleScope
import eu.kanade.tachiyomi.data.connections.discord.DiscordAccount
import eu.kanade.tachiyomi.data.connections.discord.DiscordAccountManager
import eu.kanade.tachiyomi.data.connections.discord.DiscordUserValidator
import eu.kanade.tachiyomi.ui.base.activity.BaseThemedActivity
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.coroutines.launch
import uy.kohesive.injekt.injectLazy
import yokai.i18n.MR
import yokai.util.lang.getString

/**
 * Logs a Discord account in by loading discord.com/login in a WebView and pulling the session
 * token out of localStorage once login finishes - the same general approach as the Kizzy-style
 * reference (Discord has no public OAuth flow suited to a bot-adjacent client like this one).
 *
 * What's different from the reference implementation (see docs/discord-rpc/AGENTS.md):
 * the captured token is verified against `/users/@me` (see [DiscordUserValidator]) *before*
 * anything is saved - a syntactically-token-shaped-but-dead string is rejected here instead of
 * being written to disk and failing silently later. Saved accounts go through
 * [DiscordAccountManager], which encrypts them at rest.
 */
class DiscordLoginActivity : BaseThemedActivity() {

    private val accountManager: DiscordAccountManager by injectLazy()
    private val validator: DiscordUserValidator by injectLazy()

    private lateinit var progressBar: ProgressBar

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
        }
        progressBar = ProgressBar(this)

        val root = FrameLayout(this).apply {
            addView(webView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(
                progressBar,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER),
            )
        }
        setContentView(root)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                if (url != null && url.endsWith("/app")) {
                    view.stopLoading()
                    view.evaluateJavascript(TOKEN_EXTRACTION_JS) { rawResult ->
                        val token = rawResult?.trim('"').orEmpty()
                        if (token.isEmpty() || token == "null") {
                            finishWithError(getString(MR.strings.discord_login_failed))
                        } else {
                            verifyAndSave(token)
                        }
                    }
                }
            }
        }
        webView.loadUrl(DISCORD_LOGIN_URL)
    }

    private fun verifyAndSave(token: String) {
        progressBar.visibility = ProgressBar.VISIBLE
        lifecycleScope.launch {
            when (val result = validator.validate(token)) {
                is DiscordUserValidator.Result.Success -> {
                    accountManager.addAccount(
                        DiscordAccount(
                            id = result.id,
                            username = result.username,
                            avatarUrl = result.avatarUrl,
                            token = token,
                            isActive = true,
                        ),
                    )
                    toast(getString(MR.strings.discord_login_success, result.username))
                    setResult(RESULT_OK)
                    finish()
                }
                is DiscordUserValidator.Result.InvalidToken ->
                    finishWithError(getString(MR.strings.discord_login_invalid_token))
                is DiscordUserValidator.Result.NetworkError ->
                    finishWithError(getString(MR.strings.discord_login_network_error))
            }
        }
    }

    private fun finishWithError(message: String) {
        toast(message)
        setResult(RESULT_CANCELED)
        finish()
    }

    private companion object {
        private const val DISCORD_LOGIN_URL = "https://discord.com/login"

        // Reads the token out of the page's localStorage via a throwaway iframe, same trick the
        // Kizzy-style reference uses - Discord's web client keeps the session token there.
        private const val TOKEN_EXTRACTION_JS = """
            (() => {
                const i = document.createElement('iframe');
                document.body.appendChild(i);
                const token = JSON.parse(i.contentWindow.localStorage.token);
                i.remove();
                return token;
            })();
        """
    }
}
