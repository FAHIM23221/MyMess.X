package com.messerp.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messerp.app.data.Dates
import com.messerp.app.data.MemberLedger
import com.messerp.app.data.Money
import com.messerp.app.data.ReceiptPdf
import com.messerp.app.data.Role

private fun catLabel(c: String) = when (c) {
    "SEAT_RENT" -> "সিট ভাড়া"
    "MEAL" -> "মিল"
    "ELECTRICITY" -> "বিদ্যুৎ বিল"
    "CHEF" -> "রাঁধুনি বিল"
    "WIFI" -> "ওয়াই-ফাই"
    "GAS" -> "গ্যাস"
    "WATER" -> "পানি"
    "FINE" -> "জরিমানা"
    "PAYMENT" -> "পেমেন্ট"
    else -> "অন্যান্য"
}

private val CHARGE_CATEGORIES = listOf("SEAT_RENT", "ELECTRICITY", "WIFI", "GAS", "WATER", "FINE", "OTHER")
private val METHODS = listOf("CASH", "BKASH", "NAGAD", "BANK", "OTHER")

private fun signed(minor: Long): String = when {
    minor > 0 -> "${Money.format(minor)} বাকি"
    minor < 0 -> "${Money.format(-minor)} অগ্রিম"
    else -> Money.format(0)
}

@Composable
fun MonthBar(vm: AppViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(onClick = { vm.shiftMonth(-1) }, enabled = !vm.busy) { Text("‹") }
        Text("মাস: " + Dates.bnMonth(vm.ledgerMonth), fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = { vm.shiftMonth(1) }, enabled = !vm.busy) { Text("›") }
    }
}

