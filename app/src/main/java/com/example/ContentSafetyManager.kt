package com.example

import android.content.Context
import android.util.Log

// Sensitivity Levels
enum class ContentSensitivityLevel(val value: Int) {
    OFF(0),
    LOW(1),
    MEDIUM(2),
    HIGH(3);

    companion object {
        fun fromValue(value: Int): ContentSensitivityLevel {
            return values().find { it.value == value } ?: MEDIUM
        }
    }
}

// Prediction Categories
object ContentCategory {
    const val ADULT = "Adult / Explicit"
    const val VIOLENCE = "Violence / Self-Harm"
    const val DRUGS = "Drugs / Illegal Substances"
    const val GAMBLING = "Gambling / Restricted"
    const val SUGGESTIVE = "Suggestive / Dating"
}

// Diagnostic analysis result
data class ContentDetectionResult(
    val isRisky: Boolean,
    val matchedKeyword: String,
    val category: String,
    val severity: String
)

/**
 * Future modular interface for AI runtime image analysis.
 * Adheres to Phase 7: "Build the architecture in a modular way so future AI-powered image analysis can be added later."
 */
interface ImageSafetyAnalyzer {
    suspend fun analyzeScreenImage(imageBytes: ByteArray): Boolean
    suspend fun isModelAvailable(): Boolean
}

class ContentSafetyManager(private val context: Context) {

    // Preloaded robust dictionary of local key terms to ensure zero network uploads (privacy-first)
    private val adultKeywords = setOf(
        "porn", "pornography", "xxx", "hentai", "nsfw", "adult video", "adult content",
        "redtube", "pornhub", "xvideos", "sex video", "pussy", "blowjob", "erotic", "nude",
        "nudity", "naked", "strip club", "playboy", "anal sex", "vagina", "clitoris", "intercourse"
    )

    private val violenceKeywords = setOf(
        "suicide", "kill myself", "how to suicide", "commit suicide", "blood gore", "decapitation",
        "self harm", "cutting myself", "torture", "mass murder", "school shooting", "make a bomb"
    )

    private val drugsKeywords = setOf(
        "buy cocaine", "buy meth", "buy heroin", "buy weed online", "fentanyl buy", "crack cocaine",
        "drug dealer", "psychedelic mushrooms", "buying lsd", "extasy pill", "crystal meth"
    )

    private val gamblingKeywords = setOf(
        "online casino", "bet online", "sports gambling", "slot machine hacks", "poker online real money",
        "roulette cheat", "gambling slots"
    )

    private val suggestiveKeywords = setOf(
        "tinder dating", "onlyfans account", "sugar daddy", "adult friend finder", "meet local singles",
        "hookup tonight", "escort service", "erotic massage", "slutty dating"
    )

    /**
     * Highly optimized local analysis of extracted screen text.
     * Evaluates text according to current sensitivity levels and filters.
     * Returns a valid [ContentDetectionResult].
     */
    fun analyzeText(text: String, sensitivityLevel: ContentSensitivityLevel): ContentDetectionResult {
        if (sensitivityLevel == ContentSensitivityLevel.OFF) {
            return ContentDetectionResult(false, "", "", "")
        }

        val cleanText = text.trim().lowercase()
        if (cleanText.isEmpty()) {
            return ContentDetectionResult(false, "", "", "")
        }

        // 1. Evaluate LOW Level rules (Only extremely explicit adult terms)
        for (keyword in adultKeywords) {
            // Priority check extremely harmful/explicit adult
            if (keyword in listOf("porn", "pornography", "xxx", "hentai", "nsfw", "adult video", "redtube", "pornhub", "xvideos")) {
                if (cleanText.contains(keyword)) {
                    return ContentDetectionResult(true, keyword, ContentCategory.ADULT, "HIGH")
                }
            }
        }

        // 2. Evaluate MEDIUM Level rules (Low + other explicit terms, violence/self-harm, drugs)
        if (sensitivityLevel.value >= ContentSensitivityLevel.MEDIUM.value) {
            // General adult
            for (keyword in adultKeywords) {
                if (cleanText.contains(keyword)) {
                    return ContentDetectionResult(true, keyword, ContentCategory.ADULT, "HIGH")
                }
            }
            // Violence and self harm
            for (keyword in violenceKeywords) {
                if (cleanText.contains(keyword)) {
                    return ContentDetectionResult(true, keyword, ContentCategory.VIOLENCE, "HIGH")
                }
            }
            // Strict drug dealing or illegal purchase
            for (keyword in drugsKeywords) {
                if (cleanText.contains(keyword)) {
                    return ContentDetectionResult(true, keyword, ContentCategory.DRUGS, "HIGH")
                }
            }
        }

        // 3. Evaluate HIGH / STRICT Level rules (Medium + suggestive dating, gambling, additional general terms)
        if (sensitivityLevel.value >= ContentSensitivityLevel.HIGH.value) {
            for (keyword in suggestiveKeywords) {
                if (cleanText.contains(keyword)) {
                    return ContentDetectionResult(true, keyword, ContentCategory.SUGGESTIVE, "MEDIUM")
                }
            }
            for (keyword in gamblingKeywords) {
                if (cleanText.contains(keyword)) {
                    return ContentDetectionResult(true, keyword, ContentCategory.GAMBLING, "MEDIUM")
                }
            }
        }

        return ContentDetectionResult(false, "", "", "")
    }

    /**
     * Supported application bundles for targeted scans to optimize battery.
     */
    fun isSupportedApp(packageName: String): Boolean {
        val supportedList = setOf(
            "com.android.chrome",
            "org.mozilla.firefox",
            "org.mozilla.focus",
            "com.brave.browser",
            "com.microsoft.emmx",
            "com.sec.android.app.sbrowser",
            "com.opera.browser",
            "com.instagram.android",
            "com.facebook.katana",
            "com.facebook.lite",
            "com.zhiliaoapp.musically", // TikTok
            "com.twitter.android", // Twitter/X
            "com.snapchat.android",
            "com.reddit.frontpage",
            "com.google.android.youtube",
            "com.example.digitalparent" // Demo/Manual sandbox testing
        )
        return supportedList.contains(packageName)
    }
}
