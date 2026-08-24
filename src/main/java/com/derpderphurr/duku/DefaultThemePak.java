package com.derpderphurr.duku;

import javafx.scene.media.AudioClip;
import javafx.scene.paint.Color;

import java.net.URL;

public class DefaultThemePak implements ThemePak {

    private final AudioClip lvlSuccess,lvlFail,tgtFound,tgtFail;

    public DefaultThemePak() {
        URL lvlSuccessURL = getClass().getResource("/527650__fupicat__winsquare.wav");
        lvlSuccess = new AudioClip(lvlSuccessURL.toExternalForm());

        URL lvlFailURL = getClass().getResource("/475347__fupicat__videogame-death-sound.wav");
        lvlFail = new AudioClip(lvlFailURL.toExternalForm());

        URL tgtFoundURL = getClass().getResource("/471937__fupicat__videogame-menu-select.wav");
        tgtFound = new AudioClip(tgtFoundURL.toExternalForm());

        URL tgtFailURL = getClass().getResource("/538156__fupicat__yoink2.wav");
        tgtFail = new AudioClip(tgtFailURL.toExternalForm());
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
        return tgtFound;
    }

    @Override
    public AudioClip getTargetFailSound() {
        return tgtFail;
    }

    @Override
    public AudioClip getMarkSound() {
        return null;
    }
}
