package com.michomuchomacho.rubato.subsonic.models

import androidx.annotation.Keep

@Keep
class JukeboxPlaylist : JukeboxStatus() {
    var entries: List<Child>? = null
}