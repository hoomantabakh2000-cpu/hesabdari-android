package ir.hesabino.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.hesabino.app.data.Product
import ir.hesabino.app.data.ProductRepository
import java.text.NumberFormat
import java.util.Locale

private fun toman(rial: Long) = NumberFormat.getNumberInstance(Locale("fa")).format(rial / 10L) + " تومان"\n\nprivate enum class AppSection(val title: String, val symbol: String) {
    HOME("خانه", "⌂"),
    SALES("فروش", "↗"),
    PURCHASES("خرید", "↙"),
    INVENTORY("انبار", "▦"),
    MORE("بیشتر", "☰")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HesabinoApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HesabinoApp() {
    var section by rememberSaveable { mutableStateOf(AppSection.HOME) }\n    var refresh by remember { mutableIntStateOf(0) }\n
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = Color(0xFF173B5F),
                secondary = Color(0xFF18A879),
                surface = Color(0xFFF7F9FC),
                background = Color(0xFFF7F9FC)
            )
        ) {
            Scaffold(
                containerColor = Color(0xFFF7F9FC),
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text("حسابینو", fontWeight = FontWeight.Bold)
                                Text(section.title + " • نسخه ۰.۴", fontSize = 12.sp, color = Color(0xFF718096))
                            }
                        }
                    )
                },
                bottomBar = {
                    NavigationBar(
                        modifier = Modifier.navigationBarsPadding(),
                        containerColor = Color.White
                    ) {
                        AppSection.entries.forEach { item ->
                            NavigationBarItem(
                                selected = section == item,
                                onClick = { section = item },
                                icon = { Text(item.symbol, fontSize = 21.sp) },
                                label = { Text(item.title, fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color(0xFF173B5F),
                                    selectedTextColor = Color(0xFF173B5F),
                                    indicatorColor = Color(0xFFE7F0F8)
                                )
                            )
                        }
                    }
                }
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    when (section) {
                        AppSection.HOME -> DashboardScreen(
                            refresh = refresh,
                            onSales = { section = AppSection.SALES },
                            onPurchases = { section = AppSection.PURCHASES },
                            onInventory = { section = AppSection.INVENTORY }
                        )
                        AppSection.SALES -> SectionScreen("فروش", "فاکتور فروش، مشتری، کالا و روش تسویه", "فروش جدید", "فاکتورهای فروش")
                        AppSection.PURCHASES -> PurchaseScreen(refresh) { refresh++ }
                        AppSection.INVENTORY -> InventoryScreen(refresh) { refresh++ }
                        AppSection.MORE -> MoreScreen()
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardScreen(refresh: Int, onSales: () -> Unit, onPurchases: () -> Unit, onInventory: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { ProductRepository(context) }
    val products = remember(refresh) { repo.getAll() }
    val inventoryValue = remember(refresh) { repo.getInventoryValueRial() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF173B5F)), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text("داشبورد مالی", color = Color.White, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text("تصویر سریع کسب‌وکار شما", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricPill("فروش امروز", "۰ تومان", Modifier.weight(1f))
                    MetricPill("سود امروز", "۰ تومان", Modifier.weight(1f))
                }
            }
        }

        Text("وضعیت مالی", fontWeight = FontWeight.Bold, fontSize = 17.sp)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FinanceCard("صندوق و بانک", "۰ تومان", "موجودی", Modifier.weight(1f))
            FinanceCard("طلب مشتریان", "۰ تومان", "دریافتنی", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FinanceCard("بدهی تأمین‌کنندگان", "۰ تومان", "پرداختنی", Modifier.weight(1f))\n            FinanceCard("ارزش انبار", toman(inventoryValue), "بهای تمام‌شده", Modifier.weight(1f))
        }

        Text("دسترسی سریع", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        QuickAction("فروش جدید", "ثبت فاکتور و دریافت", "↗", onSales)
        QuickAction("خرید جدید", "ثبت خرید و افزایش موجودی", "↙", onPurchases)
        QuickAction("انبار", "کالاها و گردش موجودی", "▦", onInventory)
        QuickAction("طرف حساب‌ها", "مشتریان و تأمین‌کنندگان", "◎") {}

        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(18.dp)) {
                Text("هشدارها", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text("فعلاً هشداری ثبت نشده است.", color = Color(0xFF718096), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun MetricPill(title: String, value: String, modifier: Modifier) {
    Surface(color = Color.White.copy(alpha = 0.12f), shape = RoundedCornerShape(14.dp), modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(title, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
            Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun FinanceCard(title: String, value: String, subtitle: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(15.dp)) {
            Text(title, color = Color(0xFF718096), fontSize = 11.sp)
            Spacer(Modifier.height(7.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFF172B4D))
            Text(subtitle, color = Color(0xFF9AA5B1), fontSize = 10.sp)
        }
    }
}

@Composable
private fun QuickAction(title: String, subtitle: String, symbol: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(46.dp), color = Color(0xFFEAF5F1), shape = RoundedCornerShape(14.dp)) {
                Box(contentAlignment = Alignment.Center) { Text(symbol, color = Color(0xFF159A70), fontSize = 23.sp) }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Color(0xFF718096), fontSize = 12.sp)
            }
            Text("‹", color = Color(0xFF718096), fontSize = 25.sp)
        }
    }
}

@Composable
private fun AlertRow(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(9.dp), color = color, shape = RoundedCornerShape(50)) {}
        Spacer(Modifier.width(9.dp))
        Text(text, fontSize = 13.sp, color = Color(0xFF4A5568))
    }
}

