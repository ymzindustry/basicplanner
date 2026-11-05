

package com.ymz.basicplanner.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.UUID

object FileUtil {

    fun copyUriToInternalStorage(context: Context, uri: Uri): Uri? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null

            
            val fileName = getFileName(context, uri)
            val file = File(context.filesDir, fileName)

            val outputStream = file.outputStream()

            inputStream.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            Uri.fromFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getFileName(context: Context, uri: Uri): String {
        
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        
                        return "${UUID.randomUUID()}-${cursor.getString(nameIndex)}"
                    }
                }
            }
        }
        
        return "${UUID.randomUUID()}.${uri.path?.substringAfterLast('.') ?: "tmp"}"
    }

    fun deleteInternalFile(context: Context, fileUriString: String): Boolean {
        return try {
            val fileUri = Uri.parse(fileUriString)
            
            if (fileUri.scheme == "file") {
                val file = File(fileUri.path!!)
                
                if (file.absolutePath.startsWith(context.filesDir.absolutePath)) {
                    file.delete()
                } else {
                    false
                }
            } else {
                false 
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}