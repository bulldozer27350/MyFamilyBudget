package com.moe.myfamilybudget.server.launcher;

import java.awt.Desktop;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Gestionnaire de journalisation et fallback pour l'ouverture du navigateur.
 * Note : Le lancement direct est géré en amont par le SplashScreen autonome dans les scripts de lancement.
 */
@Component
public class DesktopLauncher {

    private static final Logger log = LoggerFactory.getLogger(DesktopLauncher.class);

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        Environment env = event.getApplicationContext().getEnvironment();
        String port = env.getProperty("local.server.port", env.getProperty("server.port", "8080"));
        String contextPath = env.getProperty("server.servlet.context-path", "/api/v1");

        if (!contextPath.startsWith("/")) {
            contextPath = "/" + contextPath;
        }
        if (contextPath.endsWith("/")) {
            contextPath = contextPath.substring(0, contextPath.length() - 1);
        }

        String targetUrl = "http://localhost:" + port + contextPath + "/overview.html";
        log.info("==================================================================");
        log.info("  MyFamilyBudget - Serveur Spring Boot démarré avec succès !");
        log.info("  URL d'accès : {}", targetUrl);
        log.info("==================================================================");

        // Si explicitement configuré à true (par défaut false car géré par le splashscreen immédiat)
        boolean autoOpen = Boolean.parseBoolean(env.getProperty("myfamilybudget.browser.auto-open", "false"));
        if (autoOpen && !isExplicitlyHeadless(env)) {
            openSystemBrowser(targetUrl);
        }
    }

    public static void openSystemBrowser(String url) {
        new Thread(() -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(URI.create(url));
                    return;
                }
            } catch (Throwable ignored) {
            }

            String os = System.getProperty("os.name", "").toLowerCase();
            try {
                if (os.contains("win")) {
                    new ProcessBuilder("cmd.exe", "/c", "start", "", url).start();
                } else if (os.contains("mac")) {
                    new ProcessBuilder("open", url).start();
                } else if (os.contains("nix") || os.contains("nux")) {
                    new ProcessBuilder("xdg-open", url).start();
                }
            } catch (Throwable ignored) {
            }
        }, "browser-launcher-thread").start();
    }

    private static boolean isExplicitlyHeadless(Environment env) {
        if ("true".equalsIgnoreCase(System.getenv("HEADLESS")) || "true".equalsIgnoreCase(System.getenv("CI"))) {
            return true;
        }
        return false;
    }
}
