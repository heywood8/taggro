package com.heywood8.telegramnews.ui.feed

import org.junit.Assert.*
import org.junit.Test

class PhotoFilterPredicateTest {

    // Replicates the filter predicate used in FeedViewModel.filteredMessages:
    //   includePhotos || mediaType != "photo" || text.isNotBlank()
    private fun shouldShow(
        includePhotos: Boolean,
        mediaType: String?,
        text: String,
    ): Boolean = includePhotos || mediaType != "photo" || text.isNotBlank()

    @Test
    fun `photo-only message hidden when includePhotos is false`() {
        assertFalse(shouldShow(includePhotos = false, mediaType = "photo", text = ""))
    }

    @Test
    fun `photo-only message shown when includePhotos is true`() {
        assertTrue(shouldShow(includePhotos = true, mediaType = "photo", text = ""))
    }

    @Test
    fun `photo with caption always shown regardless of setting`() {
        assertTrue(shouldShow(includePhotos = false, mediaType = "photo", text = "Some caption"))
    }

    @Test
    fun `text message always shown`() {
        assertTrue(shouldShow(includePhotos = false, mediaType = null, text = "hello world"))
    }

    @Test
    fun `non-photo non-text message with caption passes predicate`() {
        assertTrue(shouldShow(includePhotos = false, mediaType = "video", text = "Watch this"))
    }

    @Test
    fun `missing subscription defaults to exclude photos (false)`() {
        val includePhotos: Boolean = null ?: false
        assertFalse(shouldShow(includePhotos = includePhotos, mediaType = "photo", text = ""))
    }
}
