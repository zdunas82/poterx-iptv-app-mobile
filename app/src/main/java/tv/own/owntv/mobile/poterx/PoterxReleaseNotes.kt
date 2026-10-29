package tv.own.owntv.mobile.poterx

/**
 * Poterx: the release notes a locked build shows in the update card. The notes arrive as the
 * release's body, which GitHub likes to decorate with links — "Full Changelog: https://…",
 * "by @user in https://…/pull/12", `[text](url)` — and a customer must never see a repository
 * address. Every line that names a link or the repository is dropped, inline links keep their text.
 */
object PoterxReleaseNotes {
    private val markdownLink = Regex("""\[([^\]]*)]\([^)]*\)""")
    private val bareUrl = Regex("""(?i)\b[a-z][a-z0-9+.-]*://\S+""")
    private val byUserIn = Regex("""(?i)\s+by\s+@\S+\s+in\s*$""")
    private val dropLine = Regex("""(?i)github|full changelog|what's changed|new contributors|^\*\*changelog\*\*""")

    fun clean(notes: String): String {
        if (!LockedSource.enabled) return notes
        return notes.replace("\r\n", "\n").split("\n")
            .map { line ->
                line.replace(markdownLink, "$1")
                    .replace(bareUrl, "")
                    .replace(byUserIn, "")
                    .trimEnd()
            }
            .filterNot { line -> dropLine.containsMatchIn(line) }
            .joinToString("\n")
            .trim()
    }
}
