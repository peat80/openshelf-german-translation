@file:OptIn(ExperimentalFoundationApi::class)

package io.github.wingedpanther.openshelf

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Tab(val label: String) { Discover("Discover"), Categories("Categories"), Search("Search"), Mine("My Apps"), Settings("Settings") }

class FocusHandoff {
    val target = FocusRequester()
    var pending by mutableStateOf(true)
}

@Composable
fun OpenShelf() {
    val activity = LocalContext.current as Activity
    var tab by remember { mutableStateOf(Tab.Discover) }
    var page by remember { mutableStateOf<String?>(null) }
    var wallId by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var room by remember { mutableStateOf<String?>(null) }
    var lastRoom by remember { mutableStateOf<String?>(null) }
    var lastInRoom by remember { mutableStateOf<String?>(null) }
    val roomGrid = remember(room) { LazyGridState() }
    var roomRecent by remember(room) { mutableStateOf(false) }
    var tabsFocused by remember { mutableStateOf(false) }
    val tabRequesters = remember { Tab.entries.associateWith { FocusRequester() } }
    val handoff = remember { FocusHandoff() }
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { Catalog.load(ctx) }
    if (Catalog.apps.isEmpty()) {
        Box(Modifier.fillMaxSize().background(C.Wall), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(C.mark), null, Modifier.size(34.dp))
                Spacer(Modifier.width(10.dp))
                T("Open Shelf", 24.sp, weight = FontWeight.SemiBold, tight = true)
            }
        }
        return
    }

    val openApp: (String) -> Unit = { page = it; handoff.pending = true }
    val openRoom: (String) -> Unit = { room = it; lastRoom = it; lastInRoom = null; handoff.pending = true }
    val closePage = {
        if (page != null) page = null else room = null
        handoff.pending = true
    }

    BackHandler {
        when {
            page != null || room != null -> closePage()
            !tabsFocused -> runCatching { tabRequesters.getValue(tab).requestFocus() }
            else -> activity.finish()
        }
    }

    Box(Modifier.fillMaxSize().background(C.Wall)) {
        Box(Modifier.fillMaxSize().padding(top = 70.dp)) {
            val app = page?.let { Catalog.byId(it) }
            val r = room
            if (app != null) {
                AppPage(app, handoff)
            } else if (r != null) {
                RoomPage(r, roomGrid, roomRecent, { roomRecent = it }, lastInRoom, { lastInRoom = it }, openApp, handoff)
            } else when (tab) {
                Tab.Discover -> Discover(wallId, { wallId = it }, openApp, openRoom, handoff)
                Tab.Categories -> Categories(lastRoom, openRoom, handoff)
                Tab.Search -> SearchScreen(query, { query = it }, openApp, handoff)
                Tab.Mine -> MyApps(openApp, { tab = Tab.Discover; handoff.pending = true }, handoff)
                Tab.Settings -> SettingsScreen(handoff)
            }
        }
        TopBar(
            tab = tab,
            deep = page != null || room != null,
            backLabel = if (page != null) room ?: tab.label else tab.label,
            onBack = closePage,
            requesters = tabRequesters,
            onTab = { if (page == null) tab = it },
            onTabsFocus = { tabsFocused = it },
        )
    }

    LaunchedEffect(handoff.pending, page, room, tab) {
        if (handoff.pending) {
            delay(60)
            runCatching { handoff.target.requestFocus() }
            handoff.pending = false
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun TopBar(
    tab: Tab,
    deep: Boolean,
    backLabel: String,
    onBack: () -> Unit,
    requesters: Map<Tab, FocusRequester>,
    onTab: (Tab) -> Unit,
    onTabsFocus: (Boolean) -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(64.dp)) {
        if (deep) {
            Focusable(onBack, Modifier.padding(start = 44.dp, top = 18.dp)) { f ->
                Row(
                    Modifier
                        .lift(f, 1.06f, CircleShape, rest = 0.dp)
                        .background(if (f) C.accent else Color.Transparent, CircleShape)
                        .height(28.dp)
                        .padding(start = 8.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Ico(Icons.ChevLeft, 15.dp, if (f) C.OnAccent else C.Stone)
                    Spacer(Modifier.width(5.dp))
                    T(backLabel, 13.5.sp, color = if (f) C.OnAccent else C.Stone, weight = FontWeight.Medium)
                }
            }
        } else {
            Row(Modifier.padding(start = 48.dp, top = 21.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(C.mark), null, Modifier.size(21.dp))
                Spacer(Modifier.width(7.dp))
                T("Open Shelf", 15.5.sp, weight = FontWeight.SemiBold, tight = true)
            }
        }
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 17.dp)
                .background(C.Bar, CircleShape)
                .border(1.dp, C.Hair, CircleShape)
                .padding(3.dp)
                .onFocusChanged { onTabsFocus(it.hasFocus) }
                .focusProperties { enter = { requesters.getValue(tab) } }
                .focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Tab.entries.forEach { t ->
                Focusable(
                    onClick = { onTab(t) },
                    modifier = Modifier.focusProperties { canFocus = !deep },
                    requester = requesters.getValue(t),
                    onFocus = { onTab(t) },
                ) { f ->
                    val selected = t == tab
                    Box(
                        Modifier
                            .lift(f, 1.06f, CircleShape, rest = 0.dp)
                            .background(if (f) C.accent else if (selected) C.Selected else Color.Transparent, CircleShape)
                            .height(28.dp)
                            .padding(horizontal = 15.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        T(t.label, 13.5.sp, color = if (f) C.OnAccent else if (selected) C.Ink else C.Stone, weight = FontWeight.Medium)
                    }
                }
            }
        }
        Clock(Modifier.align(Alignment.TopEnd).padding(end = 48.dp, top = 23.dp))
    }
}

@Composable
private fun Clock(modifier: Modifier) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(15_000) } }
    T(SimpleDateFormat("HH:mm", Locale.getDefault()).format(now), 13.5.sp, modifier, color = C.Stone, weight = FontWeight.Medium)
}

