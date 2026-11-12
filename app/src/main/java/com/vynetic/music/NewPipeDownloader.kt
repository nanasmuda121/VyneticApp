package com.vynetic.music

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.util.concurrent.TimeUnit

class NewPipeDownloader(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) : Downloader() {

    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBody = if (dataToSend != null) {
            val contentType = headers?.get("Content-Type")?.firstOrNull()?.toMediaTypeOrNull()
            dataToSend.toRequestBody(contentType)
        } else if (httpMethod.equals("POST", ignoreCase = true) || httpMethod.equals("PUT", ignoreCase = true)) {
            byteArrayOf().toRequestBody(null)
        } else {
            null
        }

        val requestBuilder = okhttp3.Request.Builder()
            .method(httpMethod, requestBody)
            .url(url)
            .addHeader("User-Agent", USER_AGENT)

        headers?.forEach { (headerName, headerValueList) ->
            requestBuilder.removeHeader(headerName)
            headerValueList.forEach { headerValue ->
                requestBuilder.addHeader(headerName, headerValue)
            }
        }

        val call = client.newCall(requestBuilder.build())
        val response = call.execute()
        response.use { resp ->
            if (resp.code == 429) {
                throw ReCaptchaException("reCaptcha Challenge requested", url)
            }
            val responseBodyToReturn = resp.body?.string().orEmpty()
            val latestUrl = resp.request.url.toString()
            return Response(
                resp.code,
                resp.message,
                resp.headers.toMultimap(),
                responseBodyToReturn,
                latestUrl
            )
        }
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:153.0) Gecko/20100101 Firefox/153.0"
    }
}
