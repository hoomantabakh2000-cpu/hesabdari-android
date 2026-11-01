package ir.hesabino.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import ir.hesabino.app.data.Product
import ir.hesabino.app.data.ProductRepository
import java.text.NumberFormat
import java.util.Locale

private val HesabinoNavy = Color(0xFF082B50)
private val HesabinoTeal = Color(0xFF08B7B0)
private val HesabinoBackground = Color(0xFFF3F6FB)
private val inventoryCategories = listOf("همه کالاها", "آرایشی", "بهداشتی", "مراقبتی", "درمانی", "لوازم جانبی", "سایر")

private fun inventoryDigits(value: String): String = value.mapNotNull {
    when (it) {
        in '۰'..'۹' -> ('0'.code + (it.code - '۰'.code)).toChar()
        in '٠'..'٩' -> ('0'.code + (it.code - '٠'.code)).toChar()
        else -> if (it.isDigit()) it else null
    }
}.joinToString("")

private fun inventoryNumber(value: Long): String =
    NumberFormat.getIntegerInstance(Locale("fa")).format(value)

private fun inventoryToman(valueRial: Long): String =
    "${inventoryNumber(valueRial / 10)} تومان"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryFirstApp() {
    val context = LocalContext.current
    val repository = remember { ProductRepository(context) }
    var products by remember { mutableStateOf(repository.getAll()) }
    var search by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("همه کالاها") }
    var stockFilter by remember { mutableStateOf("همه") }
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Product?>(null) }
    var adjusting by remember { mutableStateOf<Product?>(null) }
    var deleting by remember { mutableStateOf<Product?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var scanTarget by remember { mutableStateOf("search") }
    var scanResult by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }

    DisposableEffect(repository) {
        onDispose { repository.close() }
    }

    fun reload() { products = repository.getAll() }

    val filtered = products.filter { p ->
        val textMatch = search.isBlank() || p.name.contains(search.trim(), true) || p.barcode.contains(inventoryDigits(search))
        val categoryMatch = category == "همه کالاها" || p.category == category
        val stockMatch = when (stockFilter) {
            "کم‌موجود" -> p.stock > 0 && p.stock <= p.minStock
            "ناموجود" -> p.stock == 0L
            else -> true
        }
        textMatch && categoryMatch && stockMatch
    }
    val lowCount = products.count { it.stock > 0 && it.stock <= it.minStock }
    val outCount = products.count { it.stock == 0L }
    val inventoryValue = runCatching { repository.getInventoryValueRial() }.getOrDefault(0L)

    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = HesabinoNavy,
                secondary = HesabinoTeal,
                background = HesabinoBackground,
                surface = Color.White
            )
        ) {
            Scaffold(
                containerColor = HesabinoBackground,
                snackbarHost = { SnackbarHost(snackbar) },
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text("حسابینو", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = Color.White)
                                Text("مدیریت انبار و کالاها", fontSize = 12.sp, color = Color.White.copy(alpha = .78f))
                            }
                        },
                        actions = {
                            IconButton(onClick = { scanTarget = "search"; scanning = true }) {
                                Icon(Icons.Default.Search, contentDescription = "اسکن بارکد", tint = Color.White)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = HesabinoNavy)
                    )
                },
                floatingActionButton = {
                    ExtendedFloatingActionButton(
                        onClick = { editing = null; showForm = true },
                        containerColor = HesabinoTeal,
                        contentColor = Color.White,
                        icon = { Icon(Icons.Default.Add, null) },
                        text = { Text("ثبت کالای جدید", fontWeight = FontWeight.Bold) }
                    )
                }
            ) { padding ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp),
                    contentPadding = PaddingValues(top = 14.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            InventorySummaryCard("تعداد کالا", inventoryNumber(products.size.toLong()), "قلم کالا", Color(0xFF1676D2), Modifier.weight(1f))
                            InventorySummaryCard("ارزش موجودی", inventoryToman(inventoryValue), "بر اساس بهای تمام‌شده", Color(0xFF079B87), Modifier.weight(1.35f))
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            InventoryMiniCard("کم‌موجود", inventoryNumber(lowCount.toLong()), Color(0xFFFFA726), Modifier.weight(1f)) { stockFilter = "کم‌موجود" }
                            InventoryMiniCard("ناموجود", inventoryNumber(outCount.toLong()), Color(0xFFE05252), Modifier.weight(1f)) { stockFilter = "ناموجود" }
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("جستجوی نام کالا یا بارکد") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            trailingIcon = {
                                TextButton(onClick = { scanTarget = "search"; scanning = true }) { Text("اسکن") }
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White)
                        )
                    }
                    item {
                        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            items((listOf("همه کالاها") + (inventoryCategories.drop(1) + products.map { it.category }).distinct())) { item ->
                                FilterChip(
                                    selected = category == item,
                                    onClick = { category = item },
                                    label = { Text(item) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = HesabinoNavy, selectedLabelColor = Color.White)
                                )
                            }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            listOf("همه", "کم‌موجود", "ناموجود").forEach { item ->
                                FilterChip(
                                    selected = stockFilter == item,
                                    onClick = { stockFilter = item },
                                    label = { Text(item) }
                                )
                            }
                        }
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("فهرست کالاها", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = HesabinoNavy, modifier = Modifier.weight(1f))
                            Text("${inventoryNumber(filtered.size.toLong())} کالا", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                    if (filtered.isEmpty()) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                                Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Home, null, tint = HesabinoTeal, modifier = Modifier.size(42.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text(if (products.isEmpty()) "هنوز کالایی ثبت نشده" else "کالایی با این مشخصات پیدا نشد", fontWeight = FontWeight.Bold)
                                    Text("برای شروع، کالای جدید ثبت کن.", color = Color.Gray, fontSize = 12.sp)
                                }
                            }
                        }
                    } else {
                        items(filtered, key = { it.id }) { product ->
                            InventoryProductCard(
                                product = product,
                                onEdit = { editing = product; showForm = true },
                                onAdjust = { adjusting = product },
                                onDelete = { deleting = product }
                            )
                        }
                    }
                }
            }
        }
    }

    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("حذف کالا") },
            text = { Text("از حذف «" + deleting!!.name + "» مطمئنی؟ اگر کالا گردش انبار داشته باشد، حذف آن امکان‌پذیر نیست.") },
            confirmButton = {
                TextButton(onClick = {
                    val target = deleting!!
                    runCatching { repository.delete(target.id); reload() }
                        .onFailure { error = it.message ?: "حذف کالا انجام نشد" }
                    deleting = null
                }) { Text("حذف", color = Color(0xFFE05252)) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("انصراف") } }
        )
    }

    if (showForm) {
        InventoryProductForm(
            product = editing,
            onDismiss = { showForm = false; editing = null },
            onScan = { scanResult = ""; scanning = true; scanTarget = "form" },
            scannedCode = scanResult,
            onSave = { product ->
                runCatching {
                    if (product.id == 0L) repository.insert(product) else repository.update(product)
                    reload()
                    showForm = false
                    editing = null
                }.onFailure { error = it.message ?: "ذخیره کالا انجام نشد" }
            }
        )
    }
    if (adjusting != null) {
        InventoryStockAdjustmentDialog(
            product = adjusting!!,
            onDismiss = { adjusting = null },
            onSave = { newStock ->
                runCatching { repository.setStock(adjusting!!.id, newStock); reload(); adjusting = null }
                    .onFailure { error = it.message ?: "اصلاح موجودی انجام نشد" }
            }
        )
    }
    if (scanning) {
        BarcodeScannerDialog(
            onDismiss = { scanning = false },
            onCode = { code ->
                scanResult = code
                if (scanTarget == "search") search = code
                scanning = false
            }
        )
    }
    LaunchedEffect(error) {
        if (error.isNotBlank()) {
            snackbar.showSnackbar(error)
            error = ""
        }
    }
}