@Composable
private fun Discover(wallId: String, onWall: (String) -> Unit, openApp: (String) -> Unit, openRoom: (String) -> Unit, handoff: FocusHandoff) {
    val ctx = LocalContext.current
    val tick = Device.tick
    val first = remember { Catalog.visible.firstOrNull { it.art.banner != null && it.art.wall != null && it.runsHere } ?: Catalog.apps.first() }
    val wall = Catalog.byId(wallId) ?: first
    var browsing by remember { mutableStateOf(false) }
    val heroH by animateDpAsState(if (browsing) 150.dp else 268.dp, tween(320), label = "hero")
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 48.dp).height(heroH)) {
            AnimatedContent(wall, transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(200)) }, label = "wall") { a ->
                Box(Modifier.size(heroH * 16f / 9f, heroH).shadow(8.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp))) {
                    ArtImage(a.art.wall, a, 960)
                }
            }
            Spacer(Modifier.width(36.dp))
            Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
                AnimatedContent(wall, transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(160)) }, label = "label") { a ->
                    Column {
                        T(a.name, 32.sp, weight = FontWeight.SemiBold, tight = true, maxLines = 1)
                        T("by ${a.maker}", 13.5.sp, Modifier.padding(top = 4.dp), color = C.Stone)
                        AnimatedVisibility(!browsing, enter = expandVertically(tween(320)) + fadeIn(tween(320)), exit = shrinkVertically(tween(320)) + fadeOut(tween(160))) {
                            Column {
                                T(a.note, 15.5.sp, Modifier.padding(top = 11.dp).width(330.dp), color = C.Ink2, maxLines = 2)
                                Box(Modifier.padding(top = 15.dp).fillMaxWidth().height(1.dp).background(C.Hair))
                                Column(Modifier.padding(top = 11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Fact("Version", "${a.version}, ${Fmt.date(a.date)}")
                                    Fact("Licence", a.licence ?: "See repository")
                                    Fact("Stars", Fmt.count(a.stars))
                                }
                            }
                        }
                    }
                }
                Row(Modifier.padding(top = 13.dp).offset(x = (-4).dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Pill("View app", { openApp(wall.id) }, solid = true, requester = handoff.target)
                    InstallButton(wall, solid = false)
                }
            }
        }
        val tv = remember { Catalog.visible.filter { it.art.banner != null && it.runsHere }.take(40) }
        val recent = remember { Catalog.visible.sortedByDescending { it.date }.take(40) }
        val starred = remember { Catalog.visible.take(40) }
        val rooms = remember { roomsOf() }
        fun meta(a: App) = status(ctx, a)
        @Suppress("UNUSED_VARIABLE") val watch = tick
        val rowsState = rememberLazyListState()
        val spec = LocalBringIntoViewSpec.current
        CompositionLocalProvider(LocalBringIntoViewSpec provides Still) {
        LazyColumn(
            Modifier.padding(top = 12.dp).fillMaxWidth().weight(1f).onFocusChanged { browsing = it.hasFocus },
            state = rowsState,
        ) {
            item {
                ShelfRow(0, rowsState, spec, "Built for TV", "apps that ship TV artwork", "Recently updated") {
                    items(tv, key = { it.id }) { a -> Tile(a, meta(a).ifEmpty { "★ ${Fmt.stars(a.stars)}" }, meta(a).isNotEmpty(), { onWall(a.id) }) { openApp(a.id) } }
                }
            }
            item {
                ShelfRow(1, rowsState, spec, "Recently updated", "new releases on GitHub", "Most starred") {
                    items(recent, key = { it.id }) { a -> Tile(a, meta(a).ifEmpty { Fmt.shortDate(a.date) }, meta(a).isNotEmpty(), { onWall(a.id) }) { openApp(a.id) } }
                }
            }
            item {
                ShelfRow(2, rowsState, spec, "Most starred", null, "Categories") {
                    items(starred, key = { it.id }) { a -> Tile(a, meta(a).ifEmpty { "★ ${Fmt.stars(a.stars)}" }, meta(a).isNotEmpty(), { onWall(a.id) }) { openApp(a.id) } }
                }
            }
            item {
                ShelfRow(3, rowsState, spec, "Categories", "browse by what apps do", null) {
                    items(rooms, key = { it.first }) { (room, apps) -> RoomCard(room, apps.size) { openRoom(room) } }
                }
            }
        }
        }
    }
}

private val Still = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float) = 0f
}

@Composable
private fun Fact(label: String, value: String) {
    Row {
        T(label, 12.5.sp, Modifier.width(64.dp), color = C.Stone)
        T(value, 12.5.sp, weight = FontWeight.Medium, maxLines = 1)
    }
}

private fun toneOf(b: ImageBitmap): Color =
    Color(Bitmap.createScaledBitmap(b.asAndroidBitmap(), 1, 1, true).getPixel(0, 0)).copy(alpha = 1f)

