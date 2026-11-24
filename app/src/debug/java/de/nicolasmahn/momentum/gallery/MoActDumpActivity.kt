package de.nicolasmahn.momentum.gallery

import android.app.Activity
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import de.nicolasmahn.momentum.feature.mo.acts.MoActBook
import java.io.File

/**
 * Renders every act to a contact sheet on a device's disk, as [moActSheet] draws it by
 * [reviewRows]. `scripts/mo_acts.py` renders the same sheets on the PC, which needs no device; this
 * is for the times the phone's own drawing is in question. Start it with
 * `adb shell am start -n com.example.workouttracker.test/de.nicolasmahn.momentum.gallery.MoActDumpActivity`
 * and pull `files/mo-acts` from the app's external files directory. An `--es only <prefix>` extra
 * limits it to acts whose id starts with that prefix.
 */
class MoActDumpActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val directory = File(getExternalFilesDir(null), "mo-acts").apply {
            deleteRecursively()
            mkdirs()
        }
        val only = intent.getStringExtra("only").orEmpty()
        val acts = MoActBook.filter { it.id.startsWith(only) }
        acts.forEach { act ->
            File(directory, "${act.id}.png").outputStream().use { moActSheet(act, act.reviewRows()).compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        File(directory, "index.txt").writeText(acts.joinToString("\n") { "${it.id}\t${it.occasion}\t${it.bodies.joinToString(",")}" })
        Log.i("MoActDump", "Rendered ${acts.size} acts to $directory")
        finish()
    }
}
