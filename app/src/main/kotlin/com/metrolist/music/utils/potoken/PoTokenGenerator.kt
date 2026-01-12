package com.metrolist.music.utils.potoken

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PoTokenGenerator(private val context: Context) {
    private val TAG = "PoTokenGenerator"

    private val webViewSupported by lazy { runCatching { CookieManager.getInstance() }.isSuccess }
    private var webViewBadImpl = false

    private val webPoTokenGenLock = Object()
    private var webPoTokenSessionId: String? = null
    private var webPoTokenStreamingPot: String? = null
    private var webPoTokenGenerator: PoTokenWebView? = null

    fun getWebClientPoToken(videoId: String, sessionId: String): PoTokenResult? {
        if (!webViewSupported || webViewBadImpl) {
            return null
        }

        return try {
            getWebClientPoTokenSync(videoId, sessionId, forceRecreate = false)
        } catch (e: Exception) {
            when (e) {
                is BadWebViewException -> {
                    Log.e(TAG, "Could not obtain poToken because WebView is broken", e)
                    webViewBadImpl = true
                    null
                }
                else -> {
                    Log.e(TAG, "Error getting poToken", e)
                    null
                }
            }
        }
    }

    private fun getWebClientPoTokenSync(videoId: String, sessionId: String, forceRecreate: Boolean): PoTokenResult {
        Log.d(TAG, "Web poToken requested: $videoId, $sessionId")

        data class GeneratorState(
            val generator: PoTokenWebView,
            val streamingPot: String,
            val hasBeenRecreated: Boolean
        )

        val state: GeneratorState = synchronized(webPoTokenGenLock) {
            val shouldRecreate = forceRecreate || 
                webPoTokenGenerator == null || 
                webPoTokenGenerator!!.isExpired || 
                webPoTokenSessionId != sessionId

            if (shouldRecreate) {
                webPoTokenSessionId = sessionId

                // Close the current webPoTokenGenerator on the main thread
                webPoTokenGenerator?.let { oldGenerator ->
                    val closeLatch = CountDownLatch(1)
                    Handler(Looper.getMainLooper()).post {
                        try {
                            oldGenerator.close()
                        } finally {
                            closeLatch.countDown()
                        }
                    }
                    closeLatch.await(3, TimeUnit.SECONDS)
                }

                // Create a new webPoTokenGenerator
                webPoTokenGenerator = PoTokenWebView.newPoTokenGenerator(context)
                
                // The streaming poToken needs to be generated exactly once before generating
                // any other (player) tokens.
                webPoTokenStreamingPot = webPoTokenGenerator!!.generatePoToken(webPoTokenSessionId!!)
            }

            GeneratorState(
                webPoTokenGenerator!!,
                webPoTokenStreamingPot!!,
                shouldRecreate
            )
        }

        val playerPot = try {
            if (videoId.isEmpty()) "" else state.generator.generatePoToken(videoId)
        } catch (throwable: Throwable) {
            if (state.hasBeenRecreated) {
                throw throwable
            } else {
                Log.e(TAG, "Failed to obtain poToken, retrying", throwable)
                return getWebClientPoTokenSync(videoId = videoId, sessionId = sessionId, forceRecreate = true)
            }
        }

        Log.d(TAG, "[$videoId] playerPot=$playerPot, streamingPot=${state.streamingPot}")

        return PoTokenResult(playerPot, state.streamingPot)
    }
}
