package ir.hesabino.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.*
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import ir.hesabino.app.data.*
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.Executors

private fun digitsOnly(value:String):String = value.map {
    when(it) {
        '۰'->'0';'۱'->'1';'۲'->'2';'۳'->'3';'۴'->'4'
        '۵'->'5';'۶'->'6';'۷'->'7';'۸'->'8';'۹'->'9'
        '٠'->'0';'١'->'1';'٢'->'2';'٣'->'3';'٤'->'4'
        '٥'->'5';'٦'->'6';'٧'->'7';'٨'->'8';'٩'->'9'
        else->if(it.isDigit())it else null
    }
}.filterNotNull().joinToString("")

private fun grouped(value:String):String {
    val d=digitsOnly(value).trimStart('0').ifBlank{"0"}
    return NumberFormat.getNumberInstance(Locale("fa")).format(d.toLongOrNull()?:0L)
}
private fun toman(rial:Long)=grouped((rial/10L).toString())+" تومان"
private enum class AppSection(val title:String){HOME("خانه"),SALES("فروش"),PURCHASES("خرید"),INVENTORY("انبار"),MORE("بیشتر")}

class MainActivity:ComponentActivity(){
    private val cameraPermission=registerForActivityResult(ActivityResultContracts.RequestPermission()){}
    override fun onCreate(b:Bundle?){super.onCreate(b);setContent{InventoryFirstApp()}}
    fun requestCamera(){
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)
            cameraPermission.launch(Manifest.permission.CAMERA)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun HesabinoApp(){
    var section by rememberSaveable{mutableStateOf(AppSection.HOME)}
    var refresh by remember{mutableIntStateOf(0)}
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl){
        MaterialTheme(colorScheme=lightColorScheme(primary=Color(0xFF173B5F),secondary=Color(0xFF18A879),background=Color(0xFFF5F7FA))){
            Scaffold(
                topBar={TopAppBar(title={Column{Text("حسابینو",fontWeight=FontWeight.Bold);Text(section.title+" • مدیریت واقعی",fontSize=11.sp,color=Color.Gray)}})},
                bottomBar={NavigationBar{
                    AppSection.entries.forEach{item->
                        NavigationBarItem(selected=section==item,onClick={section=item},
                            icon={Icon(when(item){
                                AppSection.HOME->Icons.Default.Home;AppSection.SALES->Icons.Default.ShoppingCart
                                AppSection.PURCHASES->Icons.Default.ShoppingCart;AppSection.INVENTORY->Icons.Default.Home
                                AppSection.MORE->Icons.Default.MoreVert
                            },null)},label={Text(item.title)})
                    }
                }}
            ){p->Box(Modifier.padding(p)){when(section){
                AppSection.HOME->Dashboard(refresh,{section=AppSection.PURCHASES},{section=AppSection.INVENTORY})
                AppSection.PURCHASES->PurchaseInvoiceScreen(refresh){refresh++}
                AppSection.INVENTORY->SupplierInventoryScreen(refresh){refresh++}
                AppSection.SALES->Coming("فروش","فاکتور فروش در فاز بعدی.")
                AppSection.MORE->Coming("مدیریت","گزارش‌ها، دریافت و پرداخت، بکاپ و تنظیمات در فازهای بعدی.")
            }}}
        }
    }
}

@Composable private fun Dashboard(refresh:Int,onPurchase:()->Unit,onInventory:()->Unit){
    val ctx=LocalContext.current
    val repo=remember{ProductRepository(ctx)}
    val ps=remember(refresh){repo.getAll()}
    val value=remember(refresh){repo.getInventoryValueRial()}
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF173B5F))){Column(Modifier.padding(20.dp)){
            Text("ارزش واقعی انبار",color=Color.White.copy(.75f));Text(toman(value),color=Color.White,fontSize=28.sp,fontWeight=FontWeight.Bold)
            Text("تعداد کالا: "+grouped(ps.size.toString()),color=Color.White.copy(.7f))
        }}}
        item{Action("فاکتور خرید","تأمین‌کننده + چند کالا + شماره فاکتور",Icons.Default.ShoppingCart,onPurchase)}
        item{Action("انبار و تأمین‌کنندگان","جستجو و ورود به کالاهای هر تأمین‌کننده",Icons.Default.Home,onInventory)}
    }
}
@Composable private fun Action(t:String,s:String,icon:androidx.compose.ui.graphics.vector.ImageVector,click:()->Unit)=
    Card(Modifier.fillMaxWidth().clickable(onClick=click),colors=CardDefaults.cardColors(containerColor=Color.White)){
        Row(Modifier.padding(17.dp),verticalAlignment=Alignment.CenterVertically){
            Icon(icon,null,tint=Color(0xFF18A879));Spacer(Modifier.width(13.dp));Column{Text(t,fontWeight=FontWeight.Bold);Text(s,color=Color.Gray,fontSize=12.sp)}
        }
    }

@Composable private fun SupplierInventoryScreen(refresh:Int,onChanged:()->Unit){
    val ctx=LocalContext.current
    val db=remember{HesabinoDatabase(ctx)}
    val sr=remember{SupplierRepository(db)}
    val pur=remember{PurchaseRepository(db)}
    var search by remember{mutableStateOf("")}
    var selected by remember{mutableStateOf<Supplier?>(null)}
    var addSupplier by remember{mutableStateOf(false)}
    var edit by remember{mutableStateOf<Product?>(null)}
    var addProduct by remember{mutableStateOf(false)}
    var invoice by remember{mutableStateOf<PurchaseInvoice?>(null)}
    val suppliers=remember(search,refresh){sr.getAll(search)}

    Column(Modifier.fillMaxSize().padding(16.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){Text("انبار",fontSize=25.sp,fontWeight=FontWeight.Bold);Text("انبار بر اساس تأمین‌کننده",color=Color.Gray)}
            Button(onClick={addSupplier=true}){Icon(Icons.Default.Person,null);Text(" تأمین‌کننده")}
        }
        Spacer(Modifier.height(9.dp))
        OutlinedTextField(search,{search=it},Modifier.fillMaxWidth(),singleLine=true,label={Text(if(selected==null)"جستجوی تأمین‌کننده" else "جستجوی کالا")},leadingIcon={Icon(Icons.Default.Search,null)})
        if(selected==null){
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){
                items(suppliers,key={it.id}){s->Card(Modifier.fillMaxWidth().clickable{selected=s;search=""},colors=CardDefaults.cardColors(containerColor=Color.White)){
                    Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){
                        Icon(Icons.Default.Person,null);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(s.name,fontWeight=FontWeight.Bold);if(s.phone.isNotBlank())Text(s.phone,color=Color.Gray,fontSize=12.sp)}
                        Icon(Icons.Default.ArrowBack,null)
                    }
                }}
            }
        }else{
            val selectedId=selected?.id
            val products=if(selectedId!=null) remember(selectedId,search,refresh){sr.getProducts(selectedId,search)} else emptyList()
            val invoices=if(selectedId!=null) remember(selectedId,refresh){pur.getInvoices(selectedId)} else emptyList()
            val currentSupplier=selected
            if(currentSupplier==null) return@Column
            Row(verticalAlignment=Alignment.CenterVertically){
                IconButton(onClick={selected=null;search=""}){Icon(Icons.Default.ArrowForward,null)}
                Column(Modifier.weight(1f)){Text(currentSupplier.name,fontSize=21.sp,fontWeight=FontWeight.Bold);Text("کالاهای این تأمین‌کننده",fontSize=11.sp,color=Color.Gray)}
                Button(onClick={addProduct=true}){Text("+ کالا")}
            }
            Spacer(Modifier.height(6.dp))
            LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){
                item{Text("فاکتورهای این تأمین‌کننده",fontWeight=FontWeight.Bold,fontSize=17.sp)}
                items(invoices,key={it.id}){x->Card(Modifier.fillMaxWidth().clickable{invoice=x},colors=CardDefaults.cardColors(containerColor=Color.White)){
                    Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){
                        Text(if(x.invoiceNumber.isBlank())"بدون شماره" else "فاکتور ${x.invoiceNumber}",fontWeight=FontWeight.Bold)
                        Text("مبلغ: ${toman(x.totalRial)}",fontSize=12.sp,color=Color.Gray)
                    };Icon(Icons.Default.ArrowBack,null)}
                }}
                item{Text("کالاها",fontWeight=FontWeight.Bold,fontSize=17.sp)}
                items(products,key={it.id}){p->Card(colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.padding(14.dp)){
                    Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(p.name,fontWeight=FontWeight.Bold);Text("موجودی: ${grouped(p.stock.toString())} ${p.unit}",color=Color.Gray,fontSize=12.sp)}
                        IconButton(onClick={edit=p}){Icon(Icons.Default.Edit,null)}
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(14.dp)){Text("خرید: ${toman(p.purchasePriceRial)}",fontSize=12.sp);Text("فروش: ${toman(p.salePriceRial)}",fontSize=12.sp,fontWeight=FontWeight.Bold)}
                }}}
            }
        }
    }
    if(addSupplier)SupplierDialog({addSupplier=false}){n,p->sr.findOrCreate(n,p);addSupplier=false;onChanged()}
    if(addProduct && selected != null)NewProductForSupplierDialog(selected,{addProduct=false}){product,qty,invoiceNumber->
        try{pur.saveNewProductInvoice(selected.id,invoiceNumber,product,qty);addProduct=false;onChanged()}catch(_:Exception){}
    }
    if(edit!=null)EditProductDialog(edit!!,{edit=null},{p->try{ProductRepository(ctx).update(p);edit=null;onChanged()}catch(_:Exception){}},{p->try{ProductRepository(ctx).delete(p.id);edit=null;onChanged()}catch(_:Exception){}})
    if(invoice!=null)InvoiceDetailDialog(invoice!!,pur,{invoice=null})
}

@Composable private fun SupplierDialog(onDismiss:()->Unit,onSave:(String,String)->Unit){
    var n by remember{mutableStateOf("")};var p by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,title={Text("تأمین‌کننده جدید")},text={Column{Field(n,{n=it},"نام تأمین‌کننده");Field(p,{p=it},"شماره تماس")}},
        confirmButton={Button(onClick={onSave(n,p)},enabled=n.isNotBlank()){Text("ذخیره")}},dismissButton={TextButton(onClick=onDismiss){Text("انصراف")}})
}
@Composable private fun EditProductDialog(product:Product,onDismiss:()->Unit,onSave:(Product)->Unit,onDelete:(Product)->Unit){
    var n by remember{mutableStateOf(product.name)};var b by remember{mutableStateOf(product.barcode)}
    var buy by remember{mutableStateOf((product.purchasePriceRial/10).toString())};var sell by remember{mutableStateOf((product.salePriceRial/10).toString())};var min by remember{mutableStateOf(product.minStock.toString())}
    AlertDialog(onDismissRequest=onDismiss,title={Text("ویرایش کالا")},text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
        Field(n,{n=it},"نام کالا");Field(b,{b=it},"بارکد");MoneyField(buy,{buy=it},"قیمت خرید تومان");MoneyField(sell,{sell=it},"قیمت فروش تومان");NumberField(min,{min=it},"حداقل موجودی")
    }},confirmButton={Button(onClick={onSave(product.copy(name=n,barcode=b,purchasePriceRial=(digitsOnly(buy).toLongOrNull()?:0)*10,salePriceRial=(digitsOnly(sell).toLongOrNull()?:0)*10,minStock=digitsOnly(min).toLongOrNull()?:0))}){Text("ذخیره")}},
        dismissButton={TextButton(onClick={onDelete(product)}){Text("حذف کالا",color=Color.Red)}})
}

