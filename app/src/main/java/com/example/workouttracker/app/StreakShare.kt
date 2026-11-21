package com.example.workouttracker.app

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import com.example.workouttracker.R
import com.example.workouttracker.core.locale.withAppLanguage
import java.io.File

/**
 * Writes [image] as a PNG in the app's cache and offers it to the share sheet. Only the latest
 * picture is kept. False when it could not be written or shared.
 */
internal fun Context.shareStreakImage(image: Bitmap, days: Int): Boolean = runCatching {
    val folder = File(cacheDir, ShareFolder).apply { mkdirs() }
    folder.listFiles()?.forEach { it.delete() }
    val file = File(folder, "momentum-streak-$days.png")
    file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(this, "$packageName.exports", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(file.name, uri)
    startActivity(Intent.createChooser(send, withAppLanguage().getString(R.string.mo_share_chooser)))
}.isSuccess

private const val ShareFolder = "shares"
