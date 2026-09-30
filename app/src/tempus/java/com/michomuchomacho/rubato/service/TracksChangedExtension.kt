package com.michomuchomacho.rubato.service

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.michomuchomacho.rubato.repository.AutomotiveRepository
import com.michomuchomacho.rubato.util.ConstantsAA
import com.michomuchomacho.rubato.util.Preferences
import kotlin.text.removePrefix

private const val TAG = "TracksChangedExtension"
@UnstableApi
class TracksChangedExtension(
       private val automotiveRepository: AutomotiveRepository
) : MediaServiceExtension {

    @OptIn(UnstableApi::class)
    override fun handle(
        player: Player,
        item: MediaItem,
        queueTarget: MediaManager.QueueTarget
    ): Boolean {

        if (player.mediaItemCount > 1) {
            return false
        }

        val extras = item.requestMetadata.extras ?: item.mediaMetadata.extras
        val parentId = extras?.getString("parent_id")

        if (parentId?.startsWith(ConstantsAA.INSTANTMIX_SOURCE) == true) {
            Preferences.setLastInstantMix()

            // disconnect handle
            MediaServiceExtensionRegistry.handler = null

            val withoutPrefix = parentId.removePrefix(ConstantsAA.INSTANTMIX_SOURCE)
            val countStr = withoutPrefix.substringAfter("[").substringBefore("]")
            val artistId = withoutPrefix.substringAfter("]")
            val count = countStr.toIntOrNull() ?: ConstantsAA.NUMBER_OF_TRACKS_IN_SMALL_MIX

            Log.d(TAG, "handle: Instant Mix is running for artist $artistId count=$count")

            automotiveRepository.instantMixBuilder.buildAndEnqueue(
                artistId,
                item.mediaId,
                (count-1),
                queueTarget
            )
            return true
        }

        if (parentId?.startsWith(ConstantsAA.MADE_FOR_YOU_SOURCE) == true) {
            Preferences.setLastInstantMix()

            // disconnect handle
            MediaServiceExtensionRegistry.handler = null

            val withoutPrefix = parentId.removePrefix(ConstantsAA.MADE_FOR_YOU_SOURCE)
            val countStr = withoutPrefix.substringAfter("[").substringBefore("]")
            val mixType = withoutPrefix.substringAfter("]")
            val count = countStr.toIntOrNull() ?: ConstantsAA.NUMBER_OF_TRACKS_IN_SMALL_MIX

            Log.d(TAG, "handle: MadeForYou Mix is running for $mixType count=$count")

            automotiveRepository.madeForYouBuilder.buildAndEnqueue(
                mixType,
                item.mediaId,
                (count-1),
                queueTarget
            )
            return true
        }

        return false
    }
}
