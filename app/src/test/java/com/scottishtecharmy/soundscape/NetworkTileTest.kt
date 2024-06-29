package com.scottishtecharmy.soundscape

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.scottishtecharmy.soundscape.geojsonparser.geojson.FeatureCollection
import com.scottishtecharmy.soundscape.geojsonparser.geojson.GeoMoshi
import com.scottishtecharmy.soundscape.network.Tiles
import com.scottishtecharmy.soundscape.utils.getXYTile
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule

class NetworkTileTest {
    @get:Rule
    var rule: TestRule = InstantTaskExecutorRule()

    lateinit var tiles: Tiles
    lateinit var tile: String
    private val moshi = GeoMoshi.registerAdapters(Moshi.Builder()).build()
    lateinit var featureCollectionTest: FeatureCollection

    @Test
    fun testTile() = runTest {
        givenTilesIsInitialized()
        whenTileDataIsReadAndParsedIntoAFeatureCollection()
        thenTheFeatureCollectionShouldContainLetterBox()
    }

    private fun givenTilesIsInitialized() {
        tiles = Tiles()
    }

    private suspend fun whenTileDataIsReadAndParsedIntoAFeatureCollection() {
        // center of tile
        val xyTilePair = getXYTile(51.43860066718254, -2.69439697265625, 16)

        tile = tiles.getTile(xyTilePair.first, xyTilePair.second)!!
        Assert.assertNotNull(tile)

        featureCollectionTest = moshi.adapter(FeatureCollection::class.java).fromJson(tile)!!
        Assert.assertNotNull(featureCollectionTest)

    }

    private fun thenTheFeatureCollectionShouldContainLetterBox() {

        for (feature in featureCollectionTest.features) {
            if (feature.foreign!!["feature_value"] == "letter_box") {
                Assert.assertEquals("letter_box", feature.foreign!!["feature_value"])
            }
        }
    }

}