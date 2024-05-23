package com.kersnazzle.soundscapealpha

import com.kersnazzle.soundscapealpha.geojsonparser.geojson.FeatureCollection
import com.kersnazzle.soundscapealpha.geojsonparser.geojson.GeoMoshi
import com.kersnazzle.soundscapealpha.geojsonparser.geojson.LineString
import com.kersnazzle.soundscapealpha.geojsonparser.geojson.LngLatAlt
import com.kersnazzle.soundscapealpha.geojsonparser.geojson.Point
import com.kersnazzle.soundscapealpha.geojsonparser.geojson.Polygon
import com.kersnazzle.soundscapealpha.utils.RelativeDirections
import com.kersnazzle.soundscapealpha.utils.getFovIntersectionFeatureCollection
import com.kersnazzle.soundscapealpha.utils.getFovRoadsFeatureCollection
import com.kersnazzle.soundscapealpha.utils.getIntersectionRoadNames
import com.kersnazzle.soundscapealpha.utils.getIntersectionsFeatureCollectionFromTileFeatureCollection
import com.kersnazzle.soundscapealpha.utils.getNearestIntersection
import com.kersnazzle.soundscapealpha.utils.getReferenceCoordinate
import com.kersnazzle.soundscapealpha.utils.getRelativeDirectionsPolygons
import com.kersnazzle.soundscapealpha.utils.getRoadsFeatureCollectionFromTileFeatureCollection
import com.kersnazzle.soundscapealpha.utils.polygonContainsCoordinates
import com.squareup.moshi.Moshi
import org.junit.Test

 /**
 * These aren't really tests at this point just figuring our how to handle various intersection types.
 */

 //-----------------------------------------------//
 // Intersection Types - from original Soundscape //
 //----------------------------------------------//

//  Road Switch
//
//  | ↑ |
//  | B |
//  |   |
//  | ↑ |
//  | * |
//  |   |
//  | A |
//  | ↓ |

 // Weston Road to Long Ashton Road
 // https://geojson.io/#map=17.37/51.430494/-2.657463

//  Turn Right
//   _____________
//  |          B →
//  | ↑  _________
//  | * |
//  |   |
//  | A |
//  | ↓ |

 // Belgrave Place to Codrington Place
 //https://geojson.io/#map=19.64/51.4579382/-2.6157338

//  Turn Left
//  _____________
//  ← B          |
//  _________  ↑ |
//           | * |
//           |   |
//           | A |
//           | ↓ |

 // same again just depends what road you are standing on
 // Codrington Place to Belgrave Place
 //https://geojson.io/#map=19.64/51.4579382/-2.6157338

//  Side Road Right
//
//  | ↑ |
//  | A |
//  |   |_________
//  |          B →
//  | ↑  _________
//  | * |
//  |   |
//  | A |
//  | ↓ |
//
// Example: (51.457252, -0.970259) Side Road intersection with roads:
// Blagrave Street and Valpy Street.

//  Side Road Left
//
//           | ↑ |
//           | A |
//  _________|   |
//  ← B          |
//  _________  ↑ |
//           | * |
//           |   |
//           | A |
//           | ↓ |

//  T1
//  ___________________
//  ← B             B →
//  _______     _______
//         | ↑ |
//         | * |
//         |   |
//         | A |
//         | ↓ |
//
// Example: (51.455014, -0.982331) T intersection with roads:
// Russell Street and Oxford Street.
//
// Example: (51.455674, -0.973149) [Issue] T intersection with only (right) road.
// Broad Street and Chain Street.

//  T2
//  ___________________
//  ← B             C →
//  _______     _______
//         | ↑ |
//         | * |
//         |   |
//         | A |
//         | ↓ |

//  Cross1
//         | ↑ |
//         | A |
//  _______|   |_______
//  ← B             B →
//  _______     _______
//         | ↑ |
//         | * |
//         |   |
//         | A |
//         | ↓ |

//  Cross2
//         | ↑ |
//         | A |
//  _______|   |_______
//  ← B             C →
//  _______     _______
//         | ↑ |
//         | * |
//         |   |
//         | A |
//         | ↓ |

