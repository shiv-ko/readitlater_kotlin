package com.koukishiba.todobookmark

import android.content.Intent

/** Android の共有 Intent から、URL 抽出の入力となる文字列を集める。 */
object ShareIntentReader {
    fun readTexts(intent: Intent): List<String> = buildList {
        intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            ?.toString()
            ?.let(::add)

        intent.getCharSequenceArrayListExtra(Intent.EXTRA_TEXT)
            ?.map(CharSequence::toString)
            ?.let(::addAll)

        val clipData = intent.clipData
        if (clipData != null) {
            repeat(clipData.itemCount) { index ->
                clipData.getItemAt(index).text
                    ?.toString()
                    ?.let(::add)
            }
        }
    }.filter(String::isNotBlank)
}

