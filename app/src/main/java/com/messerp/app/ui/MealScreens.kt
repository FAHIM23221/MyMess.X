package com.messerp.app.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messerp.app.data.Dates
import com.messerp.app.data.Money
import com.messerp.app.data.Role

private fun slotLabel(slot: String) = when (slot) {
    "breakfast" -> "সকাল"
    "lunch" -> "দুপুর"
    else -> "রাত"
}

private fun categoryLabel(c: String) = when (c) {
    "MARKET" -> "বাজার"
    "RICE" -> "চাল"
    "GAS" -> "গ্যাস"
    "ELECTRICITY" -> "বিদ্যুৎ"
    "WIFI" -> "ওয়াই-ফাই"
    "WATER" -> "পানি"
    "REPAIR" -> "মেরামত"
    "CLEANING" -> "পরিষ্কার"
    else -> "অন্যান্য"
}

private val CATEGORIES = listOf("MARKET", "RICE", "GAS", "ELECTRICITY", "WIFI", "WATER", "REPAIR", "CLEANING", "OTHER")

@Composable
fun ReasonDialog(title: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var reason by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("কারণ (কমপক্ষে ৩ অক্ষর)") })
        },
        confirmButton = { TextButton(onClick = { onConfirm(reason) }) { Text("নিশ্চিত করুন") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("বাতিল") } }
    )
}

@Composable
fun DateField(label: String, value: String, onPick: (String) -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        OutlinedButton(
            onClick = {
                val p = value.split("-").map { it.toIntOrNull() ?: 1 }
                DatePickerDialog(context, { _, y, m, d -> onPick(Dates.format(y, m + 1, d)) }, p[0], p[1] - 1, p[2]).show()
            },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) { Text(Money.bn(value)) }
    }
}

@Composable
fun SlotCheck(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}

