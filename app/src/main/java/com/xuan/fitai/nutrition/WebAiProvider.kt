package com.xuan.fitai.nutrition

import java.net.URI
import kotlin.random.Random

enum class WebAiProvider(val label: String, val foodId: String) {
    GOOGLE("Google AI", "GOOGLE-AI-1"), BRAVE("Brave AI", "BRAVE-AI-1"), DUCK("Duck.ai", "DUCK-AI-1");
    companion object {
        fun fromUrl(url: String): WebAiProvider? = runCatching {
            val uri = URI(url)
            if (uri.scheme != "https" || uri.userInfo != null || uri.port != -1 || url.length > 1800) return null
            when {
                isGoogleAiSearchUrl(url) -> GOOGLE
                uri.host == "search.brave.com" && uri.path == "/ask" -> BRAVE
                uri.host == "duck.ai" && uri.path in listOf("", "/") -> DUCK
                else -> null
            }
        }.getOrNull()
    }
}

/** Select only implemented providers and exclude the last actual network selection, even after failure. */
class WebAiRotation(
    private val providers: List<WebAiProvider> = WebAiProvider.entries,
    initial: WebAiProvider? = null,
    private val random: Random = Random.Default,
    private val save: (WebAiProvider) -> Unit = {},
) {
    private var last = initial
    init { require(providers.isNotEmpty() && providers.distinct().size == providers.size) }
    @Synchronized fun next(exclude: Set<WebAiProvider> = emptySet()): WebAiProvider? {
        val candidates = providers.filter { it != last && it !in exclude }
        if (candidates.isEmpty()) return null
        return candidates.random(random).also { last = it; save(it) }
    }
}

val NutritionFood.webAiProvider: WebAiProvider? get() = WebAiProvider.entries.firstOrNull { foodId ->
    id == foodId.foodId
}

fun nutritionEstimatorLabel(raw: String?, estimated: Boolean): String? {
    if (!estimated) return null
    val reference = NutritionReferenceJson.decodeOrNull(raw)
    val food = reference?.foods?.firstOrNull { it.id == reference.selectedFoodId } ?: reference?.foods?.firstOrNull()
    return food?.webAiProvider?.let { provider ->
        if (provider == WebAiProvider.DUCK && food.category.startsWith("Duck.ai / ")) food.category else provider.label
    } ?: "本地 Gemma"
}
