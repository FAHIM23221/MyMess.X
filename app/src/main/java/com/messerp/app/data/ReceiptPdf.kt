package com.messerp.app.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File

/** Builds a simple one-page PDF receipt on the device. Nothing is uploaded anywhere. */
object ReceiptPdf {

    fun methodLabel(m: String) = when (m) {
        "CASH" -> "হাতে নগদ"
        "BKASH" -> "বিকাশ"
        "NAGAD" -> "নগদ (Nagad)"
        "BANK" -> "ব্যাংক"
        else -> "অন্যান্য"
    }

    fun build(r: Receipt): ByteArray {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(420, 595, 1).create())
        val c: Canvas = page.canvas
        val green = Color.parseColor("#1F7A4D")

        val band = Paint().apply { color = green }
        c.drawRect(0f, 0f, 420f, 78f, band)
        val head = Paint().apply { isAntiAlias = true; color = Color.WHITE; textSize = 22f; isFakeBoldText = true }
        c.drawText(r.messName, 28f, 38f, head)
        val sub = Paint().apply { isAntiAlias = true; color = Color.WHITE; textSize = 13f }
        c.drawText("পেমেন্ট রসিদ", 28f, 62f, sub)

        val label = Paint().apply { isAntiAlias = true; color = Color.parseColor("#4B5B52"); textSize = 12f }
        val value = Paint().apply { isAntiAlias = true; color = Color.BLACK; textSize = 14f; isFakeBoldText = true; textAlign = Paint.Align.RIGHT }
        val line = Paint().apply { color = Color.parseColor("#DDE6DF"); strokeWidth = 1f }

        var y = 118f
        fun row(k: String, v: String) {
            c.drawText(k, 28f, y, label)
            c.drawText(v.take(38), 392f, y, value)
            y += 12f
            c.drawLine(28f, y, 392f, y, line)
            y += 22f
        }

        row("রসিদ নম্বর", r.receiptNo)
        row("তারিখ", Money.bn(r.paidDate))
        row("সদস্য", "${r.memberName} (${r.memberCode})")
        row("ফোন", Money.bn(r.memberPhone))
        row("পেমেন্টের মাধ্যম", methodLabel(r.method))
        if (!r.txnRef.isNullOrBlank()) row("ট্রানজ্যাকশন রেফারেন্স", r.txnRef)
        if (!r.note.isNullOrBlank()) row("নোট", r.note)
        row("গ্রহণকারী", r.receivedBy ?: "—")

        y += 8f
        val amountLabel = Paint().apply { isAntiAlias = true; color = Color.parseColor("#4B5B52"); textSize = 13f }
        c.drawText("প্রাপ্ত টাকা", 28f, y, amountLabel)
        val amount = Paint().apply { isAntiAlias = true; color = green; textSize = 30f; isFakeBoldText = true; textAlign = Paint.Align.RIGHT }
        c.drawText(Money.format(r.amountMinor), 392f, y + 6f, amount)

        if (r.status == "REVERSED") {
            val stamp = Paint().apply { isAntiAlias = true; color = Color.parseColor("#B42318"); textSize = 54f; isFakeBoldText = true; textAlign = Paint.Align.CENTER }
            c.save()
            c.rotate(-18f, 210f, 380f)
            c.drawText("বাতিল", 210f, 390f, stamp)
            c.restore()
            val why = Paint().apply { isAntiAlias = true; color = Color.parseColor("#B42318"); textSize = 12f; textAlign = Paint.Align.CENTER }
            c.drawText("কারণ: " + (r.reversalReason ?: "—").take(40), 210f, 430f, why)
        }

        val foot = Paint().apply { isAntiAlias = true; color = Color.parseColor("#4B5B52"); textSize = 11f; textAlign = Paint.Align.CENTER }
        c.drawText("এটি সিস্টেম থেকে তৈরি ডিজিটাল রসিদ।", 210f, 540f, foot)
        c.drawText("তৈরির সময়: " + Money.bn(r.createdAt.take(10)), 210f, 558f, foot)

        doc.finishPage(page)
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    /** One-click save into the Downloads folder. Works on Android 10 and newer. Returns false otherwise. */
    fun saveToDownloads(context: Context, name: String, bytes: ByteArray): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        val stream = resolver.openOutputStream(uri) ?: return false
        stream.use { it.write(bytes) }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return true
    }

    fun cacheUri(context: Context, name: String, bytes: ByteArray): Uri {
        val dir = File(context.cacheDir, "receipts")
        dir.mkdirs()
        val file = File(dir, name)
        file.writeBytes(bytes)
        return FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
    }
}