@Composable private fun PurchaseInvoiceScreen(refresh:Int,onChanged:()->Unit){
    val ctx=LocalContext.current
    val db=remember{HesabinoDatabase(ctx)}
    val sr=remember{SupplierRepository(db)}
    val pur=remember{PurchaseRepository(db)}
    val suppliers=remember(refresh){sr.getAll()}
    val products=remember(refresh){ProductRepository(ctx).getAll()}
    var supplier by remember{mutableStateOf<Supplier?>(null)};var ss by remember{mutableStateOf("")};var ps by remember{mutableStateOf("")};var inv by remember{mutableStateOf("")}
    var lines by remember{mutableStateOf(listOf<PurchaseLine>())};var msg by remember{mutableStateOf("")};var addS by remember{mutableStateOf(false)};var addProduct by remember{mutableStateOf(false)}
    var scan by remember{mutableStateOf(false)};var showInvoices by remember{mutableStateOf(false)};var selectedInvoice by remember{mutableStateOf<PurchaseInvoice?>(null)}
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        item{Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("فاکتور خرید",fontSize=25.sp,fontWeight=FontWeight.Bold);Text("ثبت کالا و فاکتور جداگانه برای هر تأمین‌کننده",color=Color.Gray)}
            TextButton(onClick={showInvoices=!showInvoices}){Text(if(showInvoices)"فاکتور جدید" else "فاکتورهای ثبت‌شده")}}}
        if(showInvoices){
            item{if(supplier==null){
                Field(ss,{ss=it},"جستجوی تأمین‌کننده")
                suppliers.filter{it.name.contains(ss,true)}.forEach{s->TextButton(onClick={supplier=s;ss=""}){Text(s.name)}}
                if(suppliers.isEmpty())Text("هنوز تأمین‌کننده‌ای ثبت نشده.",color=Color.Gray)
            }else Row(verticalAlignment=Alignment.CenterVertically){Text("فاکتورهای ${supplier!!.name}",fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));TextButton(onClick={supplier=null}){Text("تغییر تأمین‌کننده")}}}
            if(supplier!=null){items(pur.getInvoices(supplier!!.id),key={it.id}){x->Card(Modifier.fillMaxWidth().clickable{selectedInvoice=x},colors=CardDefaults.cardColors(containerColor=Color.White)){
                Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(if(x.invoiceNumber.isBlank())"بدون شماره" else "فاکتور ${x.invoiceNumber}",fontWeight=FontWeight.Bold);Text("مبلغ ${toman(x.totalRial)}",fontSize=12.sp,color=Color.Gray)};Icon(Icons.Default.ArrowBack,null)}
            }}}
        }else{
            item{Field(inv,{inv=it},"شماره فاکتور")}
            item{if(supplier==null){
                Field(ss,{ss=it},"تأمین‌کننده")
                suppliers.filter{it.name.contains(ss,true)}.forEach{s->TextButton(onClick={supplier=s;ss=""}){Text(s.name)}}
                TextButton(onClick={addS=true}){Text("+ تأمین‌کننده جدید")}
            }else Row(verticalAlignment=Alignment.CenterVertically){Text("تأمین‌کننده: ${supplier!!.name}",fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));TextButton(onClick={supplier=null}){Text("تغییر")}}}
            if(supplier!=null){
                item{Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.weight(1f)){Field(ps,{ps=it},"جستجوی کالا یا بارکد")};IconButton(onClick={scan=true}){Icon(Icons.Default.Search,null)}}}
                item{OutlinedButton(onClick={addProduct=true},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Add,null);Text("ثبت کالای جدید و افزودن به فاکتور")}}
                items(products.filter{ps.isBlank()||it.name.contains(ps,true)||it.barcode==digitsOnly(ps)},key={it.id}){p->Card(Modifier.fillMaxWidth().clickable{
                    val old=lines.find{it.productId==p.id}
                    lines=if(old==null)lines+PurchaseLine(p.id,1,p.purchasePriceRial)else lines.map{if(it.productId==p.id)it.copy(quantity=it.quantity+1)else it}
                },colors=CardDefaults.cardColors(containerColor=Color.White)){Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){Text(p.name,fontWeight=FontWeight.Bold);Text("موجودی ${grouped(p.stock.toString())} • خرید قبلی ${toman(p.purchasePriceRial)}",color=Color.Gray,fontSize=11.sp)}
                    Icon(Icons.Default.AddCircle,null,tint=Color(0xFF18A879))
                }}}
                item{
                    Text("اقلام فاکتور",fontWeight=FontWeight.Bold,fontSize=17.sp)
                    lines.forEachIndexed{idx,line->val p=products.find{it.id==line.productId};if(p!=null)Row(verticalAlignment=Alignment.CenterVertically){
                        Text(p.name,Modifier.weight(1f));Text("${grouped(line.quantity.toString())} × ${toman(line.unitCostRial)}")
                        IconButton(onClick={lines=lines.filterIndexed{i,_->i!=idx}}){Icon(Icons.Default.Delete,null,tint=Color.Red)}
                    }}
                }
                item{Button(onClick={try{val s=supplier?:error("تأمین‌کننده را انتخاب کن.");pur.saveInvoice(s.id,inv,lines);lines=listOf();inv="";msg="فاکتور ذخیره شد و موجودی افزایش یافت.";onChanged()}catch(e:Exception){msg=e.message?: "خطا"}},enabled=lines.isNotEmpty(),modifier=Modifier.fillMaxWidth()){
                    Icon(Icons.Default.Check,null);Text(" ذخیره فاکتور")
                };if(msg.isNotBlank())Text(msg,color=if(msg.startsWith("فاکتور"))Color(0xFF18A879) else Color.Red)}
            }
        }
    }
    if(addS)SupplierDialog({addS=false}){n,p->val id=sr.findOrCreate(n,p);supplier=sr.getAll().first{it.id==id};ss="";addS=false;onChanged()}
    if(addProduct)NewProductDialog({addProduct=false}){product,qty->try{
        val id=pur.insertProduct(product);lines=lines+PurchaseLine(id,qty,product.purchasePriceRial);addProduct=false;onChanged()
    }catch(e:Exception){msg=e.message?:"ثبت کالا ناموفق بود"}}
    if(selectedInvoice!=null)InvoiceDetailDialog(selectedInvoice!!,pur,{selectedInvoice=null})
    if(scan)BarcodeScannerDialog({scan=false}){code->ps=code;scan=false}
}

