package com.stugger.logviewer;

import javafx.application.Application;

/**
 * Launcher for the program.
 * Separated from {@link MainApp} to prevent runtime issues when packaging application as an executable with java21 bundled.
 */
public class Launcher {

    public static void main(String[] args) {
        Application.launch(MainApp.class, args);
    }

}