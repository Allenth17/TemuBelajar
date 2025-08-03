package com.hiralen.temubelajar.util

import java.awt.image.BufferedImage

internal fun BufferedImage.flipHorizontal(): BufferedImage {
    val width = this.width
    val height = this.height
    val flipped = BufferedImage(width, height, this.type)

    val g = flipped.createGraphics()
    // Transformasi: mirror horizontal
    g.drawImage(this, width, 0, 0, height, 0, 0, width, height, null)
    g.dispose()

    return flipped
}