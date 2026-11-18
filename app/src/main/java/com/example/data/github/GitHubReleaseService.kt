package com.example.data.github

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GitHubAsset(
    val name: String = "",
    val size: Long = 0L,
    @Json(name = "browser_download_url") val browserDownloadUrl: String = ""
)

@JsonClass(generateAdapter = true)
data class GitHubRelease(
    @Json(name = "tag_name") val tagName: String = "",
    val name: String = "",
    val body: String = "",
    @Json(name = "html_url") val htmlUrl: String = "",
    @Json(name = "published_at") val publishedAt: String = "",
    val assets: List<GitHubAsset> = emptyList()
)

@JsonClass(generateAdapter = true)
data class ObtainiumAdditionalSettings(
    val includePrereleases: Boolean = false,
    val fallbackToOlderReleases: Boolean = true,
    val apkFilterRegEx: String = "PulseChat.*\\.apk$",
    val autoApkFilterByArch: Boolean = true,
    val appName: String = "PulseChat"
)

@JsonClass(generateAdapter = true)
data class ObtainiumAppConfig(
    val id: String = "com.aistudio.pulsechat.qmzvkt",
    val url: String,
    val author: String,
    val name: String = "PulseChat",
    val additionalSettings: String
)

interface GitHubApi {
    @Headers("Accept: application/vnd.github+json")
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubRelease
}

object ObtainiumIntegration {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val api: GitHubApi = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
        .create(GitHubApi::class.java)

    fun parseOwnerRepo(input: String): Pair<String, String>? {
        val cleaned = input.trim()
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removeSuffix("/")
            .removeSuffix(".git")
        val parts = cleaned.split("/").filter { it.isNotBlank() }
        if (parts.size < 2) return null
        return parts[0] to parts[1]
    }

    suspend fun fetchLatestRelease(ownerRepoInput: String): Result<GitHubRelease> {
        val parsed = parseOwnerRepo(ownerRepoInput)
            ?: return Result.failure(IllegalArgumentException("Bitte Format 'owner/repo' oder GitHub-URL angeben."))
        return try {
            val release = api.getLatestRelease(parsed.first, parsed.second)
            Result.success(release)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun buildObtainiumJson(ownerRepoInput: String): String {
        val parsed = parseOwnerRepo(ownerRepoInput)
        val repoUrl = if (parsed != null) {
            "https://github.com/${parsed.first}/${parsed.second}"
        } else {
            "https://github.com/OWNER/PulseChat"
        }
        val additionalJson = moshi.adapter(ObtainiumAdditionalSettings::class.java)
            .toJson(ObtainiumAdditionalSettings())
        val config = ObtainiumAppConfig(
            url = repoUrl,
            author = parsed?.first ?: "PulseChat",
            additionalSettings = additionalJson
        )
        return moshi.adapter(ObtainiumAppConfig::class.java)
            .indent("  ")
            .toJson(config)
    }

    fun buildObtainiumDeepLink(ownerRepoInput: String): String {
        val json = buildObtainiumJson(ownerRepoInput)
        val encoded = URLEncoder.encode(json, "UTF-8")
        return "obtainium://add/$encoded"
    }

    fun buildGithubReleasesUrl(ownerRepoInput: String): String {
        val parsed = parseOwnerRepo(ownerRepoInput) ?: return "https://github.com"
        return "https://github.com/${parsed.first}/${parsed.second}/releases"
    }
}
