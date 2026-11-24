package de.nicolasmahn.momentum.app

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import de.nicolasmahn.momentum.R
import java.io.File
import java.time.LocalDate

/**
 * Writes [markdown] as `momentum-export-<today>.md` in the app's cache and offers it to the share
 * sheet. A file, not the text itself, because a whole history is too large to pass as an extra.
 * Only the latest export is kept. False when it could not be written or shared.
 */
internal fun Context.shareStatisticsExport(markdown: String, today: LocalDate): Boolean = runCatching {
    val folder = File(cacheDir, ExportFolder).apply { mkdirs() }
    folder.listFiles()?.forEach { it.delete() }
    val file = File(folder, "momentum-export-$today.md").apply { writeText(markdown) }
    val uri = FileProvider.getUriForFile(this, "$packageName.exports", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/markdown")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_TITLE, file.name)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(file.name, uri)
    startActivity(Intent.createChooser(send, getString(R.string.statistics_export_share)))
}.isSuccess

private const val ExportFolder = "exports"
