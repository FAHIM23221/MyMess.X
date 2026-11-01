package com.messerp.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messerp.app.data.Dates
import com.messerp.app.data.Money
import com.messerp.app.data.ReceiptPdf
import com.messerp.app.data.TrendPoint

private fun expLabel(c: String) = when (c) {
    "MARKET" -> "বাজার"
    "RICE" -> "চাল"
    "GAS" -> "গ্যাস"
    "ELECTRICITY" -> "বিদ্যুৎ বিল"
    "CHEF" -> "রাঁধুনি বিল"
    "WIFI" -> "ওয়াই-ফাই"
    "WATER" -> "পানি"
    "REPAIR" -> "মেরামত"
    "CLEANING" -> "পরিষ্কার"
    "PAYMENT" -> "পেমেন্ট"
    else -> "অন্যান্য"
}

/** A negative balance is shown as a shortfall, never silently turned into zero. */
private fun signedBalance(minor: Long): String = if (minor < 0) "ঘাটতি " + Money.format(-minor) else Money.format(minor)

@Composable
fun TpBookSwitch(vm: AppViewModel) {
    val utility = vm.tpBook == "UTILITY"
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceChip("মেস খাতা", !utility) { if (utility) vm.switchTpBook("MESS") }
        ChoiceChip("বিদ্যুৎ ও রাঁধুনি বিল", utility) { if (!utility) vm.switchTpBook("UTILITY") }
    }
}

@Composable
fun TpMonthBar(vm: AppViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(onClick = { vm.shiftTpMonth(-1) }, enabled = !vm.busy) { Text("‹") }
        Text(if (vm.tpMonth.isEmpty()) "চলতি মাস" else Dates.bnMonth(vm.tpMonth), fontWeight = FontWeight.Bold)
        OutlinedButton(onClick = { vm.shiftTpMonth(1) }, enabled = !vm.busy) { Text("›") }
    }
}

@Composable
private fun TrendBars(points: List<TrendPoint>) {
    val maxV = (points.maxOfOrNull { maxOf(it.collectedMinor, it.expenseMinor) } ?: 0L).coerceAtLeast(1L)
    val c = MaterialTheme.colorScheme
    points.forEach { pt ->
        Column(modifier = Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(Dates.bnMonth(pt.month), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((pt.collectedMinor.coerceAtLeast(0L).toFloat() / maxV).coerceIn(0.01f, 1f))
                        .height(8.dp)
                        .background(c.primary)
                )
            }
            Text("সংগ্রহ " + Money.format(pt.collectedMinor), fontSize = 11.sp, color = c.onSurfaceVariant)
            Box(
                modifier = Modifier
                    .fillMaxWidth((pt.expenseMinor.coerceAtLeast(0L).toFloat() / maxV).coerceIn(0.01f, 1f))
                    .height(8.dp)
                    .background(c.error)
            )
            Text("খরচ " + Money.format(pt.expenseMinor), fontSize = 11.sp, color = c.onSurfaceVariant)
        }
    }
}

