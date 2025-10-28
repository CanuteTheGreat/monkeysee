package com.monkeysee.app

/**
 * Streaming quality presets
 */
enum class QualityPreset(
    val width: Int,
    val height: Int,
    val bitrate: Int,
    val fps: Int,
    val audioBitrate: Int
) {
    /** Low quality - 480p @ 15fps, 0.5 Mbps video */
    LOW(640, 480, 500_000, 15, 64_000),

    /** Medium quality - 720p @ 24fps, 1.5 Mbps video */
    MEDIUM(1280, 720, 1_500_000, 24, 96_000),

    /** High quality - 720p @ 30fps, 2 Mbps video (default) */
    HIGH(1280, 720, 2_000_000, 30, 128_000),

    /** Ultra quality - 1080p @ 30fps, 4 Mbps video */
    ULTRA(1920, 1080, 4_000_000, 30, 192_000);

    companion object {
        val DEFAULT = HIGH
    }
}

/**
 * Custom streaming configuration
 */
data class StreamingConfig(
    val videoWidth: Int = 1280,
    val videoHeight: Int = 720,
    val videoBitrate: Int = 2_000_000,
    val videoFps: Int = 30,
    val audioBitrate: Int = 128_000,
    val audioSampleRate: Int = 48000,
    val audioChannels: Int = 2
) {
    companion object {
        fun fromPreset(preset: QualityPreset) = StreamingConfig(
            videoWidth = preset.width,
            videoHeight = preset.height,
            videoBitrate = preset.bitrate,
            videoFps = preset.fps,
            audioBitrate = preset.audioBitrate
        )

        val DEFAULT = fromPreset(QualityPreset.DEFAULT)
    }

    /**
     * Get a human-readable description of this configuration
     */
    fun getDescription(): String {
        val videoBitrateMbps = videoBitrate / 1_000_000.0
        val audioBitrateKbps = audioBitrate / 1_000
        return "${videoWidth}x${videoHeight} @ ${videoFps}fps, " +
               "${String.format("%.1f", videoBitrateMbps)} Mbps video, " +
               "${audioBitrateKbps} kbps audio"
    }
}
