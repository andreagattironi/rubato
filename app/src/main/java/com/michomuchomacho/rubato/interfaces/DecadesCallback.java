package com.michomuchomacho.rubato.interfaces;

import androidx.annotation.Keep;

@Keep
public interface DecadesCallback {
    default void onLoadYear(int year) {}
}
