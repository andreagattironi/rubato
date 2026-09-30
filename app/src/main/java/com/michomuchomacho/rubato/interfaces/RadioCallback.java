package com.michomuchomacho.rubato.interfaces;

import androidx.annotation.Keep;

@Keep

public interface RadioCallback {
    default void onDismiss() {}
}