//  Multi
//         | ↑ |
//         | D |
//  _______|   |_______
//  ← B             C →
//  _______     _______
//         | ↑ |
//         | * |
//         |   |
//         | A |
//         | ↓ |
//
// Example: (51.455464, -0.975333) Multi (cross) intersection with roads:
// Oxford Road, West Street, Broad Street and St Mary's Butts.
class IntersectionsTest {
    @Test
    fun intersectionsStraightAheadType(){
        // Fake device location and pretend the device is pointing East.
        // -2.6577997643930757, 51.43041390383118
        val currentLocation = LngLatAlt(-2.6573400576040456, 51.430456817236575)
        val deviceHeading = 90.0
        val fovDistance = 50.0


        val moshi = GeoMoshi.registerAdapters(Moshi.Builder()).build()
        val featureCollectionTest = moshi.adapter(FeatureCollection::class.java)
            .fromJson(GeoJsonIntersectionStraight.intersectionStraightAheadFeatureCollection)
        // Get the roads from the tile
        val testRoadsCollectionFromTileFeatureCollection =
            getRoadsFeatureCollectionFromTileFeatureCollection(
                featureCollectionTest!!
            )
        // create FOV to pickup the roads
        val fovRoadsFeatureCollection = getFovRoadsFeatureCollection(
            currentLocation,
            deviceHeading,
            fovDistance,
            testRoadsCollectionFromTileFeatureCollection
        )
        // Get the intersections from the tile
        val testIntersectionsCollectionFromTileFeatureCollection =
            getIntersectionsFeatureCollectionFromTileFeatureCollection(
                featureCollectionTest!!
            )
        // Create a FOV triangle to pick up the intersection (this intersection is a transition from
        // Weston Road to Long Ashton Road)
        val fovIntersectionsFeatureCollection = getFovIntersectionFeatureCollection(
            currentLocation,
            deviceHeading,
            fovDistance,
            testIntersectionsCollectionFromTileFeatureCollection
        )
        val testNearestIntersection = getNearestIntersection(currentLocation,fovIntersectionsFeatureCollection)
        val testIntersectionRoadNames = getIntersectionRoadNames(testNearestIntersection, fovRoadsFeatureCollection)
        // what relative direction(s) are the road(s) that make up the nearest intersection?

        // first create a relative direction polygon and put it on the intersection node with the same
        // heading as the device
        val intersectionLocation = testNearestIntersection.features[0].geometry as Point
        val relativeDirections = getRelativeDirectionsPolygons(
            LngLatAlt(intersectionLocation.coordinates.longitude, intersectionLocation.coordinates.latitude),
            deviceHeading,
            fovDistance,
            RelativeDirections.COMBINED
        )

        // this should be clockwise from 6 o'clock
        // so the first road will be the road we are on (direction 0) - Weston Road
        // the second road which makes up the intersection is ahead left (direction 3) etc. Long Ashton Road
        for (direction in relativeDirections){
            for (road in testIntersectionRoadNames) {
                val testReferenceCoordinateForward = getReferenceCoordinate(
                    road.geometry as LineString, 25.0, false)
                val iAmHere1 = polygonContainsCoordinates(
                    testReferenceCoordinateForward, (direction.geometry as Polygon))
                if (iAmHere1){
                    println("Road name: ${road.properties!!["name"]}")
                    println("Road direction: ${direction.properties!!["Direction"]}")
                } else {
                    // reverse the LineString, create the ref coordinate and test it again
                    val testReferenceCoordinateReverse = getReferenceCoordinate(
                        road.geometry as LineString, 25.0, true
                    )
                    val iAmHere2 = polygonContainsCoordinates(testReferenceCoordinateReverse, (direction.geometry as Polygon))
                    if (iAmHere2){
                        println("Road name: ${road.properties!!["name"]}")
                        println("Road direction: ${direction.properties!!["Direction"]}")
                    }
                }
            }
        }
    }

    @Test
    fun intersectionsRightTurn(){
        // Fake device location and pretend the device is pointing South West and we are located on:
        // Belgrave Place
        val currentLocation = LngLatAlt(-2.615585745757045,51.457957257918395)
        val deviceHeading = 225.0 // South West
        val fovDistance = 50.0

        val moshi = GeoMoshi.registerAdapters(Moshi.Builder()).build()
        val featureCollectionTest = moshi.adapter(FeatureCollection::class.java)
            .fromJson(GeoJsonIntersectionRightAndLeftTurn.intersectionRightAndLeftTurn)
        // Get the roads from the tile
        val testRoadsCollectionFromTileFeatureCollection =
            getRoadsFeatureCollectionFromTileFeatureCollection(
                featureCollectionTest!!
            )
        // create FOV to pickup the roads
        val fovRoadsFeatureCollection = getFovRoadsFeatureCollection(
            currentLocation,
            deviceHeading,
            fovDistance,
            testRoadsCollectionFromTileFeatureCollection
        )
        // Get the intersections from the tile
        val testIntersectionsCollectionFromTileFeatureCollection =
            getIntersectionsFeatureCollectionFromTileFeatureCollection(
                featureCollectionTest!!
            )
        // Create a FOV triangle to pick up the intersection (this intersection is
        // a right turn transition from Belgrave Place to Codrington Place)
        val fovIntersectionsFeatureCollection = getFovIntersectionFeatureCollection(
            currentLocation,
            deviceHeading,
            fovDistance,
            testIntersectionsCollectionFromTileFeatureCollection
        )
        val testNearestIntersection = getNearestIntersection(currentLocation,fovIntersectionsFeatureCollection)
        val testIntersectionRoadNames = getIntersectionRoadNames(testNearestIntersection, fovRoadsFeatureCollection)
        // what relative direction(s) are the road(s) that make up the nearest intersection?

        // first create a relative direction polygon and put it on the intersection node with the same
        // heading as the device
        val intersectionLocation = testNearestIntersection.features[0].geometry as Point
        val relativeDirections = getRelativeDirectionsPolygons(
            LngLatAlt(intersectionLocation.coordinates.longitude, intersectionLocation.coordinates.latitude),
            deviceHeading,
            fovDistance,
            RelativeDirections.COMBINED
        )

        // this should be clockwise from 6 o'clock
        // so the first road will be the road we are on (direction 0) - Belgrave PLace
        // the second road which makes up the intersection is right (direction 6) etc. Codrington Place
        for (direction in relativeDirections){
            for (road in testIntersectionRoadNames) {
                val testReferenceCoordinateForward = getReferenceCoordinate(
                    road.geometry as LineString, 25.0, false)
                val iAmHere1 = polygonContainsCoordinates(
                    testReferenceCoordinateForward, (direction.geometry as Polygon))
                if (iAmHere1){
                    println("Road name: ${road.properties!!["name"]}")
                    println("Road direction: ${direction.properties!!["Direction"]}")
                } else {
                    // reverse the LineString, create the ref coordinate and test it again
                    val testReferenceCoordinateReverse = getReferenceCoordinate(
                        road.geometry as LineString, 25.0, true
                    )
                    val iAmHere2 = polygonContainsCoordinates(testReferenceCoordinateReverse, (direction.geometry as Polygon))
                    if (iAmHere2){
                        println("Road name: ${road.properties!!["name"]}")
                        println("Road direction: ${direction.properties!!["Direction"]}")
                    }
                }
            }
        }
    }