@Composable
private fun SectionScreen(title: String, description: String, primary: String, secondary: String) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF172B4D))
        Text(description, color = Color(0xFF718096))
        Button(onClick = {}, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp)) { Text(primary) }
        OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp)) { Text(secondary) }
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(18.dp)) {
                Text("ساختار این بخش آماده است", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "در مرحله بعد فرم‌ها، دیتابیس و عملیات واقعی این قسمت به همین رابط متصل می‌شوند.",
                    color = Color(0xFF718096),
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun MoreScreen() {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("مدیریت حسابینو", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        MoreRow("طرف حساب‌ها", "مشتریان و تأمین‌کنندگان")
        MoreRow("دریافت و پرداخت", "صندوق، بانک و تسویه حساب")
        MoreRow("گزارش‌ها", "فروش، خرید، سود و انبار")
        MoreRow("هزینه‌ها", "ثبت و تحلیل هزینه‌های کسب‌وکار")
        MoreRow("پشتیبان‌گیری", "نسخه پشتیبان و بازیابی اطلاعات")
        MoreRow("تنظیمات", "اطلاعات فروشگاه و تنظیمات حسابداری")
    }
}

@Composable
private fun MoreRow(title: String, subtitle: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(17.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = Color(0xFF718096), fontSize = 12.sp)
        }
    }
}


@Composable
private fun InventoryScreen(refresh: Int, onChanged: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { ProductRepository(context) }
    var showAdd by remember { mutableStateOf(false) }
    val products = remember(refresh) { repo.getAll() }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("انبار واقعی", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(products.size.toString() + " کالا ثبت شده", color = Color.Gray)
            }
            Button(onClick = { showAdd = true }) { Icon(Icons.Default.Add, null); Text(" افزودن کالا") }
        }
        Spacer(Modifier.height(12.dp))
        if (products.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("هنوز کالایی ثبت نشده است.", color = Color.Gray) }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(products, key = { it.id }) { p ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Inventory2, null, tint = if (p.stock <= p.minStock) Color.Red else Color(0xFF18A879))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, fontWeight = FontWeight.Bold)
                                Text("قیمت فروش: " + toman(p.salePriceRial), fontSize = 12.sp, color = Color.Gray)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(p.stock.toString() + " " + p.unit, fontWeight = FontWeight.Bold)
                                if (p.stock <= p.minStock) Text("کمبود موجودی", color = Color.Red, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
    if (showAdd) ProductDialog({ showAdd = false }) {
        repo.insert(it); showAdd = false; onChanged()
    }
}

