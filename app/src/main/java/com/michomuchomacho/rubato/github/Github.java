package com.michomuchomacho.rubato.github;

import com.michomuchomacho.rubato.github.api.release.ReleaseClient;

public class Github {
    private static final String OWNER = "andreagattironi";
    private static final String REPO = "rubato";
    private ReleaseClient releaseClient;

    public ReleaseClient getReleaseClient() {
        if (releaseClient == null) {
            releaseClient = new ReleaseClient(this);
        }

        return releaseClient;
    }

    public String getUrl() {
        return "https://api.github.com/";
    }

    public static String getOwner() {
        return OWNER;
    }

    public static String getRepo() {
        return REPO;
    }
}