     @Test
     fun intersectionsLeftTurn(){
         // Fake device location and pretend the device is pointing South East and we are standing on:
         // Codrington Place
         val currentLocation = LngLatAlt(-2.6159411752634583, 51.45799104056931)
         val deviceHeading = 135.0 // South East
         val fovDistance = 50.0

         val moshi = GeoMoshi.registerAdapters(Moshi.Builder()).build()
         val featureCollectionTest = moshi.adapter(FeatureCollection::class.java)
             .fromJson(GeoJsonIntersectionRightAndLeftTurn.intersectionRightAndLeftTurn)
         // Get the roads from the tile
         val testRoadsCollectionFromTileFeatureCollection =
             getRoadsFeatureCollectionFromTileFeatureCollection(
                 featureCollectionTest!!
             )
         // create FOV to pickup the roads
         val fovRoadsFeatureCollection = getFovRoadsFeatureCollection(
             currentLocation,
             deviceHeading,
             fovDistance,
             testRoadsCollectionFromTileFeatureCollection
         )
         // Get the intersections from the tile
         val testIntersectionsCollectionFromTileFeatureCollection =
             getIntersectionsFeatureCollectionFromTileFeatureCollection(
                 featureCollectionTest!!
             )
         // Create a FOV triangle to pick up the intersection (this intersection is
         // a left turn transition from Codrington Place to Belgrave Place to)
         val fovIntersectionsFeatureCollection = getFovIntersectionFeatureCollection(
             currentLocation,
             deviceHeading,
             fovDistance,
             testIntersectionsCollectionFromTileFeatureCollection
         )
         val testNearestIntersection = getNearestIntersection(currentLocation,fovIntersectionsFeatureCollection)
         val testIntersectionRoadNames = getIntersectionRoadNames(testNearestIntersection, fovRoadsFeatureCollection)
         // what relative direction(s) are the road(s) that make up the nearest intersection?

         // first create a relative direction polygon and put it on the intersection node with the same
         // heading as the device
         val intersectionLocation = testNearestIntersection.features[0].geometry as Point
         val relativeDirections = getRelativeDirectionsPolygons(
             LngLatAlt(intersectionLocation.coordinates.longitude, intersectionLocation.coordinates.latitude),
             deviceHeading,
             fovDistance,
             RelativeDirections.COMBINED
         )

         // this should be clockwise from 6 o'clock
         // so the first road will be the road we are on (direction 0) - Codrington Place
         // the second road which makes up the intersection is left (direction 2) etc. Belgrave Place
         for (direction in relativeDirections){
             for (road in testIntersectionRoadNames) {
                 val testReferenceCoordinateForward = getReferenceCoordinate(
                     road.geometry as LineString, 25.0, false)
                 val iAmHere1 = polygonContainsCoordinates(
                     testReferenceCoordinateForward, (direction.geometry as Polygon))
                 if (iAmHere1){
                     println("Road name: ${road.properties!!["name"]}")
                     println("Road direction: ${direction.properties!!["Direction"]}")
                 } else {
                     // reverse the LineString, create the ref coordinate and test it again
                     val testReferenceCoordinateReverse = getReferenceCoordinate(
                         road.geometry as LineString, 25.0, true
                     )
                     val iAmHere2 = polygonContainsCoordinates(testReferenceCoordinateReverse, (direction.geometry as Polygon))
                     if (iAmHere2){
                         println("Road name: ${road.properties!!["name"]}")
                         println("Road direction: ${direction.properties!!["Direction"]}")
                     }
                 }
             }
         }

     }
}