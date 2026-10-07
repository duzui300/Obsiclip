package com.duzui.sharetoobsi

/** What arrived from the share sheet or the text-selection toolbar. */
data class IncomingShare(
    val action: String,
    val text: String,
    val sourcePackage: String?,
)
