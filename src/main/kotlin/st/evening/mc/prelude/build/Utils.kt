package st.evening.mc.prelude.build

import org.gradle.kotlin.dsl.support.uppercaseFirstChar

fun String.snakeToCamel(): String = buildString {
    var upper = false
    for (i in this@snakeToCamel.indices) {
        val c = this@snakeToCamel[i]
        if (c == '_') {
            upper = true
        } else if (upper) {
            upper = false
            append(c.uppercase())
        } else {
            append(c)
        }
    }
}

fun String.snakeToPascal(): String = snakeToCamel().uppercaseFirstChar()