@Composable
private fun InventorySummaryCard(title: String, value: String, subtitle: String, accent: Color, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = Color.Gray, fontSize = 12.sp)
            Text(value, color = accent, fontWeight = FontWeight.ExtraBold, fontSize = if (value.length > 15) 14.sp else 20.sp, maxLines = 1)
            Text(subtitle, color = Color.Gray, fontSize = 10.sp)
        }
    }
}

@Composable
private fun InventoryMiniCard(title: String, value: String, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null, tint = accent)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(title, fontSize = 11.sp, color = Color.Gray)
                Text(value, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = accent)
            }
        }
    }
}

@Composable
private fun InventoryProductCard(product: Product, onEdit: () -> Unit, onAdjust: () -> Unit, onDelete: () -> Unit) {
    val isOut = product.stock == 0L
    val isLow = !isOut && product.stock <= product.minStock
    val status = if (isOut) "ناموجود" else if (isLow) "کم‌موجود" else "موجود"
    val statusColor = if (isOut) Color(0xFFE05252) else if (isLow) Color(0xFFFFA726) else Color(0xFF159B72)
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(product.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = HesabinoNavy)
                    Text("${product.category}  •  کد: ${if (product.barcode.isBlank()) "—" else product.barcode}", color = Color.Gray, fontSize = 11.sp)
                }
                Surface(color = statusColor.copy(alpha = .13f), shape = RoundedCornerShape(30.dp)) {
                    Text(status, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InventoryInfoCell("موجودی", "${inventoryNumber(product.stock)} ${product.unit}", Modifier.weight(1f))
                InventoryInfoCell("قیمت خرید", inventoryToman(product.purchasePriceRial), Modifier.weight(1f))
                InventoryInfoCell("قیمت فروش", inventoryToman(product.salePriceRial), Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("حداقل: ${inventoryNumber(product.minStock)} ${product.unit}", color = Color.Gray, fontSize = 11.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = onAdjust) { Text("اصلاح موجودی", fontSize = 10.sp) }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "ویرایش", tint = HesabinoNavy) }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "حذف", tint = Color(0xFFE05252)) }
            }
        }
    }
}

