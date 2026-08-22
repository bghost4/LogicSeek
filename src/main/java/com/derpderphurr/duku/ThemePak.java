package com.derpderphurr.duku;

import javafx.scene.media.AudioClip;
import javafx.scene.paint.Color;

/**
 * A Class that Represents a Theme Pak Ideally this will load from some sort of file.
 */
public interface ThemePak {
    String getName();
    AudioClip getLevelSuccessSound();
    AudioClip getLevelFailSound();
    AudioClip getTargetFoundSound();
    AudioClip getTargetFailSound();
    AudioClip getMarkSound();

    Color[] getColors();
}
