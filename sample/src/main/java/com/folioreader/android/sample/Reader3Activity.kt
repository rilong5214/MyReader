package com.folioreader.android.sample

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit              // ← これ（KTX）
import kotlinx.coroutines.*
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Publication
import org.readium.r2.streamer.Streamer
import java.io.File


class Reader3Activity : AppCompatActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var streamer: Streamer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader3)
        streamer = Streamer(this)

        scope.launch {
            val file = ensureSampleEpub()              // assets/epub1.epub を files に複製
            val pub: Publication = withContext(Dispatchers.IO) { streamer.open(file) }.getOrThrow()
            val nav = EpubNavigatorFragment.newInstance(pub, initialLocator = null)
            supportFragmentManager.commit { replace(R.id.navHost, nav) }
        }
    }

    private suspend fun ensureSampleEpub(): File = withContext(Dispatchers.IO) {
        val out = File(filesDir, "sample.epub")
        if (!out.exists()) assets.open("epub1.epub").use { it.copyTo(out.outputStream()) }
        out
    }

    override fun onDestroy() { super.onDestroy(); scope.cancel() }
}
