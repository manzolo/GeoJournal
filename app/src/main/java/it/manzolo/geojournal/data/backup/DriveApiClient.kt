package it.manzolo.geojournal.data.backup

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client for Google Drive REST API v3.
 * Uses [GoogleAuthUtil] to obtain OAuth2 access tokens (no Drive SDK dependency).
 * Scope: drive.file — can only see/modify files created by this app.
 *
 * On HTTP 401 (stale cached token), [GoogleAuthUtil.clearToken] is called and the
 * operation is retried once with a freshly fetched token.
 */
class DriveApiClient(
    private val context: Context,
    private val accountEmail: String
) {
    companion object {
        private const val SCOPE = "oauth2:https://www.googleapis.com/auth/drive.file"
        private const val UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
        private const val FILES_URL = "https://www.googleapis.com/drive/v3/files"
        private const val MIME_ZIP = "application/zip"
        const val BACKUP_FILENAME = "geojournal_backup.zip"
    }

    /** Thrown when a Drive request returns HTTP 401 (token stale/revoked). */
    private class TokenExpiredException : Exception()

    /** Returns an access token from cache or network. May throw [TokenExpiredException] indirectly via callers. */
    private suspend fun getAccessToken(): String = withContext(Dispatchers.IO) {
        // GoogleAuthUtil.getToken è bloccante e senza timeout nativo: può restare appeso
        // per ore se rete o Play Services non rispondono (es. notte con connettività intermittente).
        withTimeout(30_000L) {
            runInterruptible {
                GoogleAuthUtil.getToken(context, Account(accountEmail, "com.google"), SCOPE)
            }
        }
    }

    /**
     * Runs [block] with a fresh token.
     * If the block throws [TokenExpiredException] (HTTP 401), the cached token is
     * invalidated and the block is retried exactly once with a new token.
     */
    private suspend fun <T> withTokenRetry(block: suspend (token: String) -> T): T {
        val token = getAccessToken()
        return try {
            block(token)
        } catch (_: TokenExpiredException) {
            withContext(Dispatchers.IO) { GoogleAuthUtil.clearToken(context, token) }
            block(getAccessToken())
        }
    }

    /** Finds a Drive file by name. Returns the file ID, null if not found, or throws [TokenExpiredException] on 401. */
    private suspend fun findFileId(token: String, name: String): String? = withContext(Dispatchers.IO) {
        val q = java.net.URLEncoder.encode("name='$name' and trashed=false", "UTF-8")
        val conn = (URL("$FILES_URL?q=$q&fields=files(id)&spaces=drive").openConnection() as HttpURLConnection).apply {
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        try {
            when (conn.responseCode) {
                200 -> {
                    val files = JSONObject(conn.inputStream.bufferedReader().readText()).optJSONArray("files")
                    if (files != null && files.length() > 0) files.getJSONObject(0).getString("id") else null
                }
                401 -> throw TokenExpiredException()
                else -> null
            }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Uploads [file] via multipart upload.
     * @param fileId if not null, PATCHes the existing file; otherwise creates a new one.
     * @return Drive file ID of the uploaded file.
     * @throws TokenExpiredException on HTTP 401.
     */
    private suspend fun uploadMultipart(token: String, file: File, name: String, fileId: String?): String =
        withContext(Dispatchers.IO) {
            val boundary = "gj_boundary_${System.currentTimeMillis()}"
            val urlStr = if (fileId != null) "$UPLOAD_URL/$fileId?uploadType=multipart"
                         else "$UPLOAD_URL?uploadType=multipart"
            val metaJson = if (fileId != null) """{"name":"$name"}"""
                           else """{"name":"$name","parents":["root"]}"""

            val metaBytes =
                "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metaJson\r\n"
                    .toByteArray(Charsets.UTF_8)
            val fileHeaderBytes = "--$boundary\r\nContent-Type: $MIME_ZIP\r\n\r\n".toByteArray(Charsets.UTF_8)
            val endBytes = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
            val contentLength = metaBytes.size.toLong() + fileHeaderBytes.size.toLong() +
                                 file.length() + endBytes.size.toLong()

            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = if (fileId != null) "PATCH" else "POST"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
                doOutput = true
                setFixedLengthStreamingMode(contentLength)
                connectTimeout = 30_000
                readTimeout = 120_000
            }
            try {
                conn.outputStream.use { out ->
                    out.write(metaBytes)
                    out.write(fileHeaderBytes)
                    file.inputStream().use { it.copyTo(out) }
                    out.write(endBytes)
                }
                when (conn.responseCode) {
                    200, 201 -> JSONObject(conn.inputStream.bufferedReader().readText()).getString("id")
                    401 -> throw TokenExpiredException()
                    else -> {
                        val err = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull()
                        error("Drive upload failed HTTP ${conn.responseCode}: $err")
                    }
                }
            } finally {
                conn.disconnect()
            }
        }

    /**
     * Uploads [file] to Drive, replacing an existing backup file if found.
     * Handles stale token (HTTP 401) with a single automatic retry.
     * @return Drive file ID of the resulting file.
     */
    suspend fun uploadOrReplaceBackup(file: File, name: String = BACKUP_FILENAME): String =
        withTokenRetry { token ->
            val existingId = findFileId(token, name)
            uploadMultipart(token, file, name, existingId)
        }
}
