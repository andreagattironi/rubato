package com.michomuchomacho.rubato.interfaces;

import androidx.annotation.Keep;

@Keep
public interface MediaIndexCallback {
    default void onRecovery(int index) {}
}
