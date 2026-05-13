package com.stugger.logviewer.ui;

import com.stugger.logviewer.MainApp;
import javafx.scene.Scene;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Centralized manager responsible for applying and switching application themes.
 *<p>
 * Scenes registered through {@link #apply(Scene)} are tracked using weak references,
 * allowing theme changes to propagate across all open windows without preventing
 * disposed scenes from being garbage collected.
 *<p>
 * Each theme consists of:
 * <ul>
 *     <li>A theme stylesheet (dark/light colors)</li>
 *     <li>A shared component stylesheet (layout + control styling)</li>
 * </ul>
 *
 * @author Jake
 * @since May 13th, 2026
 */
public final class ThemeManager {

    private static final String COMPONENTS_STYLESHEET = "/ui/css/components.css";

    private static final Set<Scene> SCENES = Collections.newSetFromMap(new WeakHashMap<>());

    private static Theme currentTheme = Theme.DARK;

    private ThemeManager() {
        /* empty */
    }

    /**
     * Available application themes.
     */
    public enum Theme {
        DARK("/ui/css/theme-dark.css"),
        LIGHT("/ui/css/theme-light.css");

        private final String path;

        Theme(String path) {
            this.path = path;
        }
    }

    /**
     * Registers a scene with the theme manager and applies the current theme.
     *
     * The scene will automatically receive future theme updates while it remains alive.
     *
     * @param scene the scene to theme
     */
    public static void apply(Scene scene) {
        SCENES.add(scene);
        applyToScene(scene);
    }

    /**
     * Changes the active application theme and reapplies stylesheets to all tracked scenes.
     *
     * @param theme the new theme to apply
     */
    public static void setTheme(Theme theme) {
        currentTheme = Objects.requireNonNull(theme);

        for (Scene scene : SCENES) {
            applyToScene(scene);
        }
    }

    /**
     * Gets the currently active application theme.
     *
     * @return the active theme
     */
    public static Theme getCurrentTheme() {
        return currentTheme;
    }

    /**
     * Applies the current theme stylesheets to a specific scene.
     *
     * Existing stylesheets are replaced to ensure stale theme rules are removed.
     *
     * @param scene the scene to update
     */
    private static void applyToScene(Scene scene) {
        scene.getStylesheets().setAll(
                resource(currentTheme.path),
                resource(COMPONENTS_STYLESHEET)
        );
    }

    /**
     * Resolves a stylesheet resource path into an external stylesheet URL.
     *
     * @param path the classpath resource path
     * @return the resolved external stylesheet URL
     * @throws NullPointerException if the stylesheet resource cannot be found
     */
    private static String resource(String path) {
        return Objects.requireNonNull(
                MainApp.class.getResource(path),
                "Missing stylesheet: " + path
        ).toExternalForm();
    }
}