package com.wayne.musicdeck

sealed class SongListItem {
    data class Header(val letter: String) : SongListItem()
    data class SongItem(val song: Song) : SongListItem()
    data class FolderItem(val name: String, val path: String, val count: Int) : SongListItem()
}
