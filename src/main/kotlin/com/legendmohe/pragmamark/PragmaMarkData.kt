package com.legendmohe.pragmamark

internal class PragmaMarkData {
    var title: String? = null
    var lineNum = 0
    override fun toString(): String {
        return "[$lineNum] $title"
    }
}