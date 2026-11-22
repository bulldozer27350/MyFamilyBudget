package com.moe.myfamilybudget.server.internal.updatecheck;

/**
 * Sous-ensemble utile ici de la réponse de l'API GitHub
 * {@code GET /repos/{owner}/{repo}/releases/latest}.
 */
record GithubRelease(String tagName, String htmlUrl, String downloadUrl, String releaseNotes, String publishedAt) {
}
