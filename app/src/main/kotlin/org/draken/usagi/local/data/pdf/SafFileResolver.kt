package org.draken.usagi.local.data.pdf

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import org.draken.usagi.core.util.ext.printStackTraceDebug
import tsuki.util.runCatchingCancellable
import java.io.File

private const val AUTHORITY_EXTERNAL_STORAGE = "com.android.externalstorage.documents"

/**
 * Best-effort conversion of a SAF document [Uri] (as returned for entries under a tree picked
 * via `ACTION_OPEN_DOCUMENT_TREE`) back into a real, directly-readable [File].
 *
 * This only works for Android's built-in "external storage" document provider — i.e. internal
 * shared storage and most SD cards — and only when the document id has the expected
 * `volume:relative/path` shape. It deliberately returns null (never throws) for anything else:
 * cloud-backed providers, some OEM file managers, and other providers that don't expose a real
 * path at all. Callers must treat null as "skip this file", not as an error to surface.
 */
fun Uri.toRealFileOrNull(context: Context): File? = runCatchingCancellable {
	if (authority != AUTHORITY_EXTERNAL_STORAGE || !DocumentsContract.isDocumentUri(context, this)) {
		return@runCatchingCancellable null
	}
	val docId = DocumentsContract.getDocumentId(this)
	val split = docId.split(':', limit = 2)
	if (split.size != 2) {
		return@runCatchingCancellable null
	}
	val (volume, relativePath) = split
	val root = if (volume.equals("primary", ignoreCase = true)) {
		Environment.getExternalStorageDirectory()
	} else {
		// Secondary volumes (SD cards, USB storage). Mount point naming isn't
		// guaranteed across OEMs/Android versions, hence "best-effort".
		File("/storage/$volume")
	}
	File(root, relativePath).takeIf { it.exists() && it.canRead() }
}.onFailure { e ->
	e.printStackTraceDebug()
}.getOrNull()