@Composable
private fun ProductDialog(onDismiss: () -> Unit, onSave: (Product) -> Unit) {
    var name by remember { mutableStateOf("") }; var barcode by remember { mutableStateOf("") }
    var buy by remember { mutableStateOf("") }; var sell by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("") }; var min by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("افزودن کالای جدید") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SimpleField(name, { name = it }, "نام کالا")
            SimpleField(barcode, { barcode = it }, "بارکد (اختیاری)")
            SimpleField(buy, { buy = it.filter(Char::isDigit) }, "قیمت خرید (تومان)")
            SimpleField(sell, { sell = it.filter(Char::isDigit) }, "قیمت فروش (تومان)")
            SimpleField(stock, { stock = it.filter(Char::isDigit) }, "موجودی اولیه")
            SimpleField(min, { min = it.filter(Char::isDigit) }, "حداقل موجودی")
            if (error.isNotBlank()) Text(error, color = Color.Red, fontSize = 12.sp)
        }
    }, confirmButton = {
        Button(onClick = {
            try {
                onSave(Product(name = name, barcode = barcode, purchasePriceRial = (buy.toLongOrNull() ?: 0L) * 10L, salePriceRial = (sell.toLongOrNull() ?: 0L) * 10L, stock = stock.toLongOrNull() ?: 0L, minStock = min.toLongOrNull() ?: 0L))
            } catch (e: Exception) { error = e.message ?: "اطلاعات نامعتبر است." }
        }) { Text("ذخیره کالا") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } })
}

@Composable
private fun SimpleField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun PurchaseScreen(refresh: Int, onChanged: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { ProductRepository(context) }
    val products = remember(refresh) { repo.getAll() }
    var selected by remember { mutableStateOf<Product?>(null) }
    var quantity by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("ثبت خرید واقعی", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("با ثبت خرید، موجودی و ارزش انبار همان لحظه تغییر می‌کند.", color = Color.Gray)
        }
        if (products.isEmpty()) item { Text("اول از بخش انبار یک کالا اضافه کن.", color = Color.Red) }
        items(products, key = { it.id }) { p ->
            Card(Modifier.fillMaxWidth().clickable { selected = p; price = (p.purchasePriceRial / 10L).toString() },
                colors = CardDefaults.cardColors(containerColor = if (selected?.id == p.id) Color(0xFFE8F5E9) else Color.White)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selected?.id == p.id, onClick = { selected = p; price = (p.purchasePriceRial / 10L).toString() })
                    Column { Text(p.name, fontWeight = FontWeight.Bold); Text("موجودی فعلی: " + p.stock + " " + p.unit, color = Color.Gray, fontSize = 12.sp) }
                }
            }
        }
        item {
            SimpleField(quantity, { quantity = it.filter(Char::isDigit) }, "تعداد خرید")
            Spacer(Modifier.height(8.dp))
            SimpleField(price, { price = it.filter(Char::isDigit) }, "قیمت خرید هر واحد (تومان)")
            Spacer(Modifier.height(10.dp))
            Button(onClick = {
                try {
                    val p = selected ?: error("یک کالا انتخاب کن.")
                    repo.addPurchase(p.id, quantity.toLongOrNull() ?: 0L, (price.toLongOrNull() ?: 0L) * 10L)
                    quantity = ""; message = "خرید ثبت شد؛ موجودی انبار به‌روزرسانی شد."; onChanged()
                } catch (e: Exception) { message = e.message ?: "خطا در ثبت خرید" }
            }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.AddShoppingCart, null); Text(" ثبت خرید و افزایش موجودی") }
            if (message.isNotBlank()) Text(message, color = if (message.startsWith("خرید ثبت")) Color(0xFF18A879) else Color.Red)
        }
    }
}
