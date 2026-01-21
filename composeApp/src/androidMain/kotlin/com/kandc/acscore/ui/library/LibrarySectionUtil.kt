package com.kandc.acscore.ui.library

import com.kandc.acscore.util.KoreanChosung

internal fun sectionKey(title: String): String {
    val first = title.firstOrNull { !it.isWhitespace() && it != '-' && it != '_' && it != '(' && it != ')' }
        ?: return "#"

    return when {
        first.isDigit() -> "0-9"
        first in 'A'..'Z' || first in 'a'..'z' -> first.uppercaseChar().toString()

        // 한글 완성형이면 해당 글자의 초성 1개만
        first in '가'..'힣' -> KoreanChosung.initialOf(first).toString()

        // 이미 초성(ㄱㄴㄷ...)이면 그대로
        KoreanChosung.isChosung(first) -> first.toString()

        else -> "#"
    }
}

internal fun buildSections(scores: List<com.kandc.acscore.data.model.Score>)
        : List<Pair<String, List<com.kandc.acscore.data.model.Score>>> {
    return scores
        .groupBy { sectionKey(it.title) }
        .toSortedMap(compareBySectionKey())
        .map { it.key to it.value }
}

private fun compareBySectionKey(): Comparator<String> = Comparator { a, b ->
    fun rank(k: String): Int = when {
        k == "0-9" -> 0
        k.length == 1 && KoreanChosung.isChosung(k[0]) -> 1
        k.length == 1 && k[0] in 'A'..'Z' -> 2
        else -> 3 // "#"
    }

    val ra = rank(a)
    val rb = rank(b)

    if (ra != rb) return@Comparator ra - rb

    // 같은 그룹 내에서는 문자 순서
    when (ra) {
        1 -> { // 한글 초성 순서 고정
            KoreanChosung.indexOf(a[0]) - KoreanChosung.indexOf(b[0])
        }
        else -> a.compareTo(b)
    }
}