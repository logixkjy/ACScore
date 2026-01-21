package com.kandc.acscore.util

object KoreanChosung {
    private val CHOSUNG = charArrayOf(
        'ㄱ','ㄲ','ㄴ','ㄷ','ㄸ','ㄹ','ㅁ','ㅂ','ㅃ',
        'ㅅ','ㅆ','ㅇ','ㅈ','ㅉ','ㅊ','ㅋ','ㅌ','ㅍ','ㅎ'
    )

    /** DB 저장용: 문자열 전체를 초성 스트링으로 (공백은 유지) */
    fun extract(text: String): String {
        val sb = StringBuilder()
        for (ch in text) {
            when {
                ch in '가'..'힣' -> {
                    val index = (ch.code - '가'.code) / 588
                    sb.append(CHOSUNG[index])
                }
                ch.isLetterOrDigit() -> sb.append(ch)
                ch == ' ' -> sb.append(' ')
            }
        }
        return sb.toString()
    }

    /** 검색용: 입력에서 초성만 뽑아 "ㅇㄹㄷ" 형태로 만들기 (혼합 입력도 대응) */
    fun extractOnlyChosung(text: String): String {
        val sb = StringBuilder()
        for (ch in text) {
            when {
                ch in '가'..'힣' -> {
                    val index = (ch.code - '가'.code) / 588
                    sb.append(CHOSUNG[index])
                }
                CHOSUNG.contains(ch) -> sb.append(ch)
                ch.isDigit() -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun isChosung(ch: Char): Boolean = CHOSUNG.contains(ch)

    fun initialOf(ch: Char): Char {
        val index = (ch.code - '가'.code) / 588
        return CHOSUNG[index]
    }

    fun indexOf(ch: Char): Int = CHOSUNG.indexOf(ch)

    fun extractForQueryPreserveSpaces(text: String): String {
        val sb = StringBuilder()
        for (ch in text) {
            when {
                ch in '가'..'힣' -> sb.append(initialOf(ch))
                isChosung(ch) -> sb.append(ch)
                ch.isDigit() -> sb.append(ch)
                ch in 'A'..'Z' || ch in 'a'..'z' -> sb.append(ch.uppercaseChar())
                ch.isWhitespace() -> sb.append(' ')
            }
        }
        return sb.toString().trim().replace(Regex("\\s+"), " ")
    }
}