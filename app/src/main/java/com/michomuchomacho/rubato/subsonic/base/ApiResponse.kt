package com.michomuchomacho.rubato.subsonic.base

import androidx.annotation.Keep
import com.michomuchomacho.rubato.subsonic.models.ResponseStatus
import com.michomuchomacho.rubato.subsonic.models.SubsonicResponse
import com.google.gson.annotations.SerializedName

@Keep
class ApiResponse {
    @SerializedName("subsonic-response")
    var subsonicResponse: SubsonicResponse = SubsonicResponse().apply {
        status = ResponseStatus.FAILED
    }
}