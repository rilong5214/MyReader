package com.folioreader.android.sample

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Reader3Activity - Readium 3 直接 API 使用のためのアクティビティ
 *
 * 完了した作業:
 * リフレクション wrapper (Readium3Starter) を除去
 * 直接 Readium 3 依存関係 (3.1.1) が利用可能
 * TheSilverChair.epub が assets に配置済み
 * activity_reader3.xml レイアウトが準備済み
 * AndroidManifest.xml に正しい LAUNCHER 設定済み
 * build.gradle に必要な依存関係が追加済み
 * "./gradlew :sample:assembleDebug" が成功
 * "Readium 3 is not available on classpath" エラーが解消
 *
 * 現在の課題:
 * Import パス解決が必要 - Readium 3.1.1 の正確な package 構造の特定
 *
 * 既知の API (migration guide より):
 * - FileAsset(file) で EPUB ファイルを開く
 * - Streamer(context) でストリーマーを作成
 * - streamer.open(asset, allowUserInteraction = false) で Publication を取得
 * - EpubNavigatorFactory(publication) でナビゲーター作成
 *
 * 次のステップ:
 * 1. 正確な import パスの特定（org.readium.r2.* の構造）
 * 2. API の実装とテスト
 * 3. Fragment の埋め込み完了
 */
class Reader3Activity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader3)

        lifecycleScope.launch {
            try {
                // assets/TheSilverChair.epub を /cache にコピー
                val epubFile = copyFromAssetsIfNeeded("TheSilverChair.epub")

                // TODO: Readium 3 の直接 API 使用を実装
                // 正確な import パス特定後に実装:
                // val streamer = Streamer(this@Reader3Activity)
                // val result = streamer.open(FileAsset(epubFile), allowUserInteraction = false)
                // result.onSuccess { publication ->
                //     val navigatorFactory = EpubNavigatorFactory(publication)
                //     val fragmentFactory = navigatorFactory.createFragmentFactory(null)
                //     supportFragmentManager.fragmentFactory = fragmentFactory
                //     val fragment = fragmentFactory.instantiate(classLoader, EpubNavigatorFragment::class.java.name)
                //     supportFragmentManager.beginTransaction()
                //         .replace(R.id.readerContainer, fragment, "epub-nav")
                //         .commit()
                //     Toast.makeText(this@Reader3Activity, "EPUB 読み込み完了", Toast.LENGTH_SHORT).show()
                // }.onFailure { error ->
                //     Toast.makeText(this@Reader3Activity, "EPUBを開けません: ${error.message}", Toast.LENGTH_LONG).show()
                // }

                Toast.makeText(
                    this@Reader3Activity,
                    "Readium 3 統合準備完了 - Import パス解決が必要",
                    Toast.LENGTH_LONG
                ).show()

            } catch (t: Throwable) {
                Toast.makeText(this@Reader3Activity,
                    "初期化エラー: ${t.message}", Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private suspend fun copyFromAssetsIfNeeded(name: String): File = withContext(Dispatchers.IO) {
        val out = File(cacheDir, name)
        if (!out.exists() || out.length() == 0L) {
            assets.open(name).use { input ->
                FileOutputStream(out).use { output -> input.copyTo(output) }
            }
        }
        out
    }
}