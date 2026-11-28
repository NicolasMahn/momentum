package de.nicolasmahn.momentum.feature.logactivity.route

import android.content.Context
import android.util.Log
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.nicolasmahn.momentum.BuildConfig
import de.nicolasmahn.momentum.R
import de.nicolasmahn.momentum.core.locale.currentLocale
import de.nicolasmahn.momentum.core.ui.LocalViewColors
import de.nicolasmahn.momentum.domain.activity.route.Route
import java.util.Locale
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.MultiLineString
import org.maplibre.geojson.Point

/**
 * A route drawn in the view's accent over a dark map of where it went, framed to show all of it.
 * The map only shows; it takes no gestures, because it sits in a screen that scrolls, and a map
 * that swallowed the scroll would trap the thumb. The tiles come from Momentum's own map server
 * ([BuildConfig.MAP_TILES_URL]); the style and its fonts ship with the app. Without a connection
 * the route still draws, on an empty ground.
 */
@Composable
internal fun RouteMap(route: Route, modifier: Modifier = Modifier, height: Dp = 220.dp, testTag: String = "activity_route_map") {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val accent = LocalViewColors.current.accent.toArgb()
    val language = currentLocale().language
    val mapView = remember { routeMapView(context) }
    if (mapView == null) {
        // No map on this phone (or in a test on the PC): the space stays, so nothing under it moves.
        Box(modifier.fillMaxWidth().height(height).testTag(testTag))
        return
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }

    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        mapView.onCreate(null)
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView, language) {
        mapView.getMapAsync { loaded ->
            loaded.uiSettings.setAllGesturesEnabled(false)
            loaded.uiSettings.isAttributionEnabled = false
            loaded.uiSettings.isLogoEnabled = false
            loaded.uiSettings.isCompassEnabled = false
            map = loaded
            // The style being replaced is gone once setStyle runs; nothing may touch it meanwhile.
            style = null
            loaded.setStyle(Style.Builder().fromJson(styleJson(context, language))) { loadedStyle ->
                loadedStyle.addSource(GeoJsonSource(RouteSource))
                loadedStyle.addLayer(
                    LineLayer(RouteLayer, RouteSource).withProperties(
                        PropertyFactory.lineColor(accent),
                        PropertyFactory.lineWidth(4.5f),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    ),
                )
                style = loadedStyle
            }
        }
    }

    LaunchedEffect(map, style, route, accent) {
        val loadedMap = map ?: return@LaunchedEffect
        val loadedStyle = style ?: return@LaunchedEffect
        loadedStyle.getSourceAs<GeoJsonSource>(RouteSource)?.setGeoJson(route.feature())
        (loadedStyle.getLayer(RouteLayer) as? LineLayer)?.setProperties(PropertyFactory.lineColor(accent))
        frame(loadedMap, route, context)
    }

    Column(modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(12.dp))
                .testTag(testTag),
        )
        // The map's data is OpenStreetMap's, whose licence asks for this line, leading to it, wherever it is shown.
        val attribution = stringResource(R.string.activity_route_map_attribution)
        Text(
            text = buildAnnotatedString { withLink(LinkAnnotation.Url(OpenStreetMapCopyright)) { append(attribution) } },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically),
        )
    }
}

/** The map's view, or null where MapLibre's native part cannot start: a phone without its processor's build, or a test on the PC. */
private fun routeMapView(context: Context): MapView? = runCatching {
    MapLibre.getInstance(context)
    // A texture, not a surface, so the map scrolls with the screen and is clipped like any view.
    MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true)).apply {
        // TalkBack would read MapLibre's own English hint about dragging, which this map does not take;
        // the numbers beside it say what it shows.
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }
}.onFailure { Log.w("RouteMap", "The map cannot start here", it) }.getOrNull()

/** The app's style in the label language closest to [language], pointed at the map server. */
private fun styleJson(context: Context, language: String): String {
    val file = if (language == Locale.GERMAN.language) "map/style-de.json" else "map/style-en.json"
    return context.assets.open(file).bufferedReader().use { it.readText() }.replace(TilesPlaceholder, BuildConfig.MAP_TILES_URL)
}

private fun Route.feature(): Feature =
    Feature.fromGeometry(MultiLineString.fromLngLats(stretches.map { stretch -> stretch.map { Point.fromLngLat(it.longitude, it.latitude) } }))

/** All of the route in view; a route of one point, or none yet, is shown close up around where it is. */
private fun frame(map: MapLibreMap, route: Route, context: Context) {
    val points = route.points.map { LatLng(it.latitude, it.longitude) }
    val padding = (FramePaddingDp * context.resources.displayMetrics.density).toInt()
    when {
        points.isEmpty() -> Unit
        points.distinct().size == 1 -> map.moveCamera(CameraUpdateFactory.newLatLngZoom(points.first(), CloseZoom))
        else -> {
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(points).build(), padding))
            // A short route framed edge to edge would show a single block; streets around it say more.
            if (map.cameraPosition.zoom > CloseZoom) map.moveCamera(CameraUpdateFactory.zoomTo(CloseZoom))
        }
    }
}

private const val OpenStreetMapCopyright = "https://www.openstreetmap.org/copyright"
private const val RouteSource = "momentum-route"
private const val RouteLayer = "momentum-route-line"
private const val TilesPlaceholder = "{tiles}"
private const val FramePaddingDp = 28
private const val CloseZoom = 16.0
