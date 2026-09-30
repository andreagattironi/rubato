package com.michomuchomacho.rubato.interfaces;

import androidx.annotation.Keep;

@Keep
public interface StarCallback {
    default void onError() {}
    default void onSuccess() {}

    default void onRefused() {}
}
