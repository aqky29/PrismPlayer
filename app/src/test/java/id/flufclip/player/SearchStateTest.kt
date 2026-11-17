package id.flufclip.player

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory

class SearchStateTest {
    @Test
    fun suggestionBuilderAppendsTrailingSpaceAndMovesCursorToEnd() {
        val field = buildSuggestionBuilderField("lofi hip hop")
        assertEquals("lofi hip hop ", field.text)
        assertEquals(TextRange(field.text.length), field.selection)

        val trimmed = buildSuggestionBuilderField("kotlin compose  ")
        assertEquals("kotlin compose ", trimmed.text)
        assertEquals(TextRange(trimmed.text.length), trimmed.selection)
    }

    @Test
    fun searchStateQueryHelpersKeepTextAndCursorSynchronized() {
        val state = SearchState()
        state.setQueryText("compose tutorial")
        assertEquals("compose tutorial", state.query)
        assertEquals(TextRange("compose tutorial".length), state.queryField.selection)

        state.fillQueryFromSuggestion("compose tutorial")
        assertEquals("compose tutorial ", state.query)
        assertEquals(TextRange("compose tutorial ".length), state.queryField.selection)

        state.setQueryText("", cursorAtEnd = false)
        assertEquals("", state.query)
        assertEquals(TextRange.Zero, state.queryField.selection)
    }

    @Test
    fun inlineSearchFilterAllowsOnlyVideoContainingFilters() {
        assertTrue(isInlineSearchFilter(YoutubeSearchQueryHandlerFactory.ALL))
        assertTrue(isInlineSearchFilter(YoutubeSearchQueryHandlerFactory.VIDEOS))
        assertTrue(isInlineSearchFilter(YoutubeSearchQueryHandlerFactory.MUSIC_VIDEOS))

        assertFalse(isInlineSearchFilter(YoutubeSearchQueryHandlerFactory.CHANNELS))
        assertFalse(isInlineSearchFilter(YoutubeSearchQueryHandlerFactory.PLAYLISTS))
        assertFalse(isInlineSearchFilter(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS))
        assertFalse(isInlineSearchFilter(YoutubeSearchQueryHandlerFactory.MUSIC_ALBUMS))
    }

    @Test
    fun searchFilterChipsUseSupportedNewPipeFilterIds() {
        val supported = YoutubeSearchQueryHandlerFactory.getInstance().availableContentFilter.toSet()
        assertTrue(SEARCH_FILTER_CHIPS.isNotEmpty())
        SEARCH_FILTER_CHIPS.forEach { (id, label) ->
            assertTrue("Filter $id should be supported by NewPipe", id in supported)
            assertTrue("Filter label for $id should be non-blank", label.isNotBlank())
        }
    }

    @Test
    fun centeredSearchLoaderOnlyShownDuringInitialSearchLoad() {
        assertTrue(shouldShowCenteredSearchLoader(isSearchResults = true, loading = true, itemCount = 0))
        assertFalse(shouldShowCenteredSearchLoader(isSearchResults = true, loading = true, itemCount = 5))
        assertFalse(shouldShowCenteredSearchLoader(isSearchResults = true, loading = false, itemCount = 0))
        assertFalse(shouldShowCenteredSearchLoader(isSearchResults = false, loading = true, itemCount = 0))
    }
}
