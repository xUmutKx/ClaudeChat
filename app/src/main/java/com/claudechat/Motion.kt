package com.claudechat

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

/** One page transition for the whole app: going deeper slides the new page in from the right (the old one drifts left), going back is the mirror image; 0 just fades. */
fun pageSlide(dir: Int): ContentTransform = when {
    dir > 0 -> (slideInHorizontally(tween(280)) { it / 3 } + fadeIn(tween(220))) togetherWith (slideOutHorizontally(tween(280)) { -it / 5 } + fadeOut(tween(160)))
    dir < 0 -> (slideInHorizontally(tween(280)) { -it / 5 } + fadeIn(tween(220))) togetherWith (slideOutHorizontally(tween(280)) { it / 3 } + fadeOut(tween(160)))
    else -> fadeIn(tween(200)) togetherWith fadeOut(tween(120))
}
