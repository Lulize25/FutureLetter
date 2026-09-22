package com.mydiary.futureletter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mydiary.futureletter.ui.calendar.CalendarScreen
import com.mydiary.futureletter.ui.diary.DiaryEditScreen
import com.mydiary.futureletter.ui.letters.LetterListScreen
import com.mydiary.futureletter.ui.search.SearchScreen
import com.mydiary.futureletter.ui.settings.SettingsScreen
import com.mydiary.futureletter.ui.theme.FutureLetterTheme
import dagger.hilt.android.AndroidEntryPoint

object Routes {
    const val CALENDAR = "calendar"
    const val LETTERS = "letters"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val DIARY_EDIT = "diary_edit?entryId={entryId}&date={dateMillis}"

    fun diaryEdit(entryId: Long? = null, dateMillis: Long? = null): String =
        "diary_edit?entryId=${entryId ?: ""}&date=${dateMillis ?: ""}"
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FutureLetterTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // 编辑页全屏显示，不带底部导航
    val showBottomBar = currentRoute != Routes.DIARY_EDIT

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == Routes.CALENDAR,
                        onClick = { navController.navigateTopLevel(Routes.CALENDAR) },
                        icon = { Icon(Icons.Default.EditNote, contentDescription = "日记") },
                        label = { Text("日记") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.LETTERS,
                        onClick = { navController.navigateTopLevel(Routes.LETTERS) },
                        icon = { Icon(Icons.Default.MailOutline, contentDescription = "未来信") },
                        label = { Text("未来信") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.SEARCH,
                        onClick = { navController.navigateTopLevel(Routes.SEARCH) },
                        icon = { Icon(Icons.Default.Search, contentDescription = "搜索") },
                        label = { Text("搜索") }
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.SETTINGS,
                        onClick = { navController.navigateTopLevel(Routes.SETTINGS) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "设置") },
                        label = { Text("设置") }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.CALENDAR,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.CALENDAR) {
                CalendarScreen(
                    onOpenEditor = { dateMillis, entryId ->
                        navController.navigate(Routes.diaryEdit(entryId = entryId, dateMillis = dateMillis))
                    }
                )
            }
            composable(
                route = Routes.DIARY_EDIT,
                arguments = listOf(
                    androidx.navigation.navArgument("entryId") {
                        type = androidx.navigation.NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    androidx.navigation.navArgument("dateMillis") {
                        type = androidx.navigation.NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) {
                DiaryEditScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.LETTERS) { LetterListScreen() }
            composable(Routes.SEARCH) {
                SearchScreen(
                    onOpenEntry = { entryId ->
                        navController.navigate(Routes.diaryEdit(entryId = entryId))
                    }
                )
            }
            composable(Routes.SETTINGS) { SettingsScreen() }
        }
    }
}

private fun androidx.navigation.NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
