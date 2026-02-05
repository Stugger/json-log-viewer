package com.stugger.logviewer.ui.components;

import com.stugger.logviewer.ui.SessionController;
import javafx.scene.control.Tab;

public class SessionTab extends Tab {

    public final SessionController controller;

    public SessionTab(SessionController controller) {
        this.controller = controller;
    }

}
