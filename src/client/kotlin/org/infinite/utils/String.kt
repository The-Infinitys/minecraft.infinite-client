package org.infinite.utils

import java.util.Locale.getDefault

fun toSnakeCase(camelCaseString: String): String {
    // 1. 小文字以外の文字（つまり大文字）の直前に '_' を挿入
    //    ただし、文字列の先頭にある大文字の前には挿入しない
    val withUnderscores =
        camelCaseString.replace(
            "([A-Z])".toRegex(), // 大文字にマッチ
            "_$1", // マッチした大文字の前に '_' を挿入
        )

    // 2. 結果を全て小文字に変換し、先頭の不要な '_' を削除
    //    (toRegex()の挙動により、先頭に大文字がある場合は最初に余分な '_' が付くため)
    return withUnderscores
        .lowercase(getDefault())
        .removePrefix("_")
}