private fun groundOf(b: ImageBitmap): Color? {
    val bm = b.asAndroidBitmap()
    val w = bm.width
    val h = bm.height
    if (w < 8 || h < 8) return null
    val x0 = (w * 0.04f).toInt(); val x1 = w - 1 - x0
    val y0 = (h * 0.06f).toInt(); val y1 = h - 1 - y0
    val samples = buildList {
        for (i in 0..10) {
            val x = x0 + (x1 - x0) * i / 10
            add(bm.getPixel(x, y0)); add(bm.getPixel(x, y1))
        }
        for (i in 1..5) {
            val y = y0 + (y1 - y0) * i / 6
            add(bm.getPixel(x0, y)); add(bm.getPixel(x1, y))
        }
    }.map { px -> if (android.graphics.Color.alpha(px) < 128) android.graphics.Color.WHITE else px }
    fun ch(px: Int, shift: Int) = (px shr shift) and 0xFF
    val mean = intArrayOf(16, 8, 0).map { sh -> samples.sumOf { ch(it, sh) } / samples.size }
    val close = samples.count { px -> intArrayOf(16, 8, 0).withIndex().all { (i, sh) -> kotlin.math.abs(ch(px, sh) - mean[i]) <= 22 } }
    if (close < samples.size * 0.85f) return null
    return Color(mean[0], mean[1], mean[2])
}

@Composable
private fun ArtImage(url: String?, app: App, maxWidth: Int, fill: Boolean = false) {
    var bmp by remember(url, maxWidth) { mutableStateOf(url?.let { Images.cached(it, maxWidth) }) }
    var failed by remember(url) { mutableStateOf(url == null) }
    LaunchedEffect(url, maxWidth) {
        if (bmp == null && url != null) {
            bmp = Images.load(url, maxWidth)
            failed = bmp == null
        }
    }
    val b = bmp
    val wide = b != null && (fill || b.width >= 900 || kotlin.math.abs(b.width.toFloat() / b.height - 16f / 9f) < 0.12f)
    val ground = remember(b, fill) { if (b != null && !fill && b.width < 900) groundOf(b) else null }
    val tone = remember(b, wide, ground) { if (b != null && !wide && ground == null) toneOf(b) else null }
    Box(Modifier.fillMaxSize().background(ground ?: tone ?: C.WallDeep), contentAlignment = Alignment.Center) {
        when {
            b != null && ground != null -> Image(b, null, Modifier.fillMaxSize(0.82f), contentScale = ContentScale.Fit, filterQuality = FilterQuality.High)
            b != null && wide -> Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.High)
            b != null -> Image(
                b, null,
                Modifier.fillMaxHeight(0.7f).aspectRatio(b.width.toFloat() / b.height, matchHeightConstraintsFirst = true)
                    .shadow(10.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
            )
            failed -> NameCard(app)
        }
    }
}

@Composable
private fun NameCard(app: App) {
    BoxWithConstraints(Modifier.fillMaxSize().background(C.Mat), contentAlignment = Alignment.Center) {
        val big = maxWidth > 300.dp
        val type = (maxWidth.value * 0.062f).coerceIn(13f, 30f).sp
        val icon = (maxWidth * 0.14f).coerceIn(30.dp, 68.dp)
        Column(Modifier.padding(horizontal = if (big) 36.dp else 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (app.art.icon != null) {
                AppIcon(app, icon)
                Spacer(Modifier.height(if (big) 16.dp else 8.dp))
            }
            T(app.name, type, weight = FontWeight.SemiBold, tight = true, maxLines = 2)
            if (big) T("by ${app.maker}", 13.5.sp, Modifier.padding(top = 6.dp), color = C.Stone, maxLines = 1)
        }
    }
}

@Composable
private fun AppIcon(app: App, size: Dp, modifier: Modifier = Modifier) {
    val url = app.art.icon
    var bmp by remember(url) { mutableStateOf(url?.let { Images.cached(it, 192) }) }
    LaunchedEffect(url) { if (bmp == null && url != null) bmp = Images.load(url, 192) }
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.22f)).background(C.WallDeep), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else T(app.name.take(1).uppercase(), (size.value * 0.45f).sp, color = C.Stone, weight = FontWeight.SemiBold)
    }
}

