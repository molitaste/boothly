package com.boothly.app

import android.app.Application
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.boothly.app.data.Repo
import com.boothly.app.ui.BoothlyTheme
import com.boothly.app.ui.ExportScreen
import com.boothly.app.ui.HomeScreen
import com.boothly.app.ui.PhotoScreen
import com.boothly.app.ui.ProductEditScreen
import com.boothly.app.ui.ScannerScreen
import com.boothly.app.ui.SupplierDetailScreen
import com.boothly.app.ui.SupplierEditScreen

class BoothlyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BoothlyTheme {
                AppNav()
            }
        }
    }
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onOpenSupplier = { nav.navigate("supplier/$it") },
                onNewSupplier = { nav.navigate("editSupplier/new") },
                onOpenProduct = { p -> nav.navigate("product/${p.supplierId}/${p.id}") },
                onExport = { nav.navigate("export") },
            )
        }
        composable("supplier/{id}") { e ->
            val id = e.arguments?.getString("id") ?: ""
            SupplierDetailScreen(
                id = id,
                onBack = { nav.popBackStack() },
                onEdit = { nav.navigate("editSupplier/$id") },
                onAddProduct = { nav.navigate("product/$id/new") },
                onOpenProduct = { pid -> nav.navigate("product/$id/$pid") },
                onPhoto = { name -> nav.navigate("photo/" + Uri.encode(name)) },
            )
        }
        composable("editSupplier/{id}") { e ->
            val id = e.arguments?.getString("id") ?: "new"
            val scanned by e.savedStateHandle.getStateFlow("scanned", "").collectAsState()
            SupplierEditScreen(
                id = id,
                onBack = { nav.popBackStack() },
                onSaved = { savedId, isNew ->
                    nav.popBackStack()
                    if (isNew) nav.navigate("supplier/$savedId")
                },
                onPhoto = { name -> nav.navigate("photo/" + Uri.encode(name)) },
                onScan = { nav.navigate("scan/card") },
                scanned = scanned,
                onScannedConsumed = { e.savedStateHandle["scanned"] = "" },
            )
        }
        composable("product/{sid}/{pid}") { e ->
            val scanned by e.savedStateHandle.getStateFlow("scanned", "").collectAsState()
            ProductEditScreen(
                supplierId = e.arguments?.getString("sid") ?: "",
                productId = e.arguments?.getString("pid") ?: "new",
                onBack = { nav.popBackStack() },
                onPhoto = { name -> nav.navigate("photo/" + Uri.encode(name)) },
                onTakePhotos = { nav.navigate("scan/photo") },
                scanned = scanned,
                onScannedConsumed = { e.savedStateHandle["scanned"] = "" },
            )
        }
        composable("scan/{mode}") { e ->
            ScannerScreen(
                mode = e.arguments?.getString("mode") ?: "card",
                onDone = { names ->
                    nav.previousBackStackEntry?.savedStateHandle?.set("scanned", names.joinToString("|"))
                    nav.popBackStack()
                },
                onCancel = { nav.popBackStack() },
            )
        }
        composable("export") {
            ExportScreen(onBack = { nav.popBackStack() })
        }
        composable("photo/{name}") { e ->
            PhotoScreen(name = e.arguments?.getString("name") ?: "", onBack = { nav.popBackStack() })
        }
    }
}