@Composable
fun TransparencyScreen(vm: AppViewModel) {
    val t = vm.transparency
    val utility = vm.tpBook == "UTILITY"
    LaunchedEffect(Unit) { vm.loadTransparency() }

    AppScaffold(vm, 0, "স্বচ্ছতা", if (utility) "বিদ্যুৎ ও রাঁধুনি বিলের খাতা" else "মেসের আর্থিক তথ্য") {
        MessageText(vm)
        TpBookSwitch(vm)
        TpMonthBar(vm)
        if (t == null) {
            Text("লোড হচ্ছে...")
        } else {
            Text(
                "সময়কাল: " + Dates.bnDate(t.monthStart) + " থেকে " + Dates.bnDate(t.through) +
                    " · সর্বশেষ হালনাগাদ: " + ReceiptPdf.formatLocal(t.updatedLocal),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("এ মাসের সংগ্রহ", Money.format(t.collectedMinor), Modifier.weight(1f))
                StatCard("এ মাসের খরচ", Money.format(t.expenseMinor), Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("আগের মাসের জের", signedBalance(t.openingMinor), Modifier.weight(1f))
                StatCard("মাস শেষে ব্যালেন্স", signedBalance(t.closingMinor), Modifier.weight(1f), "হিসাব অনুযায়ী")
            }

            SectionTitle("সংগ্রহ কীভাবে এসেছে (এ মাস)")
            AppCard {
                KeyValue("হাতে নগদ", Money.format(t.cashMinor))
                KeyValue("ডিজিটাল (বিকাশ, Nagad, ব্যাংক ও অন্যান্য)", Money.format(t.digitalMinor))
                t.byMethod.forEach { m -> KeyValue("   " + ReceiptPdf.methodLabel(m.method), Money.format(m.amountMinor)) }
            }

            SectionTitle("সদস্যদের কাছে পাওনা")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("মোট বাকি", Money.format(t.totalDueMinor), Modifier.weight(1f), "খাতার হিসাব")
                StatCard("মোট অগ্রিম জমা", Money.format(t.totalAdvanceMinor), Modifier.weight(1f), "বাকির সঙ্গে যোগ হয় না")
            }
            if (!utility) {
                StatCard("এ মাসের জরিমানা", Money.format(t.finesMinor), Modifier.fillMaxWidth())
                Text("চালের হিসাব পরের ধাপে আসছে।", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionTitle("খরচ বিভাগ অনুযায়ী (এ মাস)")
            if (t.byCategory.isEmpty()) Text("এ মাসে কোনো খরচ নেই।", color = MaterialTheme.colorScheme.onSurfaceVariant)
            AppCard {
                t.byCategory.forEach { c -> KeyValue(expLabel(c.category), Money.format(c.amountMinor)) }
            }

            SectionTitle("গত ৬ মাসের ধারা")
            AppCard { TrendBars(t.trend) }

            SectionTitle("ব্যক্তিভেদে সংগ্রহ ও খরচ (শুরু থেকে এ মাস পর্যন্ত)")
            AppCard {
                if (t.held.isEmpty()) Text("এখনো কোনো লেনদেন নেই।", color = MaterialTheme.colorScheme.onSurfaceVariant)
                t.held.forEach { h ->
                    Text(h.name, fontWeight = FontWeight.Bold)
                    KeyValue("সংগ্রহ করেছেন", Money.format(h.receivedMinor))
                    KeyValue("খরচ লিখেছেন", Money.format(h.spentMinor))
                    KeyValue("সংগ্রহ − খরচ", signedBalance(h.netMinor))
                }
            }

            SectionTitle("সাম্প্রতিক খরচ")
            if (t.recent.isEmpty()) Text("এখনো কোনো খরচ নেই।", color = MaterialTheme.colorScheme.onSurfaceVariant)
            t.recent.forEach { e ->
                AppCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusChip(expLabel(e.category), positive = true)
                        Text(Dates.bnDate(e.date), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(e.description, fontWeight = FontWeight.SemiBold)
                    Text(Money.format(e.amountMinor), fontWeight = FontWeight.Bold)
                    if (e.byName != null) Text("লিখেছেন: " + e.byName, fontSize = 12.sp)
                }
            }

            OutlinedButton(onClick = { vm.openCashBook() }, modifier = Modifier.fillMaxWidth()) { Text("ক্যাশ বুক দেখুন") }

            AppCard {
                Text("হিসাবের সংজ্ঞা", fontWeight = FontWeight.Bold)
                Text(
                    "• সংগ্রহ = গৃহীত পেমেন্ট − বাতিল পেমেন্ট।\n" +
                        "• খরচ = লেখা খরচ − বাতিল খরচ।\n" +
                        "• ব্যালেন্স = আগের জের + সংগ্রহ − খরচ। এটা খাতার হিসাব, সবার হাতে থাকা প্রকৃত টাকা গুনে মেলানো নয়।\n" +
                        "• বাকি ও অগ্রিম সদস্যদের খাতার হিসাব। একই টাকা দুবার ধরা এড়াতে এগুলো ব্যালেন্সের সঙ্গে যোগ করা হয় না।\n" +
                        "• ব্যক্তিভেদে তালিকা: যিনি পেমেন্ট নিয়েছেন তাঁর নামে সংগ্রহ, যিনি খরচ লিখেছেন তাঁর নামে খরচ। টাকার হস্তান্তর এখনো রেকর্ড হয় না।\n" +
                        "• কোনো সদস্যের ব্যক্তিগত পেমেন্ট বা বাকি এখানে দেখানো হয় না।",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun CashBookScreen(vm: AppViewModel) {
    val cb = vm.cashBook
    LaunchedEffect(Unit) { vm.loadCashBook() }

    AppScaffold(vm, 0, "ক্যাশ বুক", if (vm.tpBook == "UTILITY") "বিদ্যুৎ ও রাঁধুনি বিল" else "মেসের খাতা") {
        MessageText(vm)
        TpBookSwitch(vm)
        TpMonthBar(vm)
        if (cb == null) {
            Text("লোড হচ্ছে...")
        } else {
            Text(
                "সর্বশেষ হালনাগাদ: " + ReceiptPdf.formatLocal(cb.updatedLocal),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AppCard {
                KeyValue("আগের জের", signedBalance(cb.openingMinor))
            }
            if (cb.entries.isEmpty()) Text("এ মাসে কোনো লেনদেন নেই।", color = MaterialTheme.colorScheme.onSurfaceVariant)
            cb.entries.forEach { e ->
                AppCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusChip(expLabel(e.category), positive = e.incomeMinor > 0)
                        Text(ReceiptPdf.formatLocal(e.atLocal), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(e.descr, fontWeight = FontWeight.SemiBold)
                    if (e.ref != null) Text("রসিদ: " + e.ref, fontSize = 12.sp)
                    if (e.memberName != null) Text("সদস্য: " + e.memberName, fontSize = 12.sp)
                    if (e.byName != null) Text("রেকর্ড করেছেন: " + e.byName, fontSize = 12.sp)
                    if (e.incomeMinor > 0) Text("+ " + Money.format(e.incomeMinor), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    if (e.expenseMinor > 0) Text("− " + Money.format(e.expenseMinor), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    Text("ব্যালেন্স: " + signedBalance(e.balanceMinor), fontSize = 12.sp)
                }
            }
            AppCard {
                KeyValue("মাস শেষে ব্যালেন্স", signedBalance(cb.closingMinor))
            }
            Text(
                "বাতিল পেমেন্ট ও বাতিল খরচ মুছে ফেলা হয় না, আলাদা সারি হিসেবে থাকে। সদস্যদের নাম শুধু অ্যাডমিন ও সংশ্লিষ্ট ম্যানেজার দেখতে পান।",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
