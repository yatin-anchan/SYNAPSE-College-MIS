package com.projectbyyatin.synapsemis.utils

import android.content.Context
import android.net.Uri
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object ImageUploadHelper {

    private const val IMGBB_API_KEY = "536231d947f3707e8c6a1a59290981ff" // Get from https://api.imgbb.com/
    private const val IMGBB_UPLOAD_URL = "https://api.imgbb.com/1/upload"

    fun uploadImage(
        context: Context,
        imageUri: Uri,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        Thread {
            try {
                val client = OkHttpClient()

                // Convert URI to File
                val imageFile = uriToFile(context, imageUri)

                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("key", IMGBB_API_KEY)
                    .addFormDataPart(
                        "image",
                        imageFile.name,
                        RequestBody.create("image/*".toMediaType(), imageFile)
                    )
                    .build()

                val request = Request.Builder()
                    .url(IMGBB_UPLOAD_URL)
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful && responseBody != null) {
                    val jsonResponse = JSONObject(responseBody)
                    val imageUrl = jsonResponse.getJSONObject("data")
                        .getString("url")

                    onSuccess(imageUrl)
                } else {
                    onFailure("Upload failed: ${response.code}")
                }

            } catch (e: Exception) {
                onFailure("Error: ${e.message}")
            }
        }.start()
    }

    private fun uriToFile(context: Context, uri: Uri): File {
        val inputStream = context.contentResolver.openInputStream(uri)
        val tempFile = File.createTempFile("temp_image", ".jpg", context.cacheDir)

        inputStream?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }

        return tempFile
    }
}

