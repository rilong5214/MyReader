package com.folioreader.android.sample

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.folioreader.android.sample.R
import com.folioreader.v3.Readium3Starter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class Reader3Activity : AppCompatActivity() {

    private lateinit var starter: Readium3Starter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader3)

        // Readium 3 をリフレクションで起動するスターター（依存が無くてもビルド可）
        starter = Readium3Starter(applicationContext)

        // assets に置いたサンプル EPUB を /cache にコピーして開く
        lifecycleScope.launch {
            try {
                val epubFile = copyEpubFromAssetsIfNeeded("sample.epub")
                starter.openEpub(
                    epubFile,
                    onReady = {
                        Toast.makeText(this@Reader3Activity, "EPUB loaded", Toast.LENGTH_SHORT).show()
                        // TODO: Readium 3 がクラスパスにある場合、ここで Navigator を表示する処理を追加できます。
                    },
                    onError = { err ->
                        Toast.makeText(this@Reader3Activity, "EPUBを開けません: ${err.message}", Toast.LENGTH_LONG).show()
                    }
                )
            } catch (t: Throwable) {
                Toast.makeText(this@Reader3Activity, "初期化エラー: ${t.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * assets/sample.epub を /cache/sample.epub にコピー（既にあれば流用）
     */
    private suspend fun copyEpubFromAssetsIfNeeded(name: String): File = withContext(Dispatchers.IO) {
        val out = File(cacheDir, name)
        if (!out.exists() || out.length() == 0L) {
            assets.open(name).use { input ->
                FileOutputStream(out).use { output ->
                    input.copyTo(output)
                }
            }
        }
        out
    }
}
