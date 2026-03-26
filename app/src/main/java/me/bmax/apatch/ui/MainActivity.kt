package me.bmax.apatch.ui

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.Coil
import coil.ImageLoader
import com.ramcosta.composedestinations.DestinationsNavHost
import com.ramcosta.composedestinations.animations.NavHostAnimatedDestinationStyle
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.rememberNavHostEngine
import com.ramcosta.composedestinations.utils.isRouteOnBackStackAsState
import com.ramcosta.composedestinations.utils.rememberDestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.bmax.apatch.APApplication
import me.bmax.apatch.R
import me.bmax.apatch.ui.screen.BottomBarDestination
import me.bmax.apatch.ui.theme.APatchTheme
import me.bmax.apatch.ui.viewmodel.SuperUserViewModel
import me.bmax.apatch.util.rootShellForResult
import me.bmax.apatch.util.ui.LocalSnackbarHost
import me.zhanghai.android.appiconloader.coil.AppIconFetcher
import me.zhanghai.android.appiconloader.coil.AppIconKeyer

class MainActivity : AppCompatActivity() {

    private var isLoading = true

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {

        installSplashScreen().setKeepOnScreenCondition { isLoading }

        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        super.onCreate(savedInstanceState)

        setContent {
            APatchTheme {
                val navController = rememberNavController()
                val snackBarHostState = remember { SnackbarHostState() }
                val configuration = LocalConfiguration.current
                val scope = rememberCoroutineScope()

                var showSeLinuxDialog by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    val enforce = runCatching {
                        java.io.File("/sys/fs/selinux/enforce").readText().trim()
                    }.getOrDefault("1")
                    if (enforce != "1") showSeLinuxDialog = true
                }
                if (showSeLinuxDialog) {
                    AlertDialog(
                        onDismissRequest = { showSeLinuxDialog = false },
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        title = { Text(stringResource(R.string.selinux_warning_title)) },
                        text = { Text(stringResource(R.string.selinux_warning_body)) },
                        confirmButton = {
                            TextButton(onClick = {
                                showSeLinuxDialog = false
                                scope.launch(Dispatchers.IO) {
                                    rootShellForResult("setenforce 1")
                                }
                            }) { Text(stringResource(R.string.selinux_enforce)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showSeLinuxDialog = false }) {
                                Text(stringResource(android.R.string.cancel))
                            }
                        }
                    )
                }

                val bottomBarRoutes = remember {
                    BottomBarDestination.entries.map { it.direction.route }.toSet()
                }
                val state by APApplication.apStateLiveData.observeAsState(APApplication.State.UNKNOWN_STATE)
                val kPatchReady = state != APApplication.State.UNKNOWN_STATE
                val aPatchReady = state == APApplication.State.ANDROIDPATCH_INSTALLED
                val visibleDestinations = remember(state) {
                    BottomBarDestination.entries.filter { destination ->
                        !(destination.kPatchRequired && !kPatchReady) &&
                            !(destination.aPatchRequired && !aPatchReady)
                    }.toSet()
                }

                val defaultTransitions = object : NavHostAnimatedDestinationStyle() {
                    override val enterTransition:
                        AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
                        val targetRoute = targetState.destination.route
                        val initialRoute = initialState.destination.route
                        val targetIndex =
                            BottomBarDestination.entries.indexOfFirst { it.direction.route == targetRoute }
                        val initialIndex =
                            BottomBarDestination.entries.indexOfFirst { it.direction.route == initialRoute }

                        when {
                            targetIndex != -1 && initialIndex != -1 -> {
                                val offsetSign = if (targetIndex > initialIndex) 1 else -1
                                slideInHorizontally(
                                    initialOffsetX = { it * offsetSign },
                                    animationSpec = tween(300)
                                )
                            }

                            targetRoute in bottomBarRoutes && initialRoute !in bottomBarRoutes -> {
                                slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300))
                            }

                            else -> {
                                slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300))
                            }
                        }
                    }

                    override val exitTransition:
                        AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
                        val targetRoute = targetState.destination.route
                        val initialRoute = initialState.destination.route
                        val targetIndex =
                            BottomBarDestination.entries.indexOfFirst { it.direction.route == targetRoute }
                        val initialIndex =
                            BottomBarDestination.entries.indexOfFirst { it.direction.route == initialRoute }

                        when {
                            targetIndex != -1 && initialIndex != -1 -> {
                                val offsetSign = if (targetIndex > initialIndex) -1 else 1
                                slideOutHorizontally(
                                    targetOffsetX = { it * offsetSign },
                                    animationSpec = tween(300)
                                )
                            }

                            initialRoute in bottomBarRoutes && targetRoute !in bottomBarRoutes -> {
                                slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300))
                            }

                            else -> {
                                slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300))
                            }
                        }
                    }

                    override val popEnterTransition:
                        AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
                        val targetRoute = targetState.destination.route
                        val initialRoute = initialState.destination.route
                        val targetIndex =
                            BottomBarDestination.entries.indexOfFirst { it.direction.route == targetRoute }
                        val initialIndex =
                            BottomBarDestination.entries.indexOfFirst { it.direction.route == initialRoute }

                        when {
                            targetIndex != -1 && initialIndex != -1 -> {
                                val offsetSign = if (targetIndex > initialIndex) 1 else -1
                                slideInHorizontally(
                                    initialOffsetX = { it * offsetSign },
                                    animationSpec = tween(300)
                                )
                            }

                            targetRoute in bottomBarRoutes -> {
                                slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300))
                            }

                            else -> {
                                slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300))
                            }
                        }
                    }

                    override val popExitTransition:
                        AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
                        val targetRoute = targetState.destination.route
                        val initialRoute = initialState.destination.route
                        val targetIndex =
                            BottomBarDestination.entries.indexOfFirst { it.direction.route == targetRoute }
                        val initialIndex =
                            BottomBarDestination.entries.indexOfFirst { it.direction.route == initialRoute }

                        when {
                            targetIndex != -1 && initialIndex != -1 -> {
                                val offsetSign = if (targetIndex > initialIndex) -1 else 1
                                slideOutHorizontally(
                                    targetOffsetX = { it * offsetSign },
                                    animationSpec = tween(300)
                                )
                            }

                            initialRoute !in bottomBarRoutes -> {
                                slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300))
                            }

                            else -> {
                                slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300))
                            }
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    if (SuperUserViewModel.apps.isEmpty()) {
                        SuperUserViewModel().fetchAppList()
                    }
                }

                Scaffold(
                    bottomBar = {
                        if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                            BottomBar(navController, visibleDestinations)
                        }
                    },
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { innerPadding ->
                    CompositionLocalProvider(
                        LocalSnackbarHost provides snackBarHostState,
                    ) {
                        if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .windowInsetsPadding(
                                        WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)
                                    )
                            ) {
                                SideBar(
                                    navController = navController,
                                    modifier = Modifier.windowInsetsPadding(
                                        WindowInsets.systemBars.only(WindowInsetsSides.Top)
                                    ),
                                    visibleDestinations = visibleDestinations
                                )
                                DestinationsNavHost(
                                    modifier = Modifier
                                        .weight(1f)
                                        .consumeWindowInsets(
                                            WindowInsets.safeDrawing.only(WindowInsetsSides.Start)
                                        ),
                                    navGraph = NavGraphs.root,
                                    navController = navController,
                                    engine = rememberNavHostEngine(
                                        navHostContentAlignment = Alignment.TopCenter
                                    ),
                                    defaultTransitions = defaultTransitions
                                )
                            }
                        } else {
                            DestinationsNavHost(
                                modifier = Modifier
                                    .padding(innerPadding)
                                    .consumeWindowInsets(innerPadding),
                                navGraph = NavGraphs.root,
                                navController = navController,
                                engine = rememberNavHostEngine(navHostContentAlignment = Alignment.TopCenter),
                                defaultTransitions = defaultTransitions
                            )
                        }
                    }
                }
            }
        }

        val iconSize = resources.getDimensionPixelSize(android.R.dimen.app_icon_size)
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .components {
                    add(AppIconKeyer())
                    add(AppIconFetcher.Factory(iconSize, false, this@MainActivity))
                }
                .build()
        )

        isLoading = false
    }
}

