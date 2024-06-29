package com.scottishtecharmy.soundscape.dto

import com.scottishtecharmy.soundscape.geojsonparser.geojson.LngLatAlt

data class BoundingBoxCorners(
    var northWestCorner: LngLatAlt = LngLatAlt(0.0, 0.0),
    var southWestCorner: LngLatAlt = LngLatAlt(0.0, 0.0),
    var southEastCorner: LngLatAlt = LngLatAlt(0.0, 0.0),
    var northEastCorner: LngLatAlt = LngLatAlt(0.0, 0.0)
)