@Composable private fun NewProductDialog(onDismiss:()->Unit,onSave:(Product,Long)->Unit){
    var n by remember{mutableStateOf("")};var b by remember{mutableStateOf("")};var unit by remember{mutableStateOf("عدد")};var buy by remember{mutableStateOf("")};var sell by remember{mutableStateOf("")};var min by remember{mutableStateOf("")};var qty by remember{mutableStateOf("")}
    var scan by remember{mutableStateOf(false)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("ثبت کالای جدید")},text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
        Field(n,{n=it},"نام کالا")
        Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.weight(1f)){Field(b,{b=it},"بارکد")};IconButton(onClick={scan=true}){Icon(Icons.Default.Search,null)}}
        Field(unit,{unit=it},"واحد");MoneyField(buy,{buy=it},"قیمت خرید تومان");MoneyField(sell,{sell=it},"قیمت فروش تومان");NumberField(qty,{qty=it},"تعداد");NumberField(min,{min=it},"حداقل موجودی")
    }},confirmButton={Button(onClick={onSave(Product(name=n,barcode=digitsOnly(b),unit=unit,purchasePriceRial=(digitsOnly(buy).toLongOrNull()?:0)*10,salePriceRial=(digitsOnly(sell).toLongOrNull()?:0)*10,minStock=digitsOnly(min).toLongOrNull()?:0),digitsOnly(qty).toLongOrNull()?:0)},enabled=n.isNotBlank()&&(digitsOnly(buy).toLongOrNull()?:0)>0&&(digitsOnly(qty).toLongOrNull()?:0)>0){Text("ثبت کالا")}},dismissButton={TextButton(onClick=onDismiss){Text("انصراف")}})
    if(scan)BarcodeScannerDialog({scan=false}){code->b=code;scan=false}
}
@Composable private fun NewProductForSupplierDialog(supplier:Supplier,onDismiss:()->Unit,onSave:(Product,Long,String)->Unit){
    var n by remember{mutableStateOf("")};var b by remember{mutableStateOf("")};var buy by remember{mutableStateOf("")};var sell by remember{mutableStateOf("")};var qty by remember{mutableStateOf("")};var inv by remember{mutableStateOf("")};var scan by remember{mutableStateOf(false)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("خرید کالای جدید از ${supplier.name}")},text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
        Field(n,{n=it},"نام کالا")
        Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.weight(1f)){Field(b,{b=it},"بارکد")};IconButton(onClick={scan=true}){Icon(Icons.Default.Search,null)}}
        MoneyField(buy,{buy=it},"قیمت خرید تومان");MoneyField(sell,{sell=it},"قیمت فروش تومان");NumberField(qty,{qty=it},"تعداد");Field(inv,{inv=it},"شماره فاکتور")
    }},confirmButton={Button(onClick={onSave(Product(name=n,barcode=digitsOnly(b),purchasePriceRial=(digitsOnly(buy).toLongOrNull()?:0)*10,salePriceRial=(digitsOnly(sell).toLongOrNull()?:0)*10),digitsOnly(qty).toLongOrNull()?:0,inv)},enabled=n.isNotBlank()&&(digitsOnly(buy).toLongOrNull()?:0)>0&&(digitsOnly(qty).toLongOrNull()?:0)>0){Text("ثبت و ورود به انبار")}},dismissButton={TextButton(onClick=onDismiss){Text("انصراف")}})
    if(scan)BarcodeScannerDialog({scan=false}){code->b=code;scan=false}
}