@Composable
private fun ShelfRow(
    index: Int,
    rows: androidx.compose.foundation.lazy.LazyListState,
    spec: BringIntoViewSpec,
    title: String,
    sub: String?,
    next: String?,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    Column(Modifier.height(168.dp).onFocusChanged { if (it.hasFocus) scope.launch { rows.animateScrollToItem(index) } }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            T(title, 15.5.sp, weight = FontWeight.SemiBold, tight = true)
            if (sub != null) T(sub, 12.5.sp, Modifier.padding(start = 7.dp), color = C.Stone)
            Spacer(Modifier.weight(1f))
            if (next != null) {
                T(next, 11.5.sp, color = C.Stone)
                Ico(Icons.ChevDown, 12.dp, C.Stone, Modifier.padding(start = 3.dp))
            }
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides spec) {
            LazyRow(
                Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 12.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun Tile(a: App, meta: String, dot: Boolean, onFocus: () -> Unit, requester: FocusRequester? = null, onClick: () -> Unit) {
    Focusable(onClick, requester = requester, onFocus = onFocus) { f ->
        Column(Modifier.width(148.dp)) {
            Box(Modifier.size(148.dp, 83.dp).lift(f, ring = 10.dp).clip(RoundedCornerShape(10.dp))) {
                ArtImage(a.art.tile, a, 320, fill = true)
            }
            T(a.name, 13.sp, Modifier.padding(top = 10.dp), color = if (f) C.Ink else C.Ink2, weight = FontWeight.SemiBold, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (dot) { Dot(5.5.dp); Spacer(Modifier.width(5.dp)) }
                T(meta, 11.5.sp, color = C.Stone, maxLines = 1)
            }
        }
    }
}

@Composable
private fun RoomCard(room: String, n: Int, onClick: () -> Unit) {
    Focusable(onClick) { f ->
        Column(
            Modifier.size(180.dp, 83.dp).lift(f, ring = 10.dp).clip(RoundedCornerShape(10.dp)).background(C.Mat)
                .padding(horizontal = 15.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            T(room, 20.sp, weight = FontWeight.SemiBold, tight = true, maxLines = 1)
            T(if (n == 1) "1 app" else "$n apps", 11.5.sp, color = C.Stone)
        }
    }
}

private fun status(ctx: Context, a: App): String {
    val inst = Device.installed(ctx, a.pkg)
    return when {
        inst != null && inst.versionCode < a.versionCode -> "Update ready"
        inst != null -> "Installed"
        else -> ""
    }
}

private fun roomsOf(): List<Pair<String, List<App>>> =
    Catalog.visible.groupBy { it.room }.toList()
        .sortedWith(compareBy<Pair<String, List<App>>> { it.first == "Other" }.thenByDescending { it.second.size })

@Composable
private fun Categories(last: String?, openRoom: (String) -> Unit, handoff: FocusHandoff) {
    val rooms = remember { roomsOf() }
    val target = last?.takeIf { l -> rooms.any { it.first == l } } ?: rooms.first().first
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 8.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            T("${rooms.size} categories, ${Fmt.count(Catalog.visible.size)} apps", 13.5.sp, color = C.Stone, weight = FontWeight.Medium)
        }
        items(rooms, key = { it.first }) { (room, apps) ->
            CategoryCard(room, apps, if (room == target) handoff.target else null) { openRoom(room) }
        }
    }
}

@Composable
private fun CategoryCard(room: String, apps: List<App>, requester: FocusRequester?, onClick: () -> Unit) {
    val faces = remember(room) { apps.filter { it.art.tile != null }.take(3) }
    Focusable(onClick, requester = requester) { f ->
        Column(
            Modifier.fillMaxWidth().height(116.dp)
                .lift(f, 1.05f, RoundedCornerShape(12.dp), ring = 12.dp)
                .clip(RoundedCornerShape(12.dp)).background(C.Mat)
                .padding(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                faces.forEach { a -> MiniArt(a) }
            }
            Spacer(Modifier.weight(1f))
            T(room, 19.sp, weight = FontWeight.SemiBold, tight = true, maxLines = 1)
            T(if (apps.size == 1) "1 app" else "${apps.size} apps", 11.5.sp, color = if (f) C.accent else C.Stone, weight = FontWeight.Medium)
        }
    }
}

@Composable
private fun MiniArt(a: App) {
    val url = a.art.tile
    var bmp by remember(url) { mutableStateOf(url?.let { Images.cached(it, 160) }) }
    LaunchedEffect(url) { if (bmp == null && url != null) bmp = Images.load(url, 160) }
    Box(Modifier.size(52.dp, 29.dp).clip(RoundedCornerShape(6.dp)).background(C.WallDeep)) {
        bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

@Composable
private fun RoomPage(
    room: String,
    grid: LazyGridState,
    recent: Boolean,
    onRecent: (Boolean) -> Unit,
    last: String?,
    onFocusApp: (String) -> Unit,
    openApp: (String) -> Unit,
    handoff: FocusHandoff,
) {
    val ctx = LocalContext.current
    val tick = Device.tick
    val apps = remember(room, recent) {
        Catalog.visible.filter { it.room == room }.let { list -> if (recent) list.sortedByDescending { it.date } else list }
    }
    val target = last?.takeIf { l -> apps.any { it.id == l } } ?: apps.firstOrNull()?.id
    @Suppress("UNUSED_VARIABLE") val watch = tick
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        state = grid,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 4.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
                Column {
                    T(room, 32.sp, weight = FontWeight.SemiBold, tight = true, maxLines = 1)
                    T(if (apps.size == 1) "1 app" else "${apps.size} apps", 13.5.sp, Modifier.padding(top = 2.dp), color = C.Stone)
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("Most starred", !recent) { onRecent(false) }
                    Chip("Recently updated", recent) { onRecent(true) }
                }
            }
        }
        items(apps, key = { it.id }) { a ->
            val s = status(ctx, a)
            Tile(
                a, s.ifEmpty { if (recent) Fmt.shortDate(a.date) else "★ ${Fmt.stars(a.stars)}" }, s.isNotEmpty(),
                onFocus = { onFocusApp(a.id) },
                requester = if (a.id == target) handoff.target else null,
            ) { openApp(a.id) }
        }
    }
}

@Composable
private fun AppPage(app: App, handoff: FocusHandoff) {
    val ctx = LocalContext.current
    val tick = Device.tick
    val installed = remember(app.pkg, tick) { Device.installed(ctx, app.pkg) }
    val gallery = remember(app.id) { app.art.gallery }
    var shot by remember(app.id) { mutableStateOf(gallery.firstOrNull()) }
    val apk = pickApk(app)
    Row(Modifier.fillMaxSize().padding(horizontal = 48.dp)) {
        Column(Modifier.width(476.dp)) {
            AnimatedContent(shot, transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) }, label = "shot") { s ->
                Box(Modifier.size(476.dp, 268.dp).shadow(8.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp))) {
                    ArtImage(s, app, 960)
                }
            }
            if (gallery.size > 1) {
                Row(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    gallery.forEach { s ->
                        Focusable({ shot = s }, onFocus = { shot = s }) { f ->
                            Box(Modifier.size(108.dp, 61.dp).lift(f, ring = 8.dp, shape = RoundedCornerShape(8.dp)).clip(RoundedCornerShape(8.dp))) {
                                ArtImage(s, app, 240, fill = true)
                            }
                        }
                    }
                }
            }
            if (app.releases.isNotEmpty()) {
                T("Releases", 12.5.sp, Modifier.padding(top = if (gallery.size > 1) 24.dp else 28.dp), color = C.Stone, weight = FontWeight.Medium)
                Row(Modifier.padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    app.releases.take(4).forEachIndexed { i, r ->
                        Column(
                            Modifier.weight(1f).background(C.Mat, RoundedCornerShape(8.dp))
                                .border(if (i == 0) 1.5.dp else 1.dp, if (i == 0) C.accent else C.Hair, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            T(r.tag, 12.5.sp, weight = FontWeight.SemiBold, maxLines = 1)
                            T(if (i == 0) "Latest, ${Fmt.monthYear(r.date)}" else Fmt.monthYear(r.date), 10.5.sp, color = C.Stone, maxLines = 1)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(36.dp))
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app, 52.dp, Modifier.shadow(3.dp, RoundedCornerShape(11.dp)))
                Spacer(Modifier.width(13.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T(app.name, 24.sp, weight = FontWeight.SemiBold, tight = true, maxLines = 1)
                        if (installed != null) { Spacer(Modifier.width(8.dp)); Dot() }
                    }
                    T("by ${app.maker}", 12.5.sp, color = C.Stone)
                }
            }
            T(app.note, 15.5.sp, Modifier.padding(top = 12.dp), color = C.Ink2, maxLines = 2)
            if (app.archived) T("Archived by its developer. Don't expect more updates.", 11.5.sp, Modifier.padding(top = 4.dp), color = C.Stone)
            Row(Modifier.padding(top = 10.dp).offset(x = (-4).dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                InstallButton(app, requester = handoff.target)
                if (installed != null && !Installer.busy(app.id)) Pill("Uninstall", { uninstall(ctx, app.pkg) }, icon = Icons.Trash)
            }
            JobNote(app, Modifier.padding(top = 6.dp))
            StorageLine(app, installed)
            Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(1.dp).background(C.Hair))
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Row {
                    Prov(Icons.Calendar, "Released", "${app.version}, ${Fmt.date(app.date)}", Modifier.weight(1f))
                    Prov(Icons.Star, "Stars", Fmt.count(app.stars), Modifier.weight(1f))
                }
                Row {
                    Prov(Icons.Scale, "Licence", app.licence ?: "See repository", Modifier.weight(1f))
                    Prov(Icons.Box, "Download", Fmt.mb(apk.size), Modifier.weight(1f))
                }
                val cert = app.certSha256
                val mismatch = installed != null && cert != null && installed.certs.isNotEmpty() && cert !in installed.certs
                val sig = when {
                    cert == null -> "Not recorded yet. Android still checks updates against your installed copy."
                    installed == null -> "Checked against the developer’s key before install"
                    mismatch -> "Different from the developer’s key. Updates are held."
                    else -> "Matches the developer’s key"
                }
                Prov(if (mismatch) Icons.ShieldAlert else Icons.Shield, "Signing key", sig)
                val abi = if (apk.abi in listOf("any", "universal")) "every TV" else "this TV (${abiLabel(apk.abi)})"
                Prov(Icons.Chip, "File picked for $abi", apk.name)
            }
        }
    }
}

@Composable
private fun Prov(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Top) {
        Ico(icon, 14.dp, C.Stone, Modifier.padding(top = 1.dp))
        Spacer(Modifier.width(7.dp))
        Column {
            T(label, 10.5.sp, color = C.Stone, maxLines = 1)
            T(value, 12.sp, weight = FontWeight.Medium, maxLines = 1)
        }
    }
}

@Composable
private fun StorageLine(app: App, installed: Installed?) {
    val ctx = LocalContext.current
    val free = remember { Device.freeBytes(ctx) }
    val total = remember { Device.totalBytes(ctx) }
    val need = pickApk(app).size
    Column(Modifier.padding(top = 8.dp)) {
        if (installed != null) {
            T("Installed version ${installed.versionName ?: "?"}", 11.5.sp, color = C.Stone, maxLines = 1)
        } else {
            T("Needs ${Fmt.mb(need)}. ${Fmt.gb(free)} free on this TV.", 11.5.sp, color = C.Stone, maxLines = 1)
            Row(Modifier.padding(top = 5.dp).fillMaxWidth().height(4.dp).clip(CircleShape).background(C.WallDeep)) {
                val used = if (total > 0) (total - free).toFloat() / total else 0f
                Box(Modifier.fillMaxHeight().weight(used.coerceIn(0.01f, 0.98f)).background(C.Stone2))
                Box(Modifier.fillMaxHeight().width(3.dp).background(C.accent))
                Box(Modifier.fillMaxHeight().weight((1f - used).coerceIn(0.01f, 0.98f)))
            }
        }
    }
}

