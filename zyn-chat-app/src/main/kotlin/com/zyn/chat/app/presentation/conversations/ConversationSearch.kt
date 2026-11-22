package com.zyn.chat.app.presentation.conversations

/**
 * The recent conversations whose title matches [query], keeping the given (newest first) order.
 *
 * Matching is case-insensitive and term-based: the query is split on whitespace and *every* term has
 * to appear somewhere in the title. So "coffee rome" finds "Best coffee shops in Rome" whatever order
 * the terms were typed in, which is what someone searching a list of sentence-long titles expects.
 * This is deliberately looser than
 * [com.zyn.chat.app.data.providers.ProviderCatalog.search], which matches a short provider name by
 * substring: a catalog name is a word or three, while a chat title is a whole sentence and is rarely
 * recalled in order.
 *
 * Only the title is searched, because the title is the only text a drawer row shows. Matching a field
 * the row never displays would produce rows with no visible reason for being in the results.
 *
 * An empty or blank [query] matches everything, so the caller needs no separate branch.
 *
 * @param query Text the person typed in the drawer's search field.
 */
public fun List<Conversation>.matching(query: String): List<Conversation> {
    // lowercase() is locale-independent (it folds with Locale.ROOT), so a Turkish locale cannot turn
    // "I" into a dotless "ı" and change which chats match the same query.
    val terms = query.trim()
        .split(WHITESPACE)
        .filter { term -> term.isNotEmpty() }
        .map { term -> term.lowercase() }

    if (terms.isEmpty()) return this

    return filter { conversation ->
        val title = conversation.title.lowercase()
        terms.all { term -> title.contains(term) }
    }
}

private val WHITESPACE = "\\s+".toRegex()
