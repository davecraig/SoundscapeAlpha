package com.scottishtecharmy.soundscape.dto

import com.scottishtecharmy.soundscape.geojsonparser.geojson.Feature

data class IntersectionRelativeDirections(
    val direction: Int,
    val feature: Feature
)