@Composable
private fun BottomBar(navController: NavHostController, visibleDestinations: Set<BottomBarDestination>) {
    val navigator = navController.rememberDestinationsNavigator()
    val orderedDestinations = remember(visibleDestinations) { visibleDestinations.toList() }
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    var lastSelectedIndex by remember { mutableStateOf(0) }
    val isOnBackStack = orderedDestinations.map { destination ->
        navController.isRouteOnBackStackAsState(destination.direction).value
    }

    val selectedIndex = run {
        val exactMatch = orderedDestinations.indexOfFirst { it.direction.route == currentRoute }
        if (exactMatch != -1) exactMatch else isOnBackStack.indexOfLast { it }
    }

    if (selectedIndex != -1) {
        lastSelectedIndex = selectedIndex
    }

    val safeIndex = lastSelectedIndex.coerceIn(0, (orderedDestinations.size - 1).coerceAtLeast(0))
    val effectiveSelectedIndex = if (selectedIndex != -1) selectedIndex else safeIndex
    val animatedSelectedIndex by animateFloatAsState(
        targetValue = effectiveSelectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "BottomBarSelectedIndex"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                bottom = WindowInsets.navigationBars
                    .asPaddingValues()
                    .calculateBottomPadding()
            )
    ) {
        val horizontalScreenPadding = when {
            maxWidth > 600.dp -> 32.dp
            maxWidth > 400.dp -> 24.dp
            else -> 16.dp
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalScreenPadding, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.wrapContentWidth(),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 3.dp,
                shadowElevation = 8.dp
            ) {
                val itemSize = 56.dp
                val itemSpacing = 4.dp
                val containerPadding = 7.dp
                val navBarWidth = (itemSize * orderedDestinations.size) +
                    (itemSpacing * (orderedDestinations.size - 1).coerceAtLeast(0)) +
                    (containerPadding * 2)

                Box(
                    modifier = Modifier
                        .width(navBarWidth)
                        .height(72.dp)
                ) {
                    var totalWidth by remember { mutableStateOf(0) }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = containerPadding)
                            .onSizeChanged { size ->
                                totalWidth = size.width
                            }
                    ) {
                        if (totalWidth > 0 && orderedDestinations.isNotEmpty()) {
                            val density = LocalDensity.current
                            val itemSizePx = with(density) { itemSize.toPx() }
                            val itemSpacingPx = with(density) { itemSpacing.toPx() }
                            val indicatorOffset = (itemSizePx + itemSpacingPx) * animatedSelectedIndex

                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(vertical = 8.dp)
                                    .offset {
                                        IntOffset(
                                            x = indicatorOffset.toInt(),
                                            y = 0
                                        )
                                    }
                                    .width(itemSize),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(itemSize)
                                        .background(
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            shape = MaterialTheme.shapes.large
                                        )
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(itemSpacing),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            orderedDestinations.forEachIndexed { index, destination ->
                                val isSelected = index == effectiveSelectedIndex
                                Box(
                                    modifier = Modifier
                                        .size(itemSize)
                                        .clip(MaterialTheme.shapes.large)
                                        .clickable {
                                            val isCurrentDestination = destination.direction.route == currentRoute
                                            if (isCurrentDestination) {
                                                navigator.popBackStack(destination.direction, false)
                                            }
                                            navigator.navigate(destination.direction) {
                                                popUpTo(NavGraphs.root) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (isSelected) destination.iconSelected else destination.iconNotSelected,
                                        stringResource(destination.label),
                                        tint = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SideBar(
    navController: NavHostController,
    modifier: Modifier = Modifier,
    visibleDestinations: Set<BottomBarDestination>
) {
    val navigator = navController.rememberDestinationsNavigator()

    Crossfade(
        targetState = visibleDestinations,
        label = "SideBarStateCrossfade"
    ) { visibleDestinationSet ->
        NavigationRail(
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier.fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
            ) {
                visibleDestinationSet.forEach { destination ->
                    val isCurrentDestOnBackStack by navController.isRouteOnBackStackAsState(destination.direction)
                    NavigationRailItem(
                        selected = isCurrentDestOnBackStack,
                        onClick = {
                            if (isCurrentDestOnBackStack) {
                                navigator.popBackStack(destination.direction, false)
                            }
                            navigator.navigate(destination.direction) {
                                popUpTo(NavGraphs.root) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            if (isCurrentDestOnBackStack) {
                                Icon(destination.iconSelected, stringResource(destination.label))
                            } else {
                                Icon(destination.iconNotSelected, stringResource(destination.label))
                            }
                        },
                        label = { Text(stringResource(destination.label)) },
                        alwaysShowLabel = false,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}
