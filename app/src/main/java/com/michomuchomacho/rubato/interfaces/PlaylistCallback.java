package com.michomuchomacho.rubato.interfaces;

import androidx.annotation.Keep;

@Keep
public interface PlaylistCallback {
    default void onRenamed(String name) {}
}
