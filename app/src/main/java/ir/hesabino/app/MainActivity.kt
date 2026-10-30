package ir.hesabino.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class AppSection(val title: String, val symbol: String) {
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

@Composable
fun HesabinoApp() {
    var section by rememberSaveable { mutableStateOf(AppSection.HOME) }

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
                                Text(section.title, fontSize = 12.sp, color = Color(0xFF718096))
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
                            onSales = { section = AppSection.SALES },
                            onPurchases = { section = AppSection.PURCHASES },
                            onInventory = { section = AppSection.INVENTORY }
                        )
                        AppSection.SALES -> SectionScreen("فروش", "فاکتور فروش، مشتری، کالا و روش تسویه", "فروش جدید", "فاکتورهای فروش")
                        AppSection.PURCHASES -> SectionScreen("خرید", "خرید از تأمین‌کننده و افزایش خودکار موجودی", "خرید جدید", "فاکتورهای خرید")
                        AppSection.INVENTORY -> SectionScreen("انبار", "موجودی، ارزش کالا و گردش انبار", "افزودن کالا", "گردش موجودی")
                        AppSection.MORE -> MoreScreen()
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardScreen(onSales: () -> Unit, onPurchases: () -> Unit, onInventory: () -> Unit) {
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
                    MetricPill("فروش امروز", "۲٫۸۵ میلیون", Modifier.weight(1f))
                    MetricPill("سود امروز", "۶۸۰ هزار", Modifier.weight(1f))
                }
            }
        }

        Text("وضعیت مالی", fontWeight = FontWeight.Bold, fontSize = 17.sp)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FinanceCard("صندوق و بانک", "۲۵٫۸ میلیون", "موجودی", Modifier.weight(1f))
            FinanceCard("طلب مشتریان", "۱۸٫۷ میلیون", "دریافتنی", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FinanceCard("بدهی تأمین‌کنندگان", "۹٫۴ میلیون", "پرداختنی", Modifier.weight(1f))
            FinanceCard("ارزش انبار", "۳۲٫۱ میلیون", "بهای تمام‌شده", Modifier.weight(1f))
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
                AlertRow("۲ کالا به حداقل موجودی رسیده‌اند.", Color(0xFFD97706))
                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Color(0xFFEAEFF4))
                AlertRow("۱ مشتری دارای مانده سررسیدشده است.", Color(0xFFB42318))
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
