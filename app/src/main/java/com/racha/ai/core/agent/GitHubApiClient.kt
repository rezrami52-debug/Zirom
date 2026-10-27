package com.racha.ai.core.agent

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class GitHubApiClient(
    private val token: String,
    private val owner: String = "rezrami52",
    private val repo: String = "Zirom"
) {
    companion object {
        private const val TAG = "GitHubApiClient"
        private const val BASE_URL = "https://api.github.com"
    }

    data class FileFetchResult(
        val content: String,
        val sha: String
    )

    suspend fun getFileContent(filePath: String): Result<FileFetchResult> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("$BASE_URL/repos/$owner/$repo/contents/$filePath")
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.connectTimeout = 8000
            conn.readTimeout = 12000

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val response = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8")).use { it.readText() }
                val json = JSONObject(response)
                val encodedContent = json.getString("content").replace("\n", "").trim()
                val sha = json.getString("sha")
                val decodedBytes = Base64.decode(encodedContent, Base64.DEFAULT)
                val rawText = String(decodedBytes, Charsets.UTF_8)
                Result.success(FileFetchResult(rawText, sha))
            } else {
                Result.failure(Exception("فشل قراءة الملف (كود ${conn.responseCode})"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching file", e)
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun commitFileUpdate(
        filePath: String,
        newContent: String,
        commitMessage: String,
        currentSha: String,
        branch: String = "main"
    ): Result<String> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("$BASE_URL/repos/$owner/$repo/contents/$filePath")
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "PUT"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.connectTimeout = 10000
            conn.readTimeout = 20000
            conn.doOutput = true

            val encodedBase64 = Base64.encodeToString(newContent.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

            val payload = JSONObject().apply {
                put("message", commitMessage)
                put("content", encodedBase64)
                put("sha", currentSha)
                put("branch", branch)
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use { os ->
                os.write(payload.toString())
                os.flush()
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK || conn.responseCode == HttpURLConnection.HTTP_CREATED) {
                Result.success("✅ تم رفع التعديل بنجاح وجاري البناء على GitHub Actions.")
            } else {
                val errorStream = conn.errorStream?.let { BufferedReader(InputStreamReader(it)).readText() }
                Log.e(TAG, "Commit failed: $errorStream")
                Result.failure(Exception("فشل الرفع: كود ${conn.responseCode}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error committing file", e)
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }
}
