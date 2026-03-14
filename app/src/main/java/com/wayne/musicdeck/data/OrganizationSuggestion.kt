package com.wayne.musicdeck.data

data class OrganizationSuggestion(
    val songId: Long,
    val currentTitle: String,
    val currentArtist: String,
    val currentAlbum: String,
    val suggestedTitle: String,
    val suggestedArtist: String,
    val suggestedAlbum: String,
    val reason: String
) {
    val hasChanges: Boolean
        get() = currentTitle != suggestedTitle || 
                 currentArtist != suggestedArtist || 
                 currentAlbum != suggestedAlbum
}