@Composable
fun MealScreen(vm: AppViewModel) {
    val p = vm.profile ?: return
    val isAdmin = p.role != Role.MEMBER
    val status = vm.mealStatus
    val summary = vm.summary
    val today = status?.today ?: Dates.todayDhaka()
    var from by rememberSaveable(vm.formVersion) { mutableStateOf(today) }
    var to by rememberSaveable(vm.formVersion) { mutableStateOf(today) }
    var b by rememberSaveable(vm.formVersion) { mutableStateOf(true) }
    var l by rememberSaveable(vm.formVersion) { mutableStateOf(true) }
    var d by rememberSaveable(vm.formVersion) { mutableStateOf(true) }
    var reason by rememberSaveable(vm.formVersion) { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.loadMeal() }

    AppScaffold(vm, 1, "মিল ও রেট", "এ মাসের হিসাব") {
        MessageText(vm)
        AppCard {
            Text("প্রতি মিলের রেট", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                Money.formatRate(summary?.rateMinor),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            if (summary != null) {
                Text("মোট বাজার খরচ ${Money.format(summary.totalExpenseMinor)} ÷ মোট মিল ${Money.bn(summary.totalMeals)}", fontSize = 13.sp)
                Text(
                    "${Money.bn(summary.monthStart)} থেকে ${Money.bn(summary.through)} পর্যন্ত",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!isAdmin) {
            val mine = summary?.members?.firstOrNull { it.id == p.id }
            if (mine != null) {
                AppCard {
                    Text("আমার হিসাব (এ মাস)", fontWeight = FontWeight.Bold)
                    KeyValue("মোট মিল (গেস্টসহ)", Money.bn(mine.meals))
                    KeyValue("গেস্ট মিল", Money.bn(mine.guestMeals))
                    KeyValue("মিলের খরচ", Money.format(mine.costMinor))
                }
            }
        } else if (summary != null) {
            SectionTitle("সদস্যদের মিল ও খরচ")
            summary.members.forEach { m ->
                AppCard {
                    Text("${m.name} (${m.code})", fontWeight = FontWeight.Bold)
                    KeyValue("মিল (গেস্ট ${Money.bn(m.guestMeals)})", Money.bn(m.meals))
                    KeyValue("খরচ", Money.format(m.costMinor))
                }
            }
        }

        SectionTitle("আজকের মিল")
        if (status != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip("সকাল: ${if (status.breakfast) "চালু" else "বন্ধ"}", status.breakfast)
                StatusChip("দুপুর: ${if (status.lunch) "চালু" else "বন্ধ"}", status.lunch)
                StatusChip("রাত: ${if (status.dinner) "চালু" else "বন্ধ"}", status.dinner)
            }
        }
        Text(
            "ডিফল্টে সব বেলা চালু থাকে। বন্ধ করলে ওই বেলা মিলের সংখ্যা থেকে বাদ যায় এবং শেষ তারিখের পর নিজে চালু হয়।",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SectionTitle("আমার বন্ধের তালিকা")
        if (status == null || status.periods.isEmpty()) {
            Text("কোনো বন্ধ নেই।", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            status.periods.forEach { o ->
                AppCard {
                    Text("${Money.bn(o.from)} থেকে ${Money.bn(o.to)}", fontWeight = FontWeight.Bold)
                    val slots = listOfNotNull(
                        if (o.breakfast) "সকাল" else null,
                        if (o.lunch) "দুপুর" else null,
                        if (o.dinner) "রাত" else null
                    ).joinToString(", ")
                    Text("বন্ধ: $slots", fontSize = 13.sp)
                    if (!o.reason.isNullOrBlank()) Text("কারণ: ${o.reason}", fontSize = 12.sp)
                    OutlinedButton(onClick = { vm.cancelOff(o.id) }, enabled = !vm.busy) {
                        Text(if (o.from > today) "বাতিল করুন" else "আজ থেকে চালু করুন")
                    }
                }
            }
        }

        SectionTitle("মিল বন্ধ করুন")
        AppCard {
            DateField("শুরুর তারিখ", from) { from = it }
            DateField("শেষ তারিখ (এই দিন পর্যন্ত বন্ধ)", to) { to = it }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SlotCheck("সকাল", b) { b = it }
                SlotCheck("দুপুর", l) { l = it }
                SlotCheck("রাত", d) { d = it }
            }
            Field("কারণ (ঐচ্ছিক)", reason, { reason = it })
            PrimaryButton("মিল বন্ধের অনুরোধ", !vm.busy) { vm.createOff(from, to, b, l, d, reason) }
        }
    }
}

@Composable
fun ExpensesScreen(vm: AppViewModel) {
    val p = vm.profile ?: return
    if (p.role == Role.MEMBER) return
    val today = vm.mealStatus?.today ?: Dates.todayDhaka()
    var category by rememberSaveable(vm.formVersion) { mutableStateOf("MARKET") }
    var amount by rememberSaveable(vm.formVersion) { mutableStateOf("") }
    var date by rememberSaveable(vm.formVersion) { mutableStateOf(today) }
    var desc by rememberSaveable(vm.formVersion) { mutableStateOf("") }
    var guestMember by rememberSaveable(vm.formVersion) { mutableStateOf("") }
    var guestSlot by rememberSaveable(vm.formVersion) { mutableStateOf("lunch") }
    var guestCount by rememberSaveable(vm.formVersion) { mutableStateOf("1") }
    var guestDate by rememberSaveable(vm.formVersion) { mutableStateOf(today) }
    var voidExpenseId by rememberSaveable { mutableStateOf<String?>(null) }
    var voidGuestId by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { vm.loadExpenses() }

    AppScaffold(vm, 1, "বাজার খরচ ও গেস্ট মিল", "অ্যাডমিন") {
        MessageText(vm)
        vm.summary?.let {
            StatCard("এ মাসের বাজার খরচ (মিল রেটে ধরা)", Money.format(it.totalExpenseMinor), Modifier.fillMaxWidth())
        }

        SectionTitle("খরচ যোগ করুন")
        AppCard {
            Text("বিভাগ", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            CATEGORIES.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 2.dp)) {
                    row.forEach { c -> ChoiceChip(categoryLabel(c), category == c) { category = c } }
                }
            }
            if (category != "MARKET") {
                Text("মিল রেটে শুধু 'বাজার' বিভাগের খরচ ধরা হয়।", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Field("পরিমাণ (টাকা)", amount, { amount = it }, KeyboardType.Decimal, hint = "0.00")
            DateField("তারিখ", date) { date = it }
            Field("বিবরণ", desc, { desc = it })
            PrimaryButton("খরচ সংরক্ষণ", !vm.busy) { vm.addExpense(date, category, amount, desc) }
            Text(
                "সংরক্ষণের পর সম্পাদনা নেই। ভুল হলে বাতিল করে নতুন এন্ট্রি দিন, বাতিলের কারণ রেকর্ডে থাকবে।",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionTitle("এ মাসের খরচের তালিকা")
        if (vm.expenses.isEmpty()) Text("এখনো কোনো খরচ নেই।", color = MaterialTheme.colorScheme.onSurfaceVariant)
        vm.expenses.forEach { e ->
            AppCard {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(categoryLabel(e.category), positive = !e.voided)
                    Text(Money.bn(e.date), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(e.description, fontWeight = FontWeight.SemiBold)
                Text(
                    if (e.voided) "বাতিল: ${Money.format(e.amountMinor)}" else Money.format(e.amountMinor),
                    fontWeight = FontWeight.Bold,
                    color = if (e.voided) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )
                if (e.voided) {
                    Text("কারণ: ${e.voidReason ?: "—"}", fontSize = 12.sp)
                } else {
                    OutlinedButton(onClick = { voidExpenseId = e.id }, enabled = !vm.busy) { Text("বাতিল করুন") }
                }
            }
        }

        SectionTitle("গেস্ট মিল যোগ")
        AppCard {
            Text("কার গেস্ট", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            vm.members.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 2.dp)) {
                    row.forEach { m -> ChoiceChip(m.fullName, guestMember == m.id) { guestMember = m.id } }
                }
            }
            Text("বেলা", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("breakfast", "lunch", "dinner").forEach { s -> ChoiceChip(slotLabel(s), guestSlot == s) { guestSlot = s } }
            }
            Field("গেস্ট সংখ্যা", guestCount, { guestCount = it }, KeyboardType.Number)
            DateField("তারিখ", guestDate) { guestDate = it }
            PrimaryButton("গেস্ট মিল সংরক্ষণ", !vm.busy) { vm.addGuest(guestMember, guestDate, guestSlot, guestCount) }
        }

        SectionTitle("এ মাসের গেস্ট মিল")
        if (vm.guestMeals.isEmpty()) Text("এখনো কোনো গেস্ট মিল নেই।", color = MaterialTheme.colorScheme.onSurfaceVariant)
        vm.guestMeals.forEach { g ->
            val host = vm.members.firstOrNull { it.id == g.memberId }?.fullName ?: "সদস্য"
            AppCard {
                Text("$host: ${Money.bn(g.count.toLong())} জন, ${slotLabel(g.slot)}", fontWeight = FontWeight.SemiBold)
                Text(Money.bn(g.date), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { voidGuestId = g.id }, enabled = !vm.busy) { Text("বাতিল করুন") }
            }
        }
    }

    voidExpenseId?.let { id ->
        ReasonDialog("খরচ বাতিল", { reason -> vm.voidExpense(id, reason); voidExpenseId = null }, { voidExpenseId = null })
    }
    voidGuestId?.let { id ->
        ReasonDialog("গেস্ট মিল বাতিল", { reason -> vm.voidGuest(id, reason); voidGuestId = null }, { voidGuestId = null })
    }
}
