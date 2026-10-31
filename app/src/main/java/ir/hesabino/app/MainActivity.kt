package ir.hesabino.app

import android.Manifest
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import android.app.Activity
import ir.hesabino.app.data.*
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.Executors

private fun toman(rial: Long)=NumberFormat.getNumberInstance(Locale("fa")).format(rial/10L)+" تومان"
private enum class AppSection(val title:String){HOME("خانه"),SALES("فروش"),PURCHASES("خرید"),INVENTORY("انبار"),MORE("بیشتر")}

class MainActivity:ComponentActivity(){
    private val cameraPermission=registerForActivityResult(ActivityResultContracts.RequestPermission()){}
    override fun onCreate(b:Bundle?){super.onCreate(b);setContent{HesabinoApp()}}
    fun requestCamera(){if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)cameraPermission.launch(Manifest.permission.CAMERA)}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun HesabinoApp(){
    var section by rememberSaveable{mutableStateOf(AppSection.HOME)};var refresh by remember{mutableIntStateOf(0)}
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl){
        MaterialTheme(colorScheme=lightColorScheme(primary=Color(0xFF173B5F),secondary=Color(0xFF18A879),background=Color(0xFFF5F7FA))){
            Scaffold(topBar={TopAppBar(title={Column{Text("حسابینو",fontWeight=FontWeight.Bold);Text(section.title+" • مدیریت واقعی",fontSize=11.sp,color=Color.Gray)}})},bottomBar={NavigationBar{
                AppSection.entries.forEach{item->NavigationBarItem(selected=section==item,onClick={section=item},icon={Icon(when(item){AppSection.HOME->Icons.Default.Home;AppSection.SALES->Icons.Default.PointOfSale;AppSection.PURCHASES->Icons.Default.ReceiptLong;AppSection.INVENTORY->Icons.Default.Inventory2;AppSection.MORE->Icons.Default.MoreHoriz},null)},label={Text(item.title)})}
            }}){p->Box(Modifier.padding(p)){when(section){
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
    val ctx=LocalContext.current;val repo=remember{ProductRepository(ctx)};val ps=remember(refresh){repo.getAll()};val value=remember(refresh){repo.getInventoryValueRial()}
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF173B5F))){Column(Modifier.padding(20.dp)){Text("ارزش واقعی انبار",color=Color.White.copy(.75f));Text(toman(value),color=Color.White,fontSize=28.sp,fontWeight=FontWeight.Bold);Text("تعداد کالا: "+ps.size,color=Color.White.copy(.7f))}}}
        item{Action("فاکتور خرید","تأمین‌کننده + چند کالا + شماره فاکتور",Icons.Default.ReceiptLong,onPurchase)}
        item{Action("انبار و تأمین‌کنندگان","جستجو و ورود به کالاهای هر تأمین‌کننده",Icons.Default.Inventory2,onInventory)}
    }
}
@Composable private fun Action(t:String,s:String,icon:androidx.compose.ui.graphics.vector.ImageVector,click:()->Unit)=Card(Modifier.fillMaxWidth().clickable(onClick=click),colors=CardDefaults.cardColors(containerColor=Color.White)){Row(Modifier.padding(17.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=Color(0xFF18A879));Spacer(Modifier.width(13.dp));Column{Text(t,fontWeight=FontWeight.Bold);Text(s,color=Color.Gray,fontSize=12.sp)}}}