@Composable
fun LedgerView(
    vm: AppViewModel,
    lg: MemberLedger,
    admin: Boolean,
    onReverseCharge: (String) -> Unit,
    onReversePayment: (String) -> Unit
) {
    AppCard {
        Text("এখনকার অবস্থা", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        when {
            lg.dueMinor > 0 -> Text(
                "বাকি " + Money.format(lg.dueMinor),
                fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error
            )
            lg.advanceMinor > 0 -> Text(
                "অগ্রিম জমা " + Money.format(lg.advanceMinor),
                fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary
            )
            else -> Text("কোনো বাকি নেই", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard("আগের বাকি/অগ্রিম", signed(lg.openingMinor), Modifier.weight(1f))
        StatCard("এ মাসের চার্জ", Money.format(lg.chargesMinor), Modifier.weight(1f))
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard("এ মাসের পেমেন্ট", Money.format(lg.paymentsMinor), Modifier.weight(1f))
        StatCard("মাস শেষে", signed(lg.closingMinor), Modifier.weight(1f))
    }
    SectionTitle("এ মাসের এন্ট্রি")
    if (lg.entries.isEmpty()) Text("এ মাসে কোনো এন্ট্রি নেই।", color = MaterialTheme.colorScheme.onSurfaceVariant)
    lg.entries.forEach { e ->
        AppCard {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                StatusChip(catLabel(e.category), positive = e.kind == "PAYMENT")
                Text(Dates.bnDate(e.entryDate), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!e.description.isNullOrBlank()) Text(e.description)
            when (e.kind) {
                "CHARGE" -> {
                    Text(
                        (if (e.reversed) "বাতিল হয়েছে: " else "চার্জ: ") + Money.format(e.amountMinor),
                        fontWeight = FontWeight.Bold
                    )
                    if (admin && !e.reversed) {
                        OutlinedButton(onClick = { onReverseCharge(e.id) }, enabled = !vm.busy) { Text("চার্জ বাতিল করুন") }
                    }
                }
                "PAYMENT" -> {
                    Text(
                        (if (e.reversed) "বাতিল পেমেন্ট: " else "পেমেন্ট: ") + Money.format(-e.amountMinor),
                        fontWeight = FontWeight.Bold,
                        color = if (e.reversed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                    )
                    if (e.receiptNo != null) Text("রসিদ: ${e.receiptNo}", fontSize = 12.sp)
                    val pid = e.paymentId
                    if (pid != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { vm.downloadReceipt(pid) }, enabled = !vm.busy) { Text("রসিদ ডাউনলোড") }
                            OutlinedButton(onClick = { vm.shareReceipt(pid) }, enabled = !vm.busy) { Text("শেয়ার") }
                        }
                        if (admin && !e.reversed) {
                            OutlinedButton(onClick = { onReversePayment(pid) }, enabled = !vm.busy) { Text("পেমেন্ট বাতিল করুন") }
                        }
                    }
                }
                else -> Text("সংশোধন এন্ট্রি: " + Money.format(e.amountMinor), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun BookSwitch(vm: AppViewModel) {
    val utility = vm.activeBook == "UTILITY"
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceChip("মেস খাতা", !utility) { if (utility) vm.switchBook("MESS") }
        ChoiceChip("বিদ্যুৎ ও রাঁধুনি বিল", utility) { if (!utility) vm.switchBook("UTILITY") }
    }
}

@Composable
fun BillsScreen(vm: AppViewModel) {
    LaunchedEffect(Unit) { vm.loadLedger() }
    val utility = vm.activeBook == "UTILITY"
    AppScaffold(vm, 0, if (utility) "বিদ্যুৎ ও রাঁধুনি বিল" else "আমার ব্যালেন্স", "বিল ও পেমেন্ট") {
        MessageText(vm)
        BookSwitch(vm)
        MonthBar(vm)
        val lg = vm.ledger
        if (lg == null) Text("লোড হচ্ছে...") else LedgerView(vm, lg, false, {}, {})
    }
}

@Composable
fun FinanceScreen(vm: AppViewModel) {
    val p = vm.profile ?: return
    val book = vm.activeBook
    val utility = book == "UTILITY"
    val isAdmin = p.role != Role.MEMBER
    if (!isAdmin && !(utility && vm.isBillMgr)) return
    val isMain = p.role == Role.MAIN_ADMIN
    val today = vm.mealStatus?.today ?: Dates.todayDhaka()
    val cats = if (utility) listOf("ELECTRICITY", "CHEF") else listOf("SEAT_RENT", "WIFI", "GAS", "WATER", "FINE", "OTHER")
    val people = vm.dues?.members ?: emptyList()
    val others = people.filter { it.id != p.id }

    var payMember by rememberSaveable(vm.formVersion, book) { mutableStateOf("") }
    var payMethod by rememberSaveable(vm.formVersion, book) { mutableStateOf("CASH") }
    var payAmount by rememberSaveable(vm.formVersion, book) { mutableStateOf("") }
    var payDate by rememberSaveable(vm.formVersion, book) { mutableStateOf(today) }
    var payTxn by rememberSaveable(vm.formVersion, book) { mutableStateOf("") }
    var payMemo by rememberSaveable(vm.formVersion, book) { mutableStateOf("") }

    var chMember by rememberSaveable(vm.formVersion, book) { mutableStateOf("") }
    var chCategory by rememberSaveable(vm.formVersion, book) { mutableStateOf(cats.first()) }
    var chAmount by rememberSaveable(vm.formVersion, book) { mutableStateOf("") }
    var chMonth by rememberSaveable(vm.formVersion, book) { mutableStateOf(today) }
    var chDesc by rememberSaveable(vm.formVersion, book) { mutableStateOf("") }

    var mealMonth by rememberSaveable(vm.formVersion) { mutableStateOf(Dates.addMonths(Dates.monthStart(today), -1)) }
    var mealForce by rememberSaveable(vm.formVersion) { mutableStateOf(false) }
    var mealReason by rememberSaveable(vm.formVersion) { mutableStateOf("") }

    var revPayId by rememberSaveable { mutableStateOf<String?>(null) }
    var revChargeId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(book) { vm.loadFinance() }

    AppScaffold(
        vm, 0,
        if (utility) "বিদ্যুৎ ও রাঁধুনি বিল" else "পেমেন্ট ও চার্জ",
        if (isAdmin) "অ্যাডমিন" else "বিল ম্যানেজার"
    ) {
        MessageText(vm)
        if (isAdmin) BookSwitch(vm)
        if (utility) {
            Text(
                "এই খাতা মেসের মূল খাতা থেকে আলাদা। নিজের নামে চার্জ বা পেমেন্ট এন্ট্রি করা যায় না; সেটা অন্য অ্যাডমিন করবেন।",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        vm.dues?.let { d ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("মোট বাকি", Money.format(d.totalDueMinor), Modifier.weight(1f))
                StatCard("মোট অগ্রিম জমা", Money.format(d.totalAdvanceMinor), Modifier.weight(1f), "বাকি থেকে আলাদা")
            }
        }

        vm.lastPaymentId?.let { pid ->
            AppCard {
                Text("সর্বশেষ রসিদ: ${vm.lastReceiptNo ?: ""}", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.downloadReceipt(pid) }, enabled = !vm.busy) { Text("রসিদ ডাউনলোড") }
                    OutlinedButton(onClick = { vm.shareReceipt(pid) }, enabled = !vm.busy) { Text("শেয়ার") }
                }
            }
        }

        SectionTitle("পেমেন্ট লিখুন")
        AppCard {
            Text("সদস্য", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            others.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 2.dp)) {
                    row.forEach { m -> ChoiceChip(m.name, payMember == m.id) { payMember = m.id } }
                }
            }
            Text("মাধ্যম", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            METHODS.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 2.dp)) {
                    row.forEach { mt -> ChoiceChip(ReceiptPdf.methodLabel(mt), payMethod == mt) { payMethod = mt } }
                }
            }
            Field("পরিমাণ (টাকা)", payAmount, { payAmount = it }, KeyboardType.Decimal, hint = "0.00")
            DateField("পেমেন্টের তারিখ", payDate) { payDate = it }
            Field("ট্রানজ্যাকশন রেফারেন্স (ঐচ্ছিক)", payTxn, { payTxn = it })
            Field("নোট (ঐচ্ছিক)", payMemo, { payMemo = it })
            PrimaryButton("পেমেন্ট সংরক্ষণ ও রসিদ তৈরি", !vm.busy) {
                vm.recordPayment(payMember, payAmount, payMethod, payDate, payTxn, payMemo)
            }
        }

        SectionTitle(if (utility) "বিল যোগ করুন" else "চার্জ যোগ করুন")
        AppCard {
            Text("বিভাগ", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            cats.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 2.dp)) {
                    row.forEach { c -> ChoiceChip(catLabel(c), chCategory == c) { chCategory = c } }
                }
            }
            Field("পরিমাণ (টাকা)", chAmount, { chAmount = it }, KeyboardType.Decimal, hint = "0.00")
            DateField("কোন মাসের (মাসের যেকোনো তারিখ)", chMonth) { chMonth = it }
            Field("বিবরণ (ঐচ্ছিক)", chDesc, { chDesc = it })
            Text("এক সদস্যকে দিতে নিচের তালিকা থেকে সদস্য বাছুন:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            others.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 2.dp)) {
                    row.forEach { m -> ChoiceChip(m.name, chMember == m.id) { chMember = m.id } }
                }
            }
            PrimaryButton("এই সদস্যকে যোগ করুন", !vm.busy) { vm.addCharge(chMember, chMonth, chCategory, chAmount, chDesc) }
            SecondaryButton("মোট পরিমাণ সব সক্রিয় সদস্যে সমান ভাগ") { vm.splitCharge(chMonth, chCategory, chAmount, chDesc) }
        }

        if (!utility) {
            SectionTitle("মিল চার্জ বসান")
            AppCard {
                Text(
                    "মিল রেট অনুযায়ী প্রতিটি সদস্যের মিলের খরচ খাতায় বসবে। মাস শেষ হলে একবারই বসানো যায়।",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                DateField("কোন মাস (মাসের যেকোনো তারিখ)", mealMonth) { mealMonth = it }
                if (isMain) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = mealForce, onCheckedChange = { mealForce = it })
                        Text("মাস শেষের আগেই বসান (মূল অ্যাডমিন)")
                    }
                    if (mealForce) Field("কারণ", mealReason, { mealReason = it })
                }
                PrimaryButton("মিল চার্জ বসান", !vm.busy) { vm.postMeal(mealMonth, mealForce, mealReason) }
            }
        }

        SectionTitle("বকেয়া স্মরণ")
        AppCard {
            Text("যাদের বাকি আছে তাদের সবাইকে বিজ্ঞপ্তি পাঠানো হবে। ১২ ঘণ্টায় একবার পাঠানো যায়।", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { vm.sendDueReminders() }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                Text("বকেয়া স্মরণ পাঠান")
            }
        }

        SectionTitle(if (utility) "সদস্যের বিদ্যুৎ ও রাঁধুনি খাতা" else "সদস্যের খাতা")
        people.forEach { b ->
            AppCard {
                Text("${b.name} (${b.code})", fontWeight = FontWeight.Bold)
                Text(
                    if (b.balanceMinor > 0) "বাকি ${Money.format(b.balanceMinor)}"
                    else if (b.balanceMinor < 0) "অগ্রিম ${Money.format(-b.balanceMinor)}"
                    else "কোনো বাকি নেই"
                )
                OutlinedButton(onClick = { vm.selectLedgerMember(b.id) }, enabled = !vm.busy) { Text("খাতা দেখুন") }
            }
        }
        if (vm.ledgerMemberId != null) {
            val who = people.firstOrNull { it.id == vm.ledgerMemberId }?.name ?: "সদস্য"
            SectionTitle("$who-এর খাতা")
            MonthBar(vm)
            val lg = vm.ledger
            if (lg != null) LedgerView(vm, lg, true, { revChargeId = it }, { revPayId = it })
        }

        SectionTitle("সাম্প্রতিক পেমেন্ট")
        if (vm.payments.isEmpty()) Text("এখনো কোনো পেমেন্ট নেই।", color = MaterialTheme.colorScheme.onSurfaceVariant)
        vm.payments.forEach { pm ->
            val who = people.firstOrNull { it.id == pm.memberId }?.name ?: "সদস্য"
            AppCard {
                Text("$who: ${Money.format(pm.amountMinor)}", fontWeight = FontWeight.Bold)
                Text("${pm.receiptNo} · ${ReceiptPdf.methodLabel(pm.method)} · ${Dates.bnDate(pm.paidDate)}", fontSize = 12.sp)
                if (pm.status == "REVERSED") StatusChip("বাতিল")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.downloadReceipt(pm.id) }, enabled = !vm.busy) { Text("রসিদ ডাউনলোড") }
                    OutlinedButton(onClick = { vm.shareReceipt(pm.id) }, enabled = !vm.busy) { Text("শেয়ার") }
                }
                if (pm.status == "ACTIVE") {
                    OutlinedButton(onClick = { revPayId = pm.id }, enabled = !vm.busy) { Text("পেমেন্ট বাতিল করুন") }
                }
            }
        }
    }

    revPayId?.let { id ->
        ReasonDialog("পেমেন্ট বাতিল", { reason -> vm.reversePayment(id, reason); revPayId = null }, { revPayId = null })
    }
    revChargeId?.let { id ->
        ReasonDialog("চার্জ বাতিল", { reason -> vm.reverseCharge(id, reason); revChargeId = null }, { revChargeId = null })
    }
}