@Composable private fun InvoiceDetailDialog(invoice:PurchaseInvoice,pur:PurchaseRepository,onDismiss:()->Unit){
    val lines=remember(invoice.id){pur.getInvoiceItems(invoice.id)}
    AlertDialog(onDismissRequest=onDismiss,title={Text(if(invoice.invoiceNumber.isBlank())"فاکتور خرید" else "فاکتور ${invoice.invoiceNumber}")},
        text={LazyColumn(verticalArrangement=Arrangement.spacedBy(7.dp)){items(lines){line->Row(Modifier.fillMaxWidth()){Text(line.productName,Modifier.weight(1f));Text("${grouped(line.quantity.toString())} × ${toman(line.unitCostRial)}")}};item{HorizontalDivider();Spacer(Modifier.height(6.dp));Text("جمع فاکتور: ${toman(invoice.totalRial)}",fontWeight=FontWeight.Bold)}}},
        confirmButton={Button(onClick=onDismiss){Text("بستن")}})
}

@Composable fun BarcodeScannerDialog(onDismiss:()->Unit,onCode:(String)->Unit){
    val context=LocalContext.current;val owner=LocalLifecycleOwner.current
    var done by remember{mutableStateOf(false)}
    val hasPermission=ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED
    AlertDialog(onDismissRequest=onDismiss,title={Text("اسکن بارکد")},text={
        Column{
            if(!hasPermission){
                Text("برای اسکن بارکد باید دسترسی دوربین فعال شود.",color=Color.Gray)
                TextButton(onClick={if(context is Activity)(context as Activity).let{it.requestPermissions(arrayOf(Manifest.permission.CAMERA),7001)}}){Text("اجازه دسترسی به دوربین")}
            }else{
                AndroidView(factory={ctx->PreviewView(ctx).also{preview->
                    val future=ProcessCameraProvider.getInstance(ctx)
                    future.addListener({
                        try{
                            val provider=future.get()
                            val analysis=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                            analysis.setAnalyzer(Executors.newSingleThreadExecutor()){proxy->
                                val media=proxy.image
                                if(media==null){proxy.close();return@setAnalyzer}
                                BarcodeScanning.getClient().process(InputImage.fromMediaImage(media,proxy.imageInfo.rotationDegrees))
                                    .addOnSuccessListener{codes->if(!done)codes.firstOrNull()?.rawValue?.let{done=true;onCode(it)}}.addOnCompleteListener{proxy.close()}
                            }
                            provider.unbindAll()
                            provider.bindToLifecycle(owner,CameraSelector.DEFAULT_BACK_CAMERA,Preview.Builder().build().also{it.surfaceProvider=preview.surfaceProvider},analysis)
                        }catch(_:SecurityException){}
                    },ContextCompat.getMainExecutor(ctx))
                }},Modifier.fillMaxWidth().height(260.dp))
                Text("دوربین را روی بارکد بگیر؛ بعد از تشخیص خودکار بسته می‌شود.",fontSize=12.sp,color=Color.Gray)
            }
        }
    },confirmButton={TextButton(onClick=onDismiss){Text("بستن")}})
}

@Composable private fun FormattedNumberField(v:String,c:(String)->Unit,l:String,suffix:String?=null){
    var focused by remember{mutableStateOf(false)}
    var text by remember{mutableStateOf(if(v.isBlank()) "" else grouped(v))}
    LaunchedEffect(v,focused){
        if(!focused){
            val target=if(v.isBlank()) "" else grouped(v)
            if(text!=target) text=target
        }
    }
    OutlinedTextField(
        value=text,
        onValueChange={raw->
            val digits=digitsOnly(raw)
            val normalized=when{
                digits.isBlank()->""
                digits.all{it=='0'}->"0"
                else->digits.trimStart('0')
            }
            text=normalized
            c(normalized)
        },
        label={Text(l)},
        singleLine=true,
        modifier=Modifier.fillMaxWidth().onFocusChanged{state->
            val wasFocused=focused
            focused=state.isFocused
            if(wasFocused && !state.isFocused){
                text=if(v.isBlank()) "" else grouped(v)
            }
        },
        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),
        suffix=suffix?.let{unit->{Text(unit)}}
    )
}
@Composable private fun MoneyField(v:String,c:(String)->Unit,l:String)=FormattedNumberField(v,c,l,"تومان")
@Composable private fun NumberField(v:String,c:(String)->Unit,l:String)=FormattedNumberField(v,c,l)
@Composable private fun Field(v:String,c:(String)->Unit,l:String)=OutlinedTextField(v,c,label={Text(l)},singleLine=true,modifier=Modifier.fillMaxWidth())
@Composable private fun Coming(t:String,s:String)=Column(Modifier.fillMaxSize().padding(20.dp)){Text(t,fontSize=25.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(12.dp));Text(s,color=Color.Gray)}