@Composable private fun SupplierInventoryScreen(refresh:Int,onChanged:()->Unit){
    val ctx=LocalContext.current;val db=remember{HesabinoDatabase(ctx)};val sr=remember{SupplierRepository(db)};val pur=remember{PurchaseRepository(db)}
    var search by remember{mutableStateOf("")};var selected by remember{mutableStateOf<Supplier?>(null)};var addSupplier by remember{mutableStateOf(false)};var addProduct by remember{mutableStateOf(false)};var showInvoices by remember{mutableStateOf(false)};var edit by remember{mutableStateOf<Product?>(null)};var message by remember{mutableStateOf("")}
    Column(Modifier.fillMaxSize().padding(16.dp)){
        if(selected==null){
            Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("انبار",fontSize=25.sp,fontWeight=FontWeight.Bold);Text("انبار بر اساس تأمین‌کننده",color=Color.Gray)};Button(onClick={addSupplier=true}){Icon(Icons.Default.PersonAdd,null);Text(" تأمین‌کننده")}}
            Spacer(Modifier.height(9.dp));OutlinedTextField(search,{search=it},Modifier.fillMaxWidth(),singleLine=true,label={Text("جستجوی تأمین‌کننده")},leadingIcon={Icon(Icons.Default.Search,null)})
            Spacer(Modifier.height(8.dp));LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(sr.getAll(search)){s->Card(Modifier.fillMaxWidth().clickable{selected=s;search=""},colors=CardDefaults.cardColors(containerColor=Color.White)){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.Business,null);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text(s.name,fontWeight=FontWeight.Bold);if(s.phone.isNotBlank())Text(s.phone,color=Color.Gray,fontSize=12.sp)};Icon(Icons.Default.ChevronLeft,null)}}}}}
        }else{
            val supplier=selected!!
            Row(verticalAlignment=Alignment.CenterVertically){IconButton(onClick={selected=null;search="";showInvoices=false}){Icon(Icons.Default.ArrowForward,null)};Column(Modifier.weight(1f)){Text(supplier.name,fontSize=21.sp,fontWeight=FontWeight.Bold);Text("کالاها و فاکتورهای این تأمین‌کننده",color=Color.Gray,fontSize=11.sp)};IconButton(onClick={addProduct=true}){Icon(Icons.Default.AddBox,null)}}
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){OutlinedButton(onClick={showInvoices=false},modifier=Modifier.weight(1f)){Icon(Icons.Default.Inventory2,null);Text(" کالاها")};OutlinedButton(onClick={showInvoices=true},modifier=Modifier.weight(1f)){Icon(Icons.Default.ReceiptLong,null);Text(" فاکتورها")}}
            Spacer(Modifier.height(8.dp))
            if(!showInvoices){
                OutlinedTextField(search,{search=it},Modifier.fillMaxWidth(),singleLine=true,label={Text("جستجوی کالا")},leadingIcon={Icon(Icons.Default.Search,null)})
                val products=sr.getProducts(supplier.id,search)
                Spacer(Modifier.height(6.dp));LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){
                    item{OutlinedButton(onClick={addProduct=true},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Add,null);Text("افزودن کالای جدید برای ${supplier.name}")}}
                    items(products,key={it.id}){p->Card(colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.padding(14.dp)){Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(p.name,fontWeight=FontWeight.Bold);Text("موجودی: ${p.stock} ${p.unit}",color=Color.Gray,fontSize=12.sp);if(p.barcode.isNotBlank())Text("بارکد: ${p.barcode}",color=Color.Gray,fontSize=11.sp)};IconButton(onClick={edit=p}){Icon(Icons.Default.Edit,null)}};Row(horizontalArrangement=Arrangement.spacedBy(14.dp)){Text("خرید: ${toman(p.purchasePriceRial)}",fontSize=12.sp);Text("فروش: ${toman(p.salePriceRial)}",fontSize=12.sp,fontWeight=FontWeight.Bold)}}}}
                    if(products.isEmpty())item{Text("هنوز کالایی از این تأمین‌کننده ثبت نشده؛ از «افزودن کالای جدید» شروع کن.",color=Color.Gray,modifier=Modifier.padding(12.dp))}
                }
            }else{
                val invoices=pur.getInvoices(supplier.id)
                if(invoices.isEmpty())Text("هنوز فاکتوری برای این تأمین‌کننده ثبت نشده.",color=Color.Gray,modifier=Modifier.padding(12.dp))
                else LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(invoices,key={it.id}){invoice->var expanded by remember(invoice.id){mutableStateOf(false)};Card(colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.padding(14.dp)){Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(if(invoice.invoiceNumber.isBlank())"بدون شماره فاکتور" else "فاکتور ${invoice.invoiceNumber}",fontWeight=FontWeight.Bold);Text("جمع: ${toman(invoice.totalRial)}",fontSize=12.sp,color=Color.Gray)};IconButton(onClick={expanded=!expanded}){Icon(if(expanded)Icons.Default.ExpandLess else Icons.Default.ExpandMore,null)}};if(expanded)pur.getInvoiceLines(invoice.id).forEach{line->Row(Modifier.fillMaxWidth().padding(top=6.dp)){Text(line.productName,Modifier.weight(1f),fontSize=13.sp);Text("${line.quantity} × ${toman(line.unitCostRial)}",fontSize=12.sp)}}}}}}
            }
        }
    }
    if(addSupplier)SupplierDialog({addSupplier=false}){n,p->sr.findOrCreate(n,p);addSupplier=false;onChanged()}
    if(addProduct&&selected!=null)AddProductForSupplierDialog(selected!!,{addProduct=false}){product,qty,cost,invoiceNumber->try{val id=ProductRepository(ctx).insert(product);pur.saveInvoice(selected!!.id,invoiceNumber,listOf(PurchaseLine(id,qty,cost)));addProduct=false;message="کالا و فاکتور خرید ثبت شد.";onChanged()}catch(e:Exception){message=e.message?:"خطا"}}
    if(edit!=null)EditProductDialog(edit!!,{edit=null},{p->ProductRepository(ctx).update(p);edit=null;onChanged()},{p->try{ProductRepository(ctx).delete(p.id);edit=null;onChanged()}catch(e:Exception){message=e.message?:"حذف کالا ممکن نیست"}})
    if(message.isNotBlank())AlertDialog(onDismissRequest={message=""},title={Text("حسابینو")},text={Text(message)},confirmButton={Button(onClick={message=""}){Text("باشه")}})
}
@Composable private fun SupplierDialog(onDismiss:()->Unit,onSave:(String,String)->Unit){
    var n by remember{mutableStateOf("")};var p by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,title={Text("تأمین‌کننده جدید")},text={Column{Field(n,{n=it},"نام تأمین‌کننده");Field(p,{p=it},"شماره تماس")}},confirmButton={Button(onClick={onSave(n,p)}){Text("ذخیره")}},dismissButton={TextButton(onClick=onDismiss){Text("انصراف")}})
}
@Composable private fun AddProductDialog(onDismiss:()->Unit,onSave:(Product)->Unit){
    var n by remember{mutableStateOf("")};var b by remember{mutableStateOf("")};var unit by remember{mutableStateOf("عدد")};var buy by remember{mutableStateOf("")};var sell by remember{mutableStateOf("")};var min by remember{mutableStateOf("0")}
    AlertDialog(onDismissRequest=onDismiss,title={Text("ثبت کالای جدید")},text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)){Field(n,{n=it},"نام کالا *");Field(b,{b=it},"بارکد");Field(unit,{unit=it},"واحد");Field(buy,{buy=it.filter(Char::isDigit)},"قیمت خرید تومان *");Field(sell,{sell=it.filter(Char::isDigit)},"قیمت فروش تومان");Field(min,{min=it.filter(Char::isDigit)},"حداقل موجودی")}},confirmButton={Button(onClick={onSave(Product(name=n,barcode=b,unit=unit,purchasePriceRial=(buy.toLongOrNull()?:0)*10,salePriceRial=(sell.toLongOrNull()?:0)*10,minStock=min.toLongOrNull()?:0))}){Text("ثبت کالا")}},dismissButton={TextButton(onClick=onDismiss){Text("انصراف")}})
}
@Composable private fun AddProductForSupplierDialog(supplier:Supplier,onDismiss:()->Unit,onSave:(Product,Long,Long,String)->Unit){
    var n by remember{mutableStateOf("")};var b by remember{mutableStateOf("")};var unit by remember{mutableStateOf("عدد")};var buy by remember{mutableStateOf("")};var sell by remember{mutableStateOf("")};var qty by remember{mutableStateOf("1")};var inv by remember{mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,title={Text("کالای جدید برای ${supplier.name}")},text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)){Field(n,{n=it},"نام کالا *");Field(b,{b=it},"بارکد");Field(unit,{unit=it},"واحد");Field(qty,{qty=it.filter(Char::isDigit)},"تعداد خرید *");Field(buy,{buy=it.filter(Char::isDigit)},"قیمت خرید هر واحد تومان *");Field(sell,{sell=it.filter(Char::isDigit)},"قیمت فروش تومان");Field(inv,{inv=it},"شماره فاکتور")}},confirmButton={Button(onClick={onSave(Product(name=n,barcode=b,unit=unit,purchasePriceRial=(buy.toLongOrNull()?:0)*10,salePriceRial=(sell.toLongOrNull()?:0)*10),qty.toLongOrNull()?:0,(buy.toLongOrNull()?:0)*10,inv)}){Text("ثبت کالا و خرید")}},dismissButton={TextButton(onClick=onDismiss){Text("انصراف")}})
}
@Composable private fun EditProductDialog(product:Product,onDismiss:()->Unit,onSave:(Product)->Unit,onDelete:(Product)->Unit){
    var n by remember{mutableStateOf(product.name)};var b by remember{mutableStateOf(product.barcode)};var buy by remember{mutableStateOf((product.purchasePriceRial/10).toString())};var sell by remember{mutableStateOf((product.salePriceRial/10).toString())};var min by remember{mutableStateOf(product.minStock.toString())}
    AlertDialog(onDismissRequest=onDismiss,title={Text("ویرایش کالا")},text={Column{Field(n,{n=it},"نام کالا");Field(b,{b=it},"بارکد");Field(buy,{buy=it.filter(Char::isDigit)},"قیمت خرید تومان");Field(sell,{sell=it.filter(Char::isDigit)},"قیمت فروش تومان");Field(min,{min=it.filter(Char::isDigit)},"حداقل موجودی")}},confirmButton={Button(onClick={onSave(product.copy(name=n,barcode=b,purchasePriceRial=(buy.toLongOrNull()?:0)*10,salePriceRial=(sell.toLongOrNull()?:0)*10,minStock=min.toLongOrNull()?:0))}){Text("ذخیره")}},dismissButton={TextButton(onClick={onDelete(product)}){Text("حذف کالا",color=Color.Red)}})
}

