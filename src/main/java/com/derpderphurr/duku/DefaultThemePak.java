package com.derpderphurr.duku;

import javafx.scene.media.AudioClip;
import javafx.scene.paint.Color;

import java.net.URL;

public class DefaultThemePak implements ThemePak {

    private final AudioClip lvlSuccess,lvlFail;

    public static final Color[] REGION_COLORS = {
            Color.web("#e6194b"), // red
            Color.web("#f58231"), // orange
            Color.web("#ffe119"), // yellow
            Color.web("#bfef45"), // lime
            Color.web("#3cb44b"), // green
            Color.web("#469990"), // teal
            Color.web("#42d4f4"), // cyan
            Color.web("#4363d8"), // blue
            Color.web("#000075"), // navy
            Color.web("#911eb4"), // purple
            Color.web("#f032e6"), // magenta
            Color.web("#9a6324"), // brown
    };

    public Color[] getColors() { return REGION_COLORS; }

    public DefaultThemePak() {
        URL lvlSuccessURL = getClass().getResource("/527650__fupicat__winsquare.wav");
        lvlSuccess = new AudioClip(lvlSuccessURL.toExternalForm());

        URL lvlFailURL = getClass().getResource("/475347__fupicat__videogame-death-sound.wav");
        lvlFail = new AudioClip(lvlFailURL.toExternalForm());

    }

    @Override
    public String getName() {
        return "Default";
    }

    @Override
    public AudioClip getLevelSuccessSound() {
        return lvlSuccess;
    }

    @Override
    public AudioClip getLevelFailSound() {
        return lvlFail;
    }

    @Override
    public AudioClip getTargetFoundSound() {
        return null;
    }

    @Override
    public AudioClip getTargetFailSound() {
        return null;
    }

    @Override
    public AudioClip getMarkSound() {
        return null;
    }
}
