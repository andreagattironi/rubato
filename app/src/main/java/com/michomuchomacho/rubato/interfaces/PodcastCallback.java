package com.michomuchomacho.rubato.interfaces;

import androidx.annotation.Keep;

@Keep

public interface PodcastCallback {
    default void onDismiss() {}
}
