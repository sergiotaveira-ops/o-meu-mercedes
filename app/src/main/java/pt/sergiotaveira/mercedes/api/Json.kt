package pt.sergiotaveira.mercedes.api

/** Minimal JSON reader (objects, arrays, strings, numbers, booleans, null). Avoids org.json, which is stubbed in JVM unit tests. */
object Json {
    fun parse(text: String): Any? = Parser(text).run { val v = value(); ws(); v }

    fun obj(text: String): Map<String, Any?> = parse(text) as? Map<String, Any?> ?: emptyMap()

    fun encode(map: Map<String, Any?>): String = map.entries.joinToString(",", "{", "}") { (k, v) -> "${str(k)}:${enc(v)}" }

    private fun enc(v: Any?): String = when (v) {
        null -> "null"
        is String -> str(v)
        is Boolean, is Number -> v.toString()
        is Map<*, *> -> encode(v.entries.associate { it.key.toString() to it.value })
        else -> str(v.toString())
    }

    private fun str(s: String) = buildString {
        append('"')
        for (c in s) when (c) {
            '"' -> append("\\\""); '\\' -> append("\\\\"); '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
            else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
        }
        append('"')
    }

    private class Parser(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun value(): Any? {
            ws()
            require(i < s.length) { "unexpected end of JSON" }
            return when (s[i]) {
                '{' -> { i++; val m = LinkedHashMap<String, Any?>(); ws()
                    if (s[i] == '}') { i++; return m }
                    while (true) { ws(); val k = string(); ws(); expect(':'); m[k] = value(); ws()
                        if (s[i] == ',') i++ else { expect('}'); return m } } ; @Suppress("UNREACHABLE_CODE") m }
                '[' -> { i++; val l = ArrayList<Any?>(); ws()
                    if (s[i] == ']') { i++; return l }
                    while (true) { l += value(); ws(); if (s[i] == ',') i++ else { expect(']'); return l } } ; @Suppress("UNREACHABLE_CODE") l }
                '"' -> string()
                't' -> { lit("true"); true }
                'f' -> { lit("false"); false }
                'n' -> { lit("null"); null }
                else -> number()
            }
        }
        fun expect(c: Char) { require(i < s.length && s[i] == c) { "expected '$c' at $i" }; i++ }
        fun lit(w: String) { require(s.startsWith(w, i)) { "bad literal at $i" }; i += w.length }
        fun number(): Number {
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            val t = s.substring(st, i)
            require(t.isNotEmpty()) { "bad JSON at $st" }
            return t.toLongOrNull() ?: t.toDouble()
        }
        fun string(): String {
            expect('"')
            val b = StringBuilder()
            while (true) {
                val c = s[i++]
                when (c) {
                    '"' -> return b.toString()
                    '\\' -> when (val e = s[i++]) {
                        'n' -> b.append('\n'); 't' -> b.append('\t'); 'r' -> b.append('\r'); 'b' -> b.append('\b'); 'f' -> b.append('\u000c')
                        'u' -> { b.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                        else -> b.append(e)
                    }
                    else -> b.append(c)
                }
            }
        }
    }
}
