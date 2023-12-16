package com.apadmi.mockzilla

import BridgeMockzillaConfig
import BridgeMockzillaHttpRequest
import BridgeMockzillaRuntimeParams
import MockzillaFlutterApi
import MockzillaHostApi
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.apadmi.mockzilla.lib.models.MockzillaHttpRequest
import com.apadmi.mockzilla.lib.models.MockzillaHttpResponse
import com.apadmi.mockzilla.lib.startMockzilla
import com.apadmi.mockzilla.lib.stopMockzilla
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking

class MockzillaAndroid(
    private val flutterApi: MockzillaFlutterApi,
    private val context: Context
): MockzillaHostApi {

    val uiThreadHandler = Handler(Looper.getMainLooper())

    override fun startServer(config: BridgeMockzillaConfig): BridgeMockzillaRuntimeParams {
        val nativeConfig = config.toNative(
            { key -> callEndpointMatcher(this, key) },
            { key -> callDefaultHandler(this, key) },
            { key -> callErrorHandler(this, key) }
        )
        val nativeRuntimeParams = startMockzilla(nativeConfig, context)
        return BridgeMockzillaRuntimeParams.fromNative(nativeRuntimeParams)
    }

    override fun stopServer() {
        stopMockzilla()
    }

    private fun callEndpointMatcher(request: MockzillaHttpRequest, key: String): Boolean {
        val completer = CompletableDeferred<Boolean>()
        uiThreadHandler.post {
            flutterApi.endpointMatcher(BridgeMockzillaHttpRequest.fromNative(request), key) {
                completer.complete(it.getOrThrow())
            }
        }
        return runBlocking { completer.await() }
    }

    private fun callDefaultHandler(request: MockzillaHttpRequest, key: String): MockzillaHttpResponse {
        val completer = CompletableDeferred<MockzillaHttpResponse>()
        uiThreadHandler.post {
            flutterApi.defaultHandler(BridgeMockzillaHttpRequest.fromNative(request), key) {
                completer.complete(it.getOrThrow().toNative())
            }
        }
        return runBlocking { completer.await() }
    }

    private fun callErrorHandler(request: MockzillaHttpRequest, key: String): MockzillaHttpResponse {
        val completer = CompletableDeferred<MockzillaHttpResponse>()
        uiThreadHandler.post {
            flutterApi.errorHandler(BridgeMockzillaHttpRequest.fromNative(request), key) {
                completer.complete(it.getOrThrow().toNative())
            }
        }
        return runBlocking { completer.await() }
    }
}