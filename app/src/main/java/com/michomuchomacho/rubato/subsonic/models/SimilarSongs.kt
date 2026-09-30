package com.michomuchomacho.rubato.subsonic.models

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
class SimilarSongs {
    @SerializedName("song")
    var songs: List<Child>? = null
}