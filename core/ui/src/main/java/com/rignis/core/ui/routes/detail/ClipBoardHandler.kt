package com.rignis.core.ui.routes.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.rignis.core.ui.R

class ClipBoardHandler(private val context: Context) {
    fun copyPassword(password: String) {
        val clipBoard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("", password)
        clipBoard.setPrimaryClip(clip)
        Toast.makeText(context, context.getString(R.string.secret_copied), Toast.LENGTH_SHORT).show()
    }
}