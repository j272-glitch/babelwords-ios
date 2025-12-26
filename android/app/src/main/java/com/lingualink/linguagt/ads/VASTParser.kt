package com.lingualink.linguagt.ads

import android.util.Xml
import com.lingualink.linguagt.TestRigorLogger
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

data class VASTAdData(
    val mediaUrl: String?,
    val duration: Int,
    val clickThroughUrl: String?,
    val impressionUrls: List<String>,
    val trackingEvents: Map<String, List<String>>,
    val errorUrls: List<String>
)

class VASTParser {
    
    companion object {
        private const val TAG = "VASTParser"
    }
    
    fun parse(vastXml: String): VASTAdData? {
        return try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(vastXml))
            
            var mediaUrl: String? = null
            var duration = 0
            var clickThroughUrl: String? = null
            val impressionUrls = mutableListOf<String>()
            val trackingEvents = mutableMapOf<String, MutableList<String>>()
            val errorUrls = mutableListOf<String>()
            
            var currentEvent: String? = null
            var inMediaFile = false
            var inClickThrough = false
            var inImpression = false
            var inTracking = false
            var inDuration = false
            var inError = false
            
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "MediaFile" -> {
                                inMediaFile = true
                            }
                            "ClickThrough" -> {
                                inClickThrough = true
                            }
                            "Impression" -> {
                                inImpression = true
                            }
                            "Tracking" -> {
                                inTracking = true
                                currentEvent = parser.getAttributeValue(null, "event")
                            }
                            "Duration" -> {
                                inDuration = true
                            }
                            "Error" -> {
                                inError = true
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim()
                        if (!text.isNullOrEmpty()) {
                            when {
                                inMediaFile && mediaUrl == null -> {
                                    mediaUrl = extractUrl(text)
                                }
                                inClickThrough -> {
                                    clickThroughUrl = extractUrl(text)
                                }
                                inImpression -> {
                                    extractUrl(text)?.let { impressionUrls.add(it) }
                                }
                                inTracking && currentEvent != null -> {
                                    extractUrl(text)?.let { url ->
                                        trackingEvents.getOrPut(currentEvent!!) { mutableListOf() }.add(url)
                                    }
                                }
                                inDuration -> {
                                    duration = parseDuration(text)
                                }
                                inError -> {
                                    extractUrl(text)?.let { errorUrls.add(it) }
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "MediaFile" -> inMediaFile = false
                            "ClickThrough" -> inClickThrough = false
                            "Impression" -> inImpression = false
                            "Tracking" -> {
                                inTracking = false
                                currentEvent = null
                            }
                            "Duration" -> inDuration = false
                            "Error" -> inError = false
                        }
                    }
                }
                eventType = parser.next()
            }
            
            if (mediaUrl != null) {
                TestRigorLogger.logAdEvent("VAST parsed: mediaUrl=$mediaUrl, duration=$duration, impressions=${impressionUrls.size}, tracking=${trackingEvents.size}")
                VASTAdData(
                    mediaUrl = mediaUrl,
                    duration = duration,
                    clickThroughUrl = clickThroughUrl,
                    impressionUrls = impressionUrls,
                    trackingEvents = trackingEvents.mapValues { it.value.toList() },
                    errorUrls = errorUrls
                )
            } else {
                TestRigorLogger.logWarning("VAST parse failed: no media URL found")
                null
            }
        } catch (e: Exception) {
            TestRigorLogger.logError("VAST parse error", e)
            null
        }
    }
    
    private fun extractUrl(text: String): String? {
        val cleaned = text.trim()
            .removePrefix("<![CDATA[")
            .removeSuffix("]]>")
            .trim()
        return if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) {
            cleaned
        } else {
            null
        }
    }
    
    private fun parseDuration(duration: String): Int {
        return try {
            val parts = duration.split(":")
            when (parts.size) {
                3 -> {
                    val hours = parts[0].toIntOrNull() ?: 0
                    val minutes = parts[1].toIntOrNull() ?: 0
                    val seconds = parts[2].split(".")[0].toIntOrNull() ?: 0
                    hours * 3600 + minutes * 60 + seconds
                }
                2 -> {
                    val minutes = parts[0].toIntOrNull() ?: 0
                    val seconds = parts[1].split(".")[0].toIntOrNull() ?: 0
                    minutes * 60 + seconds
                }
                1 -> parts[0].split(".")[0].toIntOrNull() ?: 0
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }
}