@Composable
private fun SearchScreen(query: String, onQuery: (String) -> Unit, openApp: (String) -> Unit, handoff: FocusHandoff) {
    var editing by remember { mutableStateOf(false) }
    val field = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(editing) {
        if (editing) {
            delay(40)
            runCatching { field.requestFocus() }
            keyboard?.show()
        }
    }
    val finish = {
        keyboard?.hide()
        editing = false
        runCatching { handoff.target.requestFocus() }
    }
    val side = Modifier.padding(horizontal = 48.dp)
    Column(Modifier.fillMaxSize()) {
        Focusable(onClick = { editing = true }, modifier = side, requester = handoff.target) { f ->
            val active = f || editing
            Row(
                Modifier.padding(top = 4.dp).fillMaxWidth().height(58.dp)
                    .lift(active, 1.015f, RoundedCornerShape(16.dp), ring = 16.dp)
                    .background(C.Mat, RoundedCornerShape(16.dp)).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ico(Icons.Search, 22.dp, if (active) C.accent else C.Stone)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    BasicTextField(
                        value = query,
                        onValueChange = onQuery,
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = Geist, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = C.Ink),
                        cursorBrush = SolidColor(C.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { finish() }, onDone = { finish() }),
                        modifier = Modifier.fillMaxWidth()
                            .focusRequester(field)
                            .focusProperties { canFocus = editing }
                            .onFocusChanged { if (!it.isFocused && editing) editing = false },
                    )
                    if (query.isEmpty()) {
                        T(if (editing) "Type an app name or what it does" else "Search apps", 22.sp, color = C.Stone2, tight = true, maxLines = 1)
                    }
                }
                if (f && !editing) {
                    Box(Modifier.background(C.accent, CircleShape).padding(horizontal = 11.dp, vertical = 4.dp)) {
                        T(if (query.isEmpty()) "Press OK to type" else "OK to edit", 11.5.sp, color = C.OnAccent, weight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Row(side.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            T("Try", 12.5.sp, Modifier.padding(end = 4.dp), color = C.Stone)
            val chips = listOf("video player", "game streaming", "launcher", "web browser", "music", "screensaver")
            chips.forEach { s -> Chip(s, s == query.trim()) { onQuery(s) } }
            if (query.isNotEmpty()) Chip("Clear", false) { onQuery("") }
        }
        val q = query.trim().lowercase()
        val results = remember(q, Catalog.apps) { search(q) }
        val shown = if (q.isEmpty()) Catalog.visible.take(20).map { it to true } else results
        T(
            when {
                q.isEmpty() -> "Most starred"
                results.isEmpty() -> "No apps match “${query.trim()}”"
                else -> "${results.size} ${if (results.size == 1) "app" else "apps"} for “${query.trim()}”"
            },
            13.5.sp, side.padding(top = 20.dp), color = C.Stone, weight = FontWeight.Medium,
        )
        if (q.isNotEmpty() && results.isEmpty()) {
            T("Try a shorter word, or what the app does, like “video player”.", 15.5.sp, side.padding(top = 10.dp), color = C.Stone)
        }
        LazyRow(
            Modifier.padding(top = 2.dp),
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            items(shown.take(40), key = { it.first.id }) { (a, byName) -> ResultCard(a, q, byName) { openApp(a.id) } }
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Focusable(onClick) { f ->
        Box(
            Modifier.lift(f, 1.08f, CircleShape, rest = 0.dp).height(28.dp)
                .background(if (f) C.accent else if (selected) C.Ink else C.Wall, CircleShape)
                .border(1.dp, if (f || selected) Color.Transparent else C.Hair2, CircleShape).padding(horizontal = 13.dp),
            contentAlignment = Alignment.Center,
        ) { T(text, 12.sp, color = if (f) C.OnAccent else if (selected) C.OnInk else C.Ink2, weight = FontWeight.Medium) }
    }
}

private fun search(q: String): List<Pair<App, Boolean>> {
    if (q.isEmpty()) return emptyList()
    val words = q.split(Regex("\\s+")).filter { it.isNotEmpty() }
    return Catalog.apps.mapNotNull { a ->
        val hay = "${a.name} ${a.note} ${a.desc} ${a.room} ${a.repo}".lowercase()
        val byName = a.name.lowercase().contains(q)
        val all = words.all { w -> hay.contains(w) || (w.endsWith("s") && hay.contains(w.dropLast(1))) }
        when {
            byName && (a.stars >= Catalog.MIN_STARS || a.name.lowercase() == q) -> a to true
            all && a.stars >= Catalog.MIN_STARS -> a to false
            else -> null
        }
    }.sortedWith(compareByDescending<Pair<App, Boolean>> { it.second }.thenByDescending { it.first.stars })
}

@Composable
private fun ResultCard(a: App, q: String, byName: Boolean, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val tick = Device.tick
    val installed = remember(a.pkg, tick) { Device.installed(ctx, a.pkg) != null }
    Focusable(onClick) { f ->
        Column(Modifier.width(224.dp)) {
            Box(Modifier.size(224.dp, 126.dp).lift(f, ring = 12.dp, shape = RoundedCornerShape(12.dp)).clip(RoundedCornerShape(12.dp))) {
                ArtImage(a.art.wall ?: a.art.tile, a, 480)
            }
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                T(a.name, 15.5.sp, weight = FontWeight.SemiBold, tight = true, maxLines = 1)
                if (installed) { Spacer(Modifier.width(6.dp)); Dot() }
            }
            val field = listOf(a.note, a.desc).firstOrNull { q.isNotEmpty() && it.lowercase().contains(q) }
            val why = if (byName || field == null) buildAnnotatedString { append(a.note) } else buildAnnotatedString {
                val i = field.lowercase().indexOf(q)
                append("Matches: ")
                append(field.substring(0, i))
                withStyle(SpanStyle(background = C.accent.copy(alpha = 0.18f), color = C.Ink)) { append(field.substring(i, i + q.length)) }
                append(field.substring(i + q.length))
            }
            T(why, 11.5.sp, Modifier.padding(top = 3.dp), color = C.Stone, maxLines = 2)
        }
    }
}

private enum class State { Busy, Held, Update, Ok }

private data class LocalApp(val pkg: String, val label: String, val version: String?, val icon: ImageBitmap?)

private fun otherApps(ctx: Context, exclude: Set<String>): List<LocalApp> {
    val pm = ctx.packageManager
    return pm.getInstalledApplications(0)
        .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 && it.packageName != ctx.packageName && it.packageName !in exclude }
        .filter { pm.getLeanbackLaunchIntentForPackage(it.packageName) != null || pm.getLaunchIntentForPackage(it.packageName) != null }
        .map { ai ->
            val icon = runCatching {
                val d = pm.getApplicationIcon(ai)
                val bm = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                d.setBounds(0, 0, 96, 96)
                d.draw(android.graphics.Canvas(bm))
                bm.asImageBitmap()
            }.getOrNull()
            LocalApp(ai.packageName, pm.getApplicationLabel(ai).toString(), runCatching { pm.getPackageInfo(ai.packageName, 0).versionName }.getOrNull(), icon)
        }
        .sortedBy { it.label.lowercase() }
}

@Composable
private fun MyApps(openApp: (String) -> Unit, goDiscover: () -> Unit, handoff: FocusHandoff) {
    val ctx = LocalContext.current
    val tick = Device.tick
    val mine = remember(tick) { Catalog.apps.mapNotNull { a -> Device.installed(ctx, a.pkg)?.let { a to it } } }
    var others by remember { mutableStateOf<List<LocalApp>?>(null) }
    LaunchedEffect(tick) { others = withContext(Dispatchers.IO) { otherApps(ctx, mine.map { it.first.pkg }.toSet()) } }
    fun state(a: App, i: Installed): State = when {
        Installer.busy(a.id) -> State.Busy
        Installer.jobs[a.id] is Job.Held || (a.certSha256 != null && i.certs.isNotEmpty() && a.certSha256 !in i.certs) -> State.Held
        i.versionCode < a.versionCode -> State.Update
        else -> State.Ok
    }
    val updatable = mine.filter { (a, i) -> state(a, i) == State.Update }
    val held = mine.count { (a, i) -> state(a, i) == State.Held }
    val otherList = others.orEmpty()
    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                T("My apps", 32.sp, weight = FontWeight.SemiBold, tight = true)
                val parts = buildList {
                    if (updatable.isNotEmpty()) add("${updatable.size} ${if (updatable.size == 1) "update" else "updates"} ready")
                    if (held > 0) add("$held held for review")
                    add("${mine.size} from Open Shelf")
                    if (others != null) add("${otherList.size} other ${if (otherList.size == 1) "app" else "apps"} on this TV")
                }
                T(parts.joinToString(" · "), 13.5.sp, Modifier.padding(top = 4.dp), color = C.Stone)
            }
            if (updatable.isNotEmpty()) {
                Pill("Update all", { updatable.forEach { (a, _) -> Installer.start(ctx, a) } }, solid = true, icon = Icons.Download, requester = handoff.target)
            }
        }
        LazyColumn(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(top = 6.dp, bottom = 24.dp)) {
            item { Section("From Open Shelf") }
            if (mine.isEmpty()) {
                item {
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        T("Apps you install from Open Shelf appear here, with updates.", 13.5.sp, Modifier.padding(end = 14.dp), color = C.Ink2)
                        Pill("Find an app", goDiscover, requester = if (updatable.isEmpty()) handoff.target else null)
                    }
                }
            }
            items(mine, key = { it.first.id }) { (a, i) ->
                val st = state(a, i)
                val first = updatable.isEmpty() && a == mine.first().first
                AppRow(
                    focusRequester = if (first) handoff.target else null,
                    onClick = { if (st == State.Update) Installer.start(ctx, a) else openApp(a.id) },
                    icon = { AppIcon(a, 38.dp) },
                    title = a.name,
                    sub = when (st) {
                        State.Update -> "${i.versionName} → ${a.version} · ${Fmt.mb(pickApk(a).size)}"
                        State.Held -> "${i.versionName} · signed with a different key"
                        else -> i.versionName ?: ""
                    },
                ) { f ->
                    when (st) {
                        State.Busy -> {
                            val job = Installer.jobs[a.id]
                            val frac = (job as? Job.Downloading)?.fraction ?: 0.9f
                            Ring(frac, C.accent, C.Hair2)
                            T(if (job is Job.Downloading) "  Downloading ${(frac * 100).toInt()}%" else "  Installing", 12.5.sp, color = C.Ink2, weight = FontWeight.Medium)
                        }
                        State.Update -> { Ico(Icons.Up, 17.dp, C.accent); T("  Update ready", 12.5.sp, color = C.Ink2, weight = FontWeight.Medium); MiniPill("Update", f) }
                        State.Held -> { Ico(Icons.ShieldAlert, 17.dp, C.Ink); T("  Update held", 12.5.sp, weight = FontWeight.SemiBold); MiniPill("Review", f) }
                        State.Ok -> { Ico(Icons.Check, 17.dp, C.Stone); T("  Up to date", 12.5.sp, color = C.Stone); MiniPill("Details", f) }
                    }
                }
            }
            if (otherList.isNotEmpty()) {
                item { Section("Other apps on this TV") }
                items(otherList, key = { it.pkg }) { o ->
                    AppRow(
                        focusRequester = if (mine.isEmpty() && o == otherList.first() && updatable.isEmpty()) null else null,
                        onClick = { launch(ctx, o.pkg) },
                        icon = {
                            Box(Modifier.size(38.dp).clip(RoundedCornerShape(8.dp)).background(C.WallDeep), contentAlignment = Alignment.Center) {
                                if (o.icon != null) Image(o.icon, null, Modifier.fillMaxSize()) else T(o.label.take(1), 16.sp, color = C.Stone)
                            }
                        },
                        title = o.label,
                        sub = listOfNotNull(o.version, "not in the Open Shelf list").joinToString(" · "),
                    ) { f -> MiniPill("Open", f) }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    T(title, 12.5.sp, Modifier.padding(start = 14.dp, top = 10.dp, bottom = 2.dp), color = C.Stone, weight = FontWeight.SemiBold)
}

@Composable
private fun AppRow(
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    title: String,
    sub: String,
    trailing: @Composable (Boolean) -> Unit,
) {
    Focusable(onClick = onClick, requester = focusRequester) { f ->
        Row(
            Modifier.fillMaxWidth().height(56.dp).lift(f, 1.015f, RoundedCornerShape(12.dp), rest = 0.dp, ring = 12.dp)
                .background(if (f) C.Mat else Color.Transparent, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                T(title, 15.5.sp, weight = FontWeight.SemiBold, tight = true, maxLines = 1)
                T(sub, 11.5.sp, color = C.Stone, maxLines = 1)
            }
            trailing(f)
        }
    }
}

@Composable
private fun MiniPill(text: String, focused: Boolean) {
    Spacer(Modifier.width(14.dp))
    Box(
        Modifier.height(28.dp).background(if (focused) C.accent else Color.Transparent, CircleShape)
            .border(1.dp, if (focused) Color.Transparent else C.Hair2, CircleShape).padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center,
    ) { T(text, 12.5.sp, color = if (focused) C.OnAccent else C.Ink, weight = FontWeight.SemiBold) }
}

private class Setting(val title: String, val value: String, val help: String, val action: () -> Unit = {})

@Composable
private fun SettingsScreen(handoff: FocusHandoff) {
    val ctx = LocalContext.current
    val tick = Device.tick
    var cacheTick by remember { mutableIntStateOf(0) }
    val canInstall = remember(tick) { Installer.canInstall(ctx) }
    val cache = remember(cacheTick, tick) { Device.cacheBytes(ctx) }
    val version = remember { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }
    val rows = listOf(
        Setting("Accent colour", Prefs.accents[Prefs.accent].first,
            "Colours the focus ring, the focused button and the dot that marks installed apps. Press OK to try the next one.") { Prefs.nextAccent() },
        Setting("Appearance", Prefs.themes[Prefs.theme],
            "Light hangs the shelf on a white wall, Dark on a charcoal one for evenings. Match TV follows the TV’s own dark setting. Press OK to switch.") { Prefs.nextTheme() },
        Setting("Install apps from Open Shelf", if (canInstall) "Allowed" else "Not allowed yet",
            "Android asks you to allow this once. After that, Open Shelf can install and update apps, and each install still asks you to confirm.") {
            Installer.openInstallPermission(ctx)
        },
        Setting("APK type", "Automatic · ${abiLabel(Build.SUPPORTED_ABIS.first())}",
            "Many apps publish a different file for each kind of chip. Open Shelf picks the one that fits this TV. Supported here: ${Build.SUPPORTED_ABIS.joinToString(", ")}."),
        Setting("If an app’s signing key changes", "Hold the update",
            "Every release is signed by its developer. If a new file is signed by someone else, Open Shelf keeps your current version and doesn’t install it."),
        Setting("Catalog", "${Fmt.count(Catalog.apps.size)} apps · ${Fmt.shortDate(Catalog.generated)}",
            "Every Android TV app on GitHub that publishes an APK and has had a release in the last 3 years. Checked on ${Fmt.date(Catalog.generated)}. This version carries the list inside the app."),
        Setting("Hide apps with fewer stars than", "${Catalog.MIN_STARS}",
            "Keeps very new or little-used apps out of Discover. ${Fmt.count(Catalog.apps.size - Catalog.visible.size)} apps are hidden. Search still finds them by their exact name."),
        Setting("Download cache", "${Fmt.mb(cache)} · Clear",
            "Downloaded APK files are kept here so you can reinstall without downloading again. Press OK to clear them.") {
            Device.clearCache(ctx); cacheTick++
        },
        Setting("About", "Open Shelf $version",
            "Open Shelf is open source under GPL-3.0, at github.com/wingedpanther/openshelf. App names, art and releases come from each project’s GitHub repository."),
    )
    var focusedIndex by remember { mutableIntStateOf(0) }
    Row(Modifier.fillMaxSize().padding(horizontal = 48.dp)) {
        Column(Modifier.width(320.dp)) {
            val s = rows[focusedIndex]
            Column(
                Modifier.size(320.dp, 340.dp).shadow(6.dp, RoundedCornerShape(16.dp)).background(C.Mat, RoundedCornerShape(16.dp)).padding(28.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Image(painterResource(C.mark), null, Modifier.size(44.dp))
                Column {
                    T(s.title, 22.sp, weight = FontWeight.SemiBold, tight = true)
                    T(s.help, 14.5.sp, Modifier.padding(top = 10.dp), color = C.Ink2)
                }
            }
            T("Version $version · ${Fmt.count(Catalog.apps.size)} apps", 12.5.sp, Modifier.padding(top = 14.dp), color = C.Stone)
        }
        Spacer(Modifier.width(52.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            rows.forEachIndexed { i, s ->
                Focusable(s.action, requester = if (i == 0) handoff.target else null, onFocus = { focusedIndex = i }) { f ->
                    Row(
                        Modifier.fillMaxWidth().height(44.dp).lift(f, 1.02f, RoundedCornerShape(12.dp), rest = 0.dp, ring = 12.dp)
                            .background(if (f) C.Mat else Color.Transparent, RoundedCornerShape(12.dp)).padding(horizontal = 15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        T(s.title, 14.5.sp, Modifier.weight(1f), weight = FontWeight.Medium, maxLines = 1)
                        if (i == 0) { Dot(9.dp); Spacer(Modifier.width(7.dp)) }
                        T(s.value, 13.5.sp, color = if (f) C.Ink2 else C.Stone, maxLines = 1)
                        Ico(Icons.ChevRight, 13.dp, if (f) C.accent else C.Stone, Modifier.padding(start = 6.dp))
                    }
                }
                if (i == 4 || i == 6) Box(Modifier.padding(horizontal = 15.dp, vertical = 4.dp).fillMaxWidth().height(1.dp).background(C.Hair))
            }
        }
    }
}
