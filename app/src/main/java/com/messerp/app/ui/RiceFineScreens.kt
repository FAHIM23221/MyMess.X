package com.messerp.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messerp.app.data.Dates
import com.messerp.app.data.Fine
import com.messerp.app.data.Money
import com.messerp.app.data.ReceiptPdf
import com.messerp.app.data.Role
import com.messerp.app.data.Weight

private fun entryLabel(k: String) = when (k) {
    "SUBMITTED" -> "জমা"
    "ADJUSTMENT" -> "সংশোধন"
    else -> "বাতিল এন্ট্রি"
}

fun fineStatusLabel(s: String) = when (s) {
    "PAID" -> "পরিশোধিত"
    "PARTIAL" -> "আংশিক"
    "VOIDED" -> "বাতিল"
    else -> "অনাদায়ী"
}

@Composable
fun RiceScreen(vm: AppViewModel) {
    val p = vm.profile ?: return
    val isAdmin = p.role != Role.MEMBER
    val isMain = p.role == Role.MAIN_ADMIN
    val canManage = isAdmin || vm.isMealMgr
    val today = Dates.todayDhaka()
    var member by rememberSaveable(vm.formVersion) { mutableStateOf("") }
    var kg by rememberSaveable(vm.formVersion) { mutableStateOf("") }
    var date by rememberSaveable(vm.formVersion) { mutableStateOf(today) }
    var note by rememberSaveable(vm.formVersion) { mutableStateOf("") }
    var adjKg by rememberSaveable(vm.formVersion) { mutableStateOf("") }
    var reqKg by rememberSaveable(vm.formVersion) { mutableStateOf("") }
    var policyKg by rememberSaveable(vm.riceSummary?.defaultRequired) { mutableStateOf(Weight.plain(vm.riceSummary?.defaultRequired ?: 0L)) }
    var reverseFor by remember { mutableStateOf<String?>(null) }
    var adjustDialog by rememberSaveable { mutableStateOf(false) }
    var requirementDialog by rememberSaveable { mutableStateOf(false) }
    var policyDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.loadRice() }
    val s = vm.riceSummary
    val mine = s?.members?.firstOrNull { it.id == p.id }

    AppScaffold(vm, 0, "চালের হিসাব", "এককে গ্রাম, দেখানো হয় কেজিতে") {
        MessageText(vm)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = { vm.shiftRiceMonth(-1) }, enabled = !vm.busy) { Text("‹") }
            Text(if (vm.riceMonth.isEmpty()) "চলতি মাস" else Dates.bnMonth(vm.riceMonth), fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = { vm.shiftRiceMonth(1) }, enabled = !vm.busy) { Text("›") }
        }
        if (s == null) {
            Text("লোড হচ্ছে...")
        } else {
            AppCard {
                Text("মেসের মোট (এ মাস)", fontWeight = FontWeight.Bold)
                KeyValue("প্রয়োজন", Weight.format(s.totalRequired))
                KeyValue("জমা হয়েছে", Weight.format(s.totalSubmitted))
                KeyValue("বাকি", Weight.format(s.totalDue))
                KeyValue("বাড়তি", Weight.format(s.totalExtra))
                Text(
                    "প্রতি সদস্যের নির্ধারিত পরিমাণ: " + (if (s.defaultRequired > 0) Weight.format(s.defaultRequired) else "ঠিক করা হয়নি"),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (mine != null) {
                AppCard {
                    Text("আমার চাল (এ মাস)", fontWeight = FontWeight.Bold)
                    KeyValue("দেওয়ার কথা", Weight.format(mine.required))
                    KeyValue("জমা দিয়েছি", Weight.format(mine.submitted))
                    KeyValue("বাকি", Weight.format(mine.due))
                    KeyValue("বাড়তি", Weight.format(mine.extra))
                }
            }
            vm.memberRice?.let { mr ->
                if (!canManage || vm.riceMember == null || vm.riceMember == p.id) {
                    SectionTitle("আমার এন্ট্রি")
                    if (mr.entries.isEmpty()) Text("এ মাসে কোনো এন্ট্রি নেই।", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    mr.entries.forEach { e ->
                        AppCard {
                            Text(entryLabel(e.kind) + ": " + Weight.format(e.grams) + (if (e.reversed) " (বাতিল)" else ""), fontWeight = FontWeight.Bold)
                            Text(Dates.bnDate(e.date) + (if (e.byName != null) " · " + e.byName else ""), fontSize = 12.sp)
                            if (!e.note.isNullOrBlank()) Text(e.note, fontSize = 12.sp)
                        }
                    }
                }
            }

            if (canManage) {
                SectionTitle("সদস্যদের চাল")
                s.members.forEach { m ->
                    AppCard {
                        Text("${m.name} (${m.code})", fontWeight = FontWeight.Bold)
                        KeyValue("দেওয়ার কথা", Weight.format(m.required))
                        KeyValue("জমা", Weight.format(m.submitted))
                        KeyValue("বাকি / বাড়তি", Weight.format(m.due) + " / " + Weight.format(m.extra))
                        OutlinedButton(onClick = { member = m.id; vm.selectRiceMember(m.id) }, enabled = !vm.busy) { Text("এন্ট্রি দেখুন") }
                    }
                }
                val sel = vm.riceMember
                val mr = vm.memberRice
                if (sel != null && sel != p.id && mr != null) {
                    SectionTitle((s.members.firstOrNull { it.id == sel }?.name ?: "সদস্য") + "-এর এন্ট্রি")
                    if (mr.entries.isEmpty()) Text("এ মাসে কোনো এন্ট্রি নেই।", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    mr.entries.forEach { e ->
                        AppCard {
                            Text(entryLabel(e.kind) + ": " + Weight.format(e.grams) + (if (e.reversed) " (বাতিল)" else ""), fontWeight = FontWeight.Bold)
                            Text(Dates.bnDate(e.date) + (if (e.byName != null) " · রেকর্ড: " + e.byName else ""), fontSize = 12.sp)
                            if (!e.note.isNullOrBlank()) Text(e.note, fontSize = 12.sp)
                            if (!e.reversed && e.kind != "REVERSAL") {
                                OutlinedButton(onClick = { reverseFor = e.id }, enabled = !vm.busy) { Text("বাতিল করুন") }
                            }
                        }
                    }
                    if (isAdmin) {
                        AppCard {
                            Text("সংশোধন ও নির্ধারিত পরিমাণ", fontWeight = FontWeight.Bold)
                            Field("সংশোধন (কেজি, কমাতে - দিন)", adjKg, { adjKg = it }, KeyboardType.Text, hint = "যেমন -1 বা 0.5")
                            OutlinedButton(onClick = { adjustDialog = true }, enabled = !vm.busy) { Text("সংশোধন সংরক্ষণ") }
                            Field("এ মাসে এই সদস্যের নির্ধারিত পরিমাণ (কেজি)", reqKg, { reqKg = it }, KeyboardType.Decimal, hint = "যেমন 8")
                            OutlinedButton(onClick = { requirementDialog = true }, enabled = !vm.busy) { Text("নির্ধারিত পরিমাণ বদলান") }
                        }
                    }
                }

                SectionTitle("চাল জমা নিন")
                AppCard {
                    Text("সদস্য", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    s.members.filter { isAdmin || it.id != p.id }.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 2.dp)) {
                            row.forEach { m -> ChoiceChip(m.name, member == m.id) { member = m.id } }
                        }
                    }
                    Field("পরিমাণ (কেজি)", kg, { kg = it }, KeyboardType.Decimal, hint = "যেমন 2.5")
                    DateField("তারিখ", date) { date = it }
                    Field("নোট (ঐচ্ছিক)", note, { note = it })
                    PrimaryButton("জমা সংরক্ষণ", !vm.busy) { vm.recordRice(member, kg, date, note) }
                    Text("ভুল হলে এন্ট্রি বাতিল করে নতুন দিতে হবে। নিজের চাল নিজে জমা নেওয়া যায় না।", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (isMain) {
                SectionTitle("মূল নীতি")
                AppCard {
                    Field("প্রতি সদস্য প্রতি মাসে (কেজি)", policyKg, { policyKg = it }, KeyboardType.Decimal, hint = "যেমন 15, ০ হলে চাল লাগে না")
                    OutlinedButton(onClick = { policyDialog = true }, enabled = !vm.busy) { Text("নীতি সংরক্ষণ") }
                }
            }
            Text(
                "চালের বাকি টাকার বকেয়ার সঙ্গে যোগ হয় না, আলাদা দেখানো হয়। একক: ১ কেজি = ১০০০ গ্রাম।",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    reverseFor?.let { id -> ReasonDialog("এন্ট্রি বাতিল", { reason -> vm.reverseRice(id, reason); reverseFor = null }, { reverseFor = null }) }
    if (adjustDialog) ReasonDialog("সংশোধনের কারণ", { reason -> vm.adjustRice(vm.riceMember ?: member, adjKg, reason); adjustDialog = false }, { adjustDialog = false })
    if (requirementDialog) ReasonDialog("পরিমাণ বদলের কারণ", { reason -> vm.saveRiceRequirement(vm.riceMember ?: member, reqKg, reason); requirementDialog = false }, { requirementDialog = false })
    if (policyDialog) ReasonDialog("নীতি বদলের কারণ", { reason -> vm.saveRicePolicy(policyKg, reason); policyDialog = false }, { policyDialog = false })
}

@Composable
fun FinesScreen(vm: AppViewModel) {
    val p = vm.profile ?: return
    val isAdmin = p.role != Role.MEMBER
    val today = Dates.todayDhaka()
    var member by rememberSaveable(vm.formVersion) { mutableStateOf("") }
    var amount by rememberSaveable(vm.formVersion) { mutableStateOf("") }
    var reason by rememberSaveable(vm.formVersion) { mutableStateOf("") }
    var date by rememberSaveable(vm.formVersion) { mutableStateOf(today) }
    var payFor by remember { mutableStateOf<Fine?>(null) }
    var payAmount by rememberSaveable { mutableStateOf("") }
    var payMethod by rememberSaveable { mutableStateOf("CASH") }
    var payTxn by rememberSaveable { mutableStateOf("") }
    var voidFor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { vm.loadFines() }

    AppScaffold(vm, 0, "জরিমানা", if (isAdmin) "অ্যাডমিন" else "আমার জরিমানা") {
        MessageText(vm)
        if (isAdmin) {
            vm.lastPaymentId?.let { pid ->
                AppCard {
                    Text("সর্বশেষ রসিদ: ${vm.lastReceiptNo ?: ""}", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.downloadReceipt(pid) }, enabled = !vm.busy) { Text("রসিদ ডাউনলোড") }
                        OutlinedButton(onClick = { vm.shareReceipt(pid) }, enabled = !vm.busy) { Text("শেয়ার") }
                    }
                }
            }
            SectionTitle("নতুন জরিমানা")
            AppCard {
                Text("সদস্য", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                vm.members.filter { it.role == Role.MEMBER && it.isActive }.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 2.dp)) {
                        row.forEach { m -> ChoiceChip(m.fullName, member == m.id) { member = m.id } }
                    }
                }
                Field("পরিমাণ (টাকা)", amount, { amount = it }, KeyboardType.Decimal, hint = "0.00")
                Field("কারণ (বাধ্যতামূলক)", reason, { reason = it })
                DateField("তারিখ", date) { date = it }
                PrimaryButton("জরিমানা দিন", !vm.busy) { vm.addFine(member, amount, reason, date) }
                Text("জরিমানা মেসের খাতায় বকেয়া হিসেবে যোগ হয়। সদস্য বিজ্ঞপ্তি পান।", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        val list = if (isAdmin) vm.adminFines else vm.myFines
        SectionTitle(if (isAdmin) "সব জরিমানা" else "আমার জরিমানা")
        if (list.isEmpty()) Text("কোনো জরিমানা নেই।", color = MaterialTheme.colorScheme.onSurfaceVariant)
        list.forEach { f ->
            AppCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(fineStatusLabel(f.status), positive = f.status == "PAID")
                    Text(Dates.bnDate(f.date), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (isAdmin) Text(f.memberName, fontWeight = FontWeight.Bold)
                Text(f.reason, fontWeight = FontWeight.SemiBold)
                KeyValue("মোট", Money.format(f.amountMinor))
                KeyValue("পরিশোধ", Money.format(f.paidMinor))
                KeyValue("বাকি", Money.format(f.remainingMinor))
                if (isAdmin && f.recordedBy != null) Text("দিয়েছেন: " + f.recordedBy, fontSize = 12.sp)
                if (isAdmin && f.status != "VOIDED") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (f.remainingMinor > 0) {
                            OutlinedButton(onClick = {
                                payAmount = (f.remainingMinor / 100).toString() + (if (f.remainingMinor % 100 == 0L) "" else String.format(".%02d", f.remainingMinor % 100))
                                payFor = f
                            }, enabled = !vm.busy) { Text("পরিশোধ নিন") }
                        }
                        OutlinedButton(onClick = { voidFor = f.id }, enabled = !vm.busy) { Text("বাতিল") }
                    }
                }
            }
        }
        Text(
            "পরিশোধে মেসের খাতায় এন্ট্রি ও রসিদ তৈরি হয়। পেমেন্ট বাতিল হলে জরিমানার অবস্থা নিজে ফিরে যায়।",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    voidFor?.let { id -> ReasonDialog("জরিমানা বাতিল", { r -> vm.voidFine(id, r); voidFor = null }, { voidFor = null }) }
    payFor?.let { f ->
        AlertDialog(
            onDismissRequest = { payFor = null },
            title = { Text("পরিশোধ নিন: " + f.memberName) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = payAmount, onValueChange = { payAmount = it }, label = { Text("পরিমাণ (টাকা)") })
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("CASH", "BKASH", "NAGAD").forEach { m -> ChoiceChip(ReceiptPdf.methodLabel(m), payMethod == m) { payMethod = m } }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("BANK", "OTHER").forEach { m -> ChoiceChip(ReceiptPdf.methodLabel(m), payMethod == m) { payMethod = m } }
                    }
                    OutlinedTextField(value = payTxn, onValueChange = { payTxn = it }, label = { Text("ট্রানজ্যাকশন রেফারেন্স (ঐচ্ছিক)") })
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.payFine(f.id, payAmount, payMethod, today, "", payTxn); payFor = null }) { Text("নিশ্চিত করুন") }
            },
            dismissButton = { TextButton(onClick = { payFor = null }) { Text("বাতিল") } }
        )
    }
}
