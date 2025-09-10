package com.folioreader.android.sample

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.createFactory   // ★ 拡張関数の import
import org.readium.r2.shared.publication.Publication
import org.readium.r2.streamer.Streamer
import org.readium.r2.streamer.open                  // ★ 拡張関数の import
import java.io.File

class Reader3Activity : AppCompatActivity() {

    private lateinit var streamer: Streamer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader3)

        streamer = Streamer(this)

        lifecycleScope.launch {
            try {
                val epubFile = ensureSampleEpub()

                val publication: Publication = withContext(Dispatchers.IO) {
                    // File を直接渡す拡張関数（要 import org.readium.r2.streamer.open）
                    streamer.open(
                        file = epubFile,
                        allowUserInteraction = false,
                        sender = this@Reader3Activity
                    ).getOrThrow()
                }

                // Navigator を FragmentFactory 経由で生成（要 import createFactory）
                val factory = EpubNavigatorFragment.createFactory(
                    publication = publication,
                    initialLocator = null
                )
                supportFragmentManager.fragmentFactory = factory
                supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.readerContainer, EpubNavigatorFragment::class.java, null)
                    .commit()

            } catch (t: Throwable) {
                Log.e("Reader3", "Failed to open publication", t)
                finish()
            }
        }
    }

    private suspend fun ensureSampleEpub(): File = withContext(Dispatchers.IO) {
        val out = File(filesDir, "sample.epub")
        if (!out.exists()) {
            // ★ Context のプロパティは "assets"（複数形）。"asset" だと未解決になります。
            assets.open("epub1.epub").use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
        }
        out
    }
}
