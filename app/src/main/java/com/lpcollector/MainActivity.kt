package com.lpcollector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.lpcollector.data.model.ListType
import com.lpcollector.ui.detail.ReleaseDetailScreen
import com.lpcollector.ui.list.RecordListScreen
import com.lpcollector.ui.record.RecordScreen
import com.lpcollector.ui.search.SearchScreen
import com.lpcollector.ui.settings.SettingsScreen
import com.lpcollector.ui.theme.LPCollectorTheme
import kotlinx.serialization.Serializable

@Serializable object CollectionRoute
@Serializable object WishlistRoute
@Serializable object SearchRoute
@Serializable object SettingsRoute
@Serializable data class ReleaseRoute(val id: Long)
@Serializable data class RecordRoute(val id: Long)

private data class Tab(val route: Any, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(CollectionRoute, "Collection", Icons.Filled.LibraryMusic),
    Tab(WishlistRoute, "Wishlist", Icons.Filled.Favorite),
    Tab(SearchRoute, "Search", Icons.Filled.Search),
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LPCollectorTheme { App() } }
    }
}

@Composable
private fun App() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showTabs = tabs.any { tab -> destination?.hasRoute(tab.route::class) == true }

    Scaffold(
        // Only the bottom bar's space is reserved here; each screen handles the other insets itself.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showTabs) NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = destination?.hasRoute(tab.route::class) == true,
                        onClick = { nav.switchTab(tab.route) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { outer ->
        // Screens draw their own top bars; only reserve space for the bottom navigation.
        NavHost(
            nav,
            startDestination = CollectionRoute,
            modifier = Modifier.padding(bottom = outer.calculateBottomPadding()).consumeWindowInsets(outer),
        ) {
            composable<CollectionRoute> {
                RecordListScreen(
                    ListType.COLLECTION,
                    onOpenRecord = { nav.navigate(RecordRoute(it)) },
                    onSearch = { nav.switchTab(SearchRoute) },
                    onSettings = { nav.navigate(SettingsRoute) },
                )
            }
            composable<WishlistRoute> {
                RecordListScreen(
                    ListType.WISHLIST,
                    onOpenRecord = { nav.navigate(RecordRoute(it)) },
                    onSearch = { nav.switchTab(SearchRoute) },
                    onSettings = { nav.navigate(SettingsRoute) },
                )
            }
            composable<SearchRoute> {
                SearchScreen(
                    onOpenRelease = { nav.navigate(ReleaseRoute(it)) },
                    onSettings = { nav.navigate(SettingsRoute) },
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(onBack = dropUnlessResumed { nav.popBackStack() })
            }
            composable<ReleaseRoute> { entry ->
                val id = entry.toRoute<ReleaseRoute>().id
                ReleaseDetailScreen(
                    id,
                    onBack = dropUnlessResumed { nav.popBackStack() },
                    onOpenRecord = { nav.navigate(RecordRoute(it)) },
                )
            }
            composable<RecordRoute> { entry ->
                RecordScreen(entry.toRoute<RecordRoute>().id, onBack = dropUnlessResumed { nav.popBackStack() })
            }
        }
    }
}

private fun NavHostController.switchTab(route: Any) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
