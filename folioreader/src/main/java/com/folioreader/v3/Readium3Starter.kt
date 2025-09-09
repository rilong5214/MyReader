package com.folioreader.v3

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Readium 3 starter kept compile-safe without hard dependency on Readium 3.
 * - Uses reflection to invoke Streamer.open(file) if Readium 3 is on classpath.
 * - If not present, calls onError so callers can handle gracefully.
 */
class Readium3Starter(private val appContext: Context) {
    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    fun openEpub(file: File, onReady: (Any) -> Unit, onError: (Throwable) -> Unit) {
        scope.launch {
            try {
                val streamerCls = Class.forName("org.readium.r2.streamer.Streamer")
                val ctor = streamerCls.getConstructor(Context::class.java)
                val streamer = ctor.newInstance(appContext)

                val openMethod = streamerCls.getMethod("open", File::class.java)
                val result = openMethod.invoke(streamer, file)

                // Expecting Kotlin Result type; attempt to call getOrThrow() via reflection
                val getOrThrow = result.javaClass.methods.firstOrNull { it.name == "getOrThrow" && it.parameterCount == 0 }
                val publication = getOrThrow?.invoke(result) ?: result
                onReady(publication)
            } catch (cnf: ClassNotFoundException) {
                onError(IllegalStateException("Readium 3 is not available on classpath", cnf))
            } catch (t: Throwable) {
                onError(t)
            }
        }
    }

    data class ReaderPrefs(val fontSize: Float = 1.0f, val theme: String = "day")
    fun toNavigatorPreferences(p: ReaderPrefs): Any = Any()
}
