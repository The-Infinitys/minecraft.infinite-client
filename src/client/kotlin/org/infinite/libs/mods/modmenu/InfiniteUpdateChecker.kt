package org.infinite.libs.mods.modmenu

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.terraformersmc.modmenu.api.UpdateChannel
import com.terraformersmc.modmenu.api.UpdateChecker
import com.terraformersmc.modmenu.api.UpdateInfo
import net.fabricmc.loader.api.FabricLoader
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.maven.artifact.versioning.ComparableVersion
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * GitHub APIを使用して、Mod Menuにアップデート情報を提供するカスタム UpdateChecker。
 */
class InfiniteUpdateChecker : UpdateChecker {
    private val updateCheckExecutors = Executors.newSingleThreadExecutor()
    private val modId = "infinite"
    private val apiUrl = "https://api.github.com/repos/The-Infinitys/minecraft.infinite-client/releases/latest"

    // HTTPクライアント (OkHttp 5.3.0を使用)
    private val httpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

    /**
     * GitHub Releases APIのレスポンスから必要な情報のみを抽出するためのデータクラス
     */
    private data class GitHubRelease(
        @SerializedName("tag_name")
        val tagName: String,
        @SerializedName("html_url")
        val htmlUrl: String,
    )

    /**
     * 最新バージョンを外部APIから取得し、現在のバージョンと比較する（非同期で実行）
     * @return 最新情報を含む UpdateInfo を CompletableFuture でラップして返します。
     */
    override fun checkForUpdates(): UpdateInfo? =
        CompletableFuture.supplyAsync({
            try {
                // 1. 現在のバージョンを取得
                val currentVersion =
                    FabricLoader
                        .getInstance()
                        .getModContainer(modId)
                        .orElseThrow { IllegalStateException("Mod container for $modId not found.") }
                        .metadata.version.friendlyString

                // 2. 外部APIから最新バージョンとリンクを取得 (ブロッキングI/O)
                val latestRelease = fetchLatestRelease()
                // "v1.21.10-6" -> "1.21.10-6" のように 'v' プレフィックスを削除
                val latestVersionString = latestRelease.tagName.removePrefix("v")
                val downloadLink = latestRelease.htmlUrl

                // 3. バージョンの比較ロジック (Maven Artifactを使用)
                val isOutdated = compareVersions(currentVersion, latestVersionString)

                if (isOutdated) {
                    InfiniteUpdateInfo(
                        isUpdate = true,
                        link = downloadLink,
                        channel = UpdateChannel.RELEASE,
                    )
                } else {
                    null // 最新バージョンである場合
                }
            } catch (e: Exception) {
                System.err.println("Failed to check for updates for mod '$modId': ${e.message}")
                e.printStackTrace()
                null
            }
        }, updateCheckExecutors) as UpdateInfo?

    /**
     * GitHub APIから最新のリリース情報を取得する (OkHttp 5.3.0を使用)。
     * @throws IOException ネットワーク通信エラーが発生した場合
     * @throws RuntimeException APIリクエストの失敗やJSONパースエラーが発生した場合
     */
    private fun fetchLatestRelease(): GitHubRelease {
        val request =
            Request
                .Builder()
                .url(apiUrl)
                .header("Accept", "application/vnd.github.v3+json")
                .build()

        // OkHttpのコールを実行し、リソースの解放を保証
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("GitHub API request failed: ${response.code}")
            }

            // レスポンスボディを取得
            val jsonString = response.body.string()

            // GsonでJSONをパースしてデータクラスに変換
            return Gson().fromJson(jsonString, GitHubRelease::class.java)
        }
    }

    /**
     * Mavenの ComparableVersion を使用して、現在のバージョンが最新バージョンよりも古いかどうかを比較します。
     * @return current < latest の場合 true を返します。
     */
    private fun compareVersions(
        current: String,
        latest: String,
    ): Boolean {
        // Mavenの ComparableVersion に変換し、比較
        val currentMaven = ComparableVersion(current)
        val latestMaven = ComparableVersion(latest)

        // latest が current よりも新しい (大きい) 場合に true を返す
        return latestMaven > currentMaven
    }
}
