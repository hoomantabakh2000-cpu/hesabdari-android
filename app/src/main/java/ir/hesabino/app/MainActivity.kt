package ir.hesabino.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HesabinoApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HesabinoApp() {
    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(title = { Text("حسابینو") })
            }
        ) { padding ->
            Column(
                modifier = Modifier.padding(padding).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("حسابداری و انبارداری", style = MaterialTheme.typography.headlineSmall)
                Text("نسخه اولیه پروژه آماده است.")
                Button(onClick = {}) { Text("فروش جدید") }
                OutlinedButton(onClick = {}) { Text("خرید جدید") }
                OutlinedButton(onClick = {}) { Text("انبار") }
                OutlinedButton(onClick = {}) { Text("گزارش‌ها") }
            }
        }
    }
}