@Composable
private fun InventoryInfoCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.background(HesabinoBackground, RoundedCornerShape(11.dp)).padding(9.dp)) {
        Text(label, color = Color.Gray, fontSize = 10.sp)
        Text(value, color = HesabinoNavy, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun InventoryProductForm(product: Product?, onDismiss: () -> Unit, onScan: () -> Unit, scannedCode: String, onSave: (Product) -> Unit) {
    var name by remember(product?.id) { mutableStateOf(product?.name ?: "") }
    var barcode by remember(product?.id) { mutableStateOf(product?.barcode ?: "") }
    var category by remember(product?.id) { mutableStateOf(product?.category ?: "سایر") }
    var unit by remember(product?.id) { mutableStateOf(product?.unit ?: "عدد") }
    var purchase by remember(product?.id) { mutableStateOf(if (product == null) "" else (product.purchasePriceRial / 10).toString()) }
    var sale by remember(product?.id) { mutableStateOf(if (product == null) "" else (product.salePriceRial / 10).toString()) }
    var quantity by remember(product?.id) { mutableStateOf("") }
    var minimum by remember(product?.id) { mutableStateOf(product?.minStock?.toString() ?: "") }
    var localError by remember(product?.id) { mutableStateOf("") }
    LaunchedEffect(scannedCode) { if (scannedCode.isNotBlank()) barcode = scannedCode }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (product == null) "ثبت کالای جدید" else "ویرایش کالا") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("نام کالا") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(barcode, { barcode = inventoryDigits(it) }, label = { Text("بارکد") }, singleLine = true, modifier = Modifier.weight(1f))
                    TextButton(onClick = onScan) { Text("اسکن") }
                }
                OutlinedTextField(category, { category = it }, label = { Text("دسته‌بندی") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(unit, { unit = it }, label = { Text("واحد (عدد، بسته، عدد و...)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(purchase, { purchase = inventoryDigits(it) }, label = { Text("قیمت خرید (تومان)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(sale, { sale = inventoryDigits(it) }, label = { Text("قیمت فروش (تومان)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(minimum, { minimum = inventoryDigits(it) }, label = { Text("حداقل موجودی") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                if (product == null) {
                    OutlinedTextField(quantity, { quantity = inventoryDigits(it) }, label = { Text("موجودی اولیه") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                } else {
                    Text("موجودی فعلی: ${inventoryNumber(product.stock)} ${product.unit}  (برای جلوگیری از دستکاری گردش انبار، اینجا تغییر نمی‌کند)", color = Color.Gray, fontSize = 11.sp)
                }
                if (localError.isNotBlank()) Text(localError, color = Color(0xFFD32F2F), fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(onClick = {
                val buy = inventoryDigits(purchase).toLongOrNull()
                val sell = inventoryDigits(sale).toLongOrNull()
                val min = inventoryDigits(minimum).toLongOrNull() ?: 0L
                val qty = if (product == null) inventoryDigits(quantity).toLongOrNull() ?: 0L else product.stock
                if (name.isBlank()) localError = "نام کالا را وارد کن"
                else if (buy == null || buy <= 0L) localError = "قیمت خرید باید بیشتر از صفر باشد"
                else if (sell == null || sell < 0L || min < 0L || qty < 0L) localError = "مقادیر واردشده معتبر نیستند"
                else {
                    val buyRial = runCatching { Math.multiplyExact(buy, 10L) }.getOrNull()
                    val sellRial = runCatching { Math.multiplyExact(sell, 10L) }.getOrNull()
                    if (buyRial == null || sellRial == null) localError = "مبلغ واردشده بیش از حد مجاز است"
                    else onSave(Product(
                        id = product?.id ?: 0L,
                        name = name.trim(),
                        barcode = inventoryDigits(barcode),
                        unit = unit.trim().ifBlank { "عدد" },
                        category = category.trim().ifBlank { "سایر" },
                        purchasePriceRial = buyRial,
                        salePriceRial = sellRial,
                        stock = qty,
                        minStock = min
                    ))
                }
            }) { Text("ذخیره کالا") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}


@Composable
private fun InventoryStockAdjustmentDialog(product: Product, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var quantity by remember(product.id) { mutableStateOf(product.stock.toString()) }
    var error by remember(product.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اصلاح موجودی کالا") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(product.name, fontWeight = FontWeight.Bold)
                Text("موجودی ثبت‌شده: ${inventoryNumber(product.stock)} ${product.unit}", color = Color.Gray, fontSize = 12.sp)
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = inventoryDigits(it); error = "" },
                    label = { Text("موجودی واقعی") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                if (error.isNotBlank()) Text(error, color = Color.Red, fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsed = inventoryDigits(quantity).toLongOrNull()
                if (parsed == null || parsed < 0L) error = "تعداد معتبر وارد کن" else onSave(parsed)
            }) { Text("ثبت اصلاح موجودی") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