@Composable private fun PurchaseInvoiceScreen(refresh:Int,onChanged:()->Unit){
    val ctx=LocalContext.current;val db=remember{HesabinoDatabase(ctx)};val sr=remember{SupplierRepository(db)};val pr=remember{ProductRepository(ctx)};val pur=remember{PurchaseRepository(db)}
    var products by remember{mutableStateOf(pr.getAll())};LaunchedEffect(refresh){products=pr.getAll()}
    val suppliers=remember(refresh){sr.getAll()}
    var supplier by remember{mutableStateOf<Supplier?>(null)};var ss by remember{mutableStateOf("")};var ps by remember{mutableStateOf("")};var inv by remember{mutableStateOf("")};var lines by remember{mutableStateOf(listOf<PurchaseLine>())};var msg by remember{mutableStateOf("")};var addS by remember{mutableStateOf(false)};var addP by remember{mutableStateOf(false)};var scan by remember{mutableStateOf(false)};var showHistory by remember{mutableStateOf(false)}
    LazyColumn(Modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        item{Text("فاکتور خرید",fontSize=25.sp,fontWeight=FontWeight.Bold);Text("فاکتورهای خرید جداگانه برای هر تأمین‌کننده",color=Color.Gray)}
        item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){Button(onClick={showHistory=false},modifier=Modifier.weight(1f)){Text("فاکتور جدید")};OutlinedButton(onClick={showHistory=true},modifier=Modifier.weight(1f)){Icon(Icons.Default.History,null);Text("سوابق خرید")}}}
        if(showHistory){
            item{
                if(supplier==null){Field(ss,{ss=it},"جستجوی تأمین‌کننده برای سوابق");suppliers.filter{it.name.contains(ss,true)}.take(8).forEach{s->TextButton(onClick={supplier=s}){Text(s.name)}}}
                else{
                    Row(verticalAlignment=Alignment.CenterVertically){Text("سوابق: ${supplier!!.name}",fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));TextButton(onClick={supplier=null}){Text("تغییر")}}
                    val invoices=pur.getInvoices(supplier!!.id)
                    invoices.forEach{invoice->var expanded by remember(invoice.id){mutableStateOf(false)};Card(colors=CardDefaults.cardColors(containerColor=Color.White),modifier=Modifier.fillMaxWidth().padding(vertical=3.dp)){Column(Modifier.padding(12.dp)){Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(if(invoice.invoiceNumber.isBlank())"بدون شماره فاکتور" else "فاکتور ${invoice.invoiceNumber}",fontWeight=FontWeight.Bold);Text("جمع: ${toman(invoice.totalRial)}",fontSize=12.sp,color=Color.Gray)};IconButton(onClick={expanded=!expanded}){Icon(if(expanded)Icons.Default.ExpandLess else Icons.Default.ExpandMore,null)}};if(expanded)pur.getInvoiceLines(invoice.id).forEach{line->Row(Modifier.fillMaxWidth().padding(top=5.dp)){Text(line.productName,Modifier.weight(1f),fontSize=13.sp);Text("${line.quantity} × ${toman(line.unitCostRial)}",fontSize=12.sp)}}}}}
                    if(invoices.isEmpty())Text("برای این تأمین‌کننده هنوز فاکتوری ثبت نشده.",color=Color.Gray)
                }
            }
        }else{
            item{Field(inv,{inv=it},"شماره فاکتور")}
            item{if(supplier==null){Field(ss,{ss=it},"جستجوی تأمین‌کننده");suppliers.filter{it.name.contains(ss,true)}.take(5).forEach{s->TextButton(onClick={supplier=s}){Text(s.name)}};TextButton(onClick={addS=true}){Text("+ تأمین‌کننده جدید")}}else Row(verticalAlignment=Alignment.CenterVertically){Text("تأمین‌کننده: ${supplier!!.name}",fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));TextButton(onClick={supplier=null}){Text("تغییر")}}}
            if(supplier!=null){
                item{Column{Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.weight(1f)){Field(ps,{ps=it},"جستجوی کالا یا بارکد")};IconButton(onClick={scan=true}){Icon(Icons.Default.QrCodeScanner,null)};IconButton(onClick={addP=true}){Icon(Icons.Default.AddBox,null)}};TextButton(onClick={addP=true}){Text("+ ثبت کالای جدید")}}}
                items(products.filter{ps.isBlank()||it.name.contains(ps,true)||it.barcode==ps},key={it.id}){p->Card(Modifier.fillMaxWidth().clickable{val old=lines.find{it.productId==p.id};lines=if(old==null)lines+PurchaseLine(p.id,1,p.purchasePriceRial)else lines.map{if(it.productId==p.id)it.copy(quantity=it.quantity+1)else it}},colors=CardDefaults.cardColors(containerColor=Color.White)){Row(Modifier.padding(13.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(p.name,fontWeight=FontWeight.Bold);Text("موجودی ${p.stock} • خرید قبلی ${toman(p.purchasePriceRial)}",color=Color.Gray,fontSize=11.sp)};Icon(Icons.Default.AddCircle,null,tint=Color(0xFF18A879))}}}
                item{Text("اقلام فاکتور",fontWeight=FontWeight.Bold,fontSize=17.sp);lines.forEachIndexed{idx,line->val p=products.find{it.id==line.productId};if(p!=null)Row(verticalAlignment=Alignment.CenterVertically){Text(p.name,Modifier.weight(1f));Text("${line.quantity} × ${toman(line.unitCostRial)}");IconButton(onClick={lines=lines.filterIndexed{i,_->i!=idx}}){Icon(Icons.Default.Delete,null,tint=Color.Red)}}}}
                item{Button(onClick={try{val s=supplier?:error("تأمین‌کننده را انتخاب کن.");pur.saveInvoice(s.id,inv,lines);lines=listOf();inv="";msg="فاکتور ذخیره شد و موجودی افزایش یافت.";products=pr.getAll();onChanged()}catch(e:Exception){msg=e.message?:"خطا"}},enabled=lines.isNotEmpty(),modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Save,null);Text(" ذخیره فاکتور")};if(msg.isNotBlank())Text(msg,color=if(msg.startsWith("فاکتور"))Color(0xFF18A879) else Color.Red)}
            }
        }
    }
    if(addS)SupplierDialog({addS=false}){n,p->supplier=Supplier(sr.findOrCreate(n,p),n,p);addS=false;onChanged()}
    if(addP)AddProductDialog({addP=false}){product->try{val id=pr.insert(product);products=pr.getAll();lines=lines+PurchaseLine(id,1,product.purchasePriceRial);addP=false;ps=product.name;msg="کالا ثبت شد و به فاکتور اضافه شد."}catch(e:Exception){msg=e.message?:"خطا"}}
    if(scan)BarcodeScannerDialog({scan=false}){code->ps=code;scan=false}
}
@Composable private fun BarcodeScannerDialog(onDismiss:()->Unit,onCode:(String)->Unit){
    val context=LocalContext.current; val owner=LocalLifecycleOwner.current; var done by remember{mutableStateOf(false)}
    LaunchedEffect(Unit){ if(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED && context is Activity) ActivityCompat.requestPermissions(context,arrayOf(Manifest.permission.CAMERA),7001) }
    AlertDialog(onDismissRequest=onDismiss,title={Text("اسکن بارکد")},text={Column{
        AndroidView(factory={ctx->PreviewView(ctx).also{preview->
            val future=ProcessCameraProvider.getInstance(ctx);future.addListener({
                val provider=future.get();val analysis=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(Executors.newSingleThreadExecutor()){proxy->
                    val media=proxy.image
                    if(media==null){proxy.close();return@setAnalyzer}
                    BarcodeScanning.getClient().process(InputImage.fromMediaImage(media,proxy.imageInfo.rotationDegrees)).addOnSuccessListener{codes->if(!done)codes.firstOrNull()?.rawValue?.let{done=true;onCode(it)}}.addOnCompleteListener{proxy.close()}
                }
                provider.unbindAll();provider.bindToLifecycle(owner,CameraSelector.DEFAULT_BACK_CAMERA,Preview.Builder().build().also{it.surfaceProvider=preview.surfaceProvider},analysis)
            },ContextCompat.getMainExecutor(ctx))
        }},Modifier.fillMaxWidth().height(260.dp))
        Text("دوربین را روی بارکد بگیر؛ بعد از تشخیص خودکار بسته می‌شود.",fontSize=12.sp,color=Color.Gray)
    }},confirmButton={TextButton(onClick=onDismiss){Text("بستن")}})
}
@Composable private fun Field(v:String,c:(String)->Unit,l:String)=OutlinedTextField(v,c,label={Text(l)},singleLine=true,modifier=Modifier.fillMaxWidth())
@Composable private fun Coming(t:String,s:String)=Column(Modifier.fillMaxSize().padding(20.dp)){Text(t,fontSize=25.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(12.dp));Text(s,color=Color.Gray)}
