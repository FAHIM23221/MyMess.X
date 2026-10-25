package com.messerp.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messerp.app.data.Money
import com.messerp.app.data.Profile
import com.messerp.app.data.Role

private fun roleLabel(r: Role) = when (r) {
    Role.MEMBER -> "সদস্য"
    Role.ADMIN -> "অ্যাডমিন"
    Role.MAIN_ADMIN -> "মূল অ্যাডমিন"
}

@Composable
fun HeaderBar(title: String, subtitle: String) {
    Surface(color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(subtitle, color = MaterialTheme.colorScheme.onPrimary, fontSize = 13.sp)
            Text(title, color = MaterialTheme.colorScheme.onPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun AppScaffold(vm: AppViewModel, active: Int, title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        HeaderBar(title, subtitle)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
        BottomBar(active, { vm.go(Screen.Home) }, { vm.go(Screen.Meal) }, { vm.go(Screen.ProfileScreen) }, { vm.comingSoon() })
    }
}

@Composable
fun HomeScreen(vm: AppViewModel) {
    val p = vm.profile ?: return
    val title = when (p.role) {
        Role.MEMBER -> p.fullName
        Role.ADMIN -> "ম্যানেজার ড্যাশবোর্ড"
        Role.MAIN_ADMIN -> "নিয়ন্ত্রণ কেন্দ্র"
    }
    val subtitle = when (p.role) {
        Role.MEMBER -> "আসসালামু আলাইকুম"
        Role.ADMIN -> "অ্যাডমিন প্যানেল"
        Role.MAIN_ADMIN -> "মূল অ্যাডমিন"
    }
    AppScaffold(vm, 0, title, subtitle) {
        MessageText(vm)
        when (p.role) {
            Role.MEMBER -> MemberContent(vm, p)
            Role.ADMIN -> AdminContent(vm, p, false)
            Role.MAIN_ADMIN -> AdminContent(vm, p, true)
        }
        MyInfoCard(p)
    }
}

@Composable
fun MyInfoCard(p: Profile) {
    AppCard {
        Text("আমার তথ্য", fontWeight = FontWeight.Bold)
        KeyValue("সদস্য আইডি", p.memberCode)
        KeyValue("রোল", roleLabel(p.role))
        KeyValue("রুম / সিট", "${p.roomNo ?: "—"} / ${p.seatNo ?: "—"}")
        KeyValue("যোগদান", p.joinDate)
        KeyValue("ম্যানেজার", if (p.isCurrentManager) "হ্যাঁ" else "না")
    }
}

@Composable
fun MemberContent(vm: AppViewModel, p: Profile) {
    val soon = { vm.comingSoon() }
    val mine = vm.summary?.members?.firstOrNull { it.id == p.id }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val lg = vm.myLedger
        val bal = when {
            lg == null -> Triple("আমার ব্যালেন্স", "—", "লোড হচ্ছে")
            lg.dueMinor > 0 -> Triple("আমার বাকি", Money.format(lg.dueMinor), "পরিশোধ করুন")
            lg.advanceMinor > 0 -> Triple("অগ্রিম জমা", Money.format(lg.advanceMinor), "পরের চার্জে সমন্বয় হবে")
            else -> Triple("আমার ব্যালেন্স", "৳ " + Money.bn("0.00"), "কোনো বাকি নেই")
        }
        StatCard(bal.first, bal.second, Modifier.weight(1f), bal.third)
        StatCard("প্রতি মিলের রেট", Money.formatRate(vm.summary?.rateMinor), Modifier.weight(1f), "বাজার খরচ ÷ মোট মিল")
    }
    val st = vm.mealStatus
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (st != null) {
            StatusChip("সকাল: ${if (st.breakfast) "চালু" else "বন্ধ"}", st.breakfast)
            StatusChip("দুপুর: ${if (st.lunch) "চালু" else "বন্ধ"}", st.lunch)
            StatusChip("রাত: ${if (st.dinner) "চালু" else "বন্ধ"}", st.dinner)
        } else {
            StatusChip("আজকের মিল: লোড হচ্ছে")
        }
    }
    TileGrid(
        listOf(
            Tile("মিল অন/অফ", true) { vm.go(Screen.Meal) },
            Tile("আমার ব্যালেন্স", true) { vm.openBills() },
            Tile("চাল", false, soon),
            Tile("স্বচ্ছতা", false, soon),
            Tile("পেমেন্ট ও রসিদ", true) { vm.openBills() },
            Tile("ডিউটি", false, soon)
        )
    )
    AppCard {
        Text("সর্বশেষ নোটিশ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("নোটিশ সুবিধা পরের ধাপে আসছে।", fontWeight = FontWeight.SemiBold)
    }
}

private class Pending(val target: Profile, val role: Role?, val status: String?, val label: String)

@Composable
fun ControlRow(title: String, onClick: () -> Unit) {
    AppCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text("শীঘ্রই", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AdminContent(vm: AppViewModel, me: Profile, isMain: Boolean) {
    val soon = { vm.comingSoon() }
    var pending by remember { mutableStateOf<Pending?>(null) }
    val active = vm.members.count { it.isActive }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard("সক্রিয় সদস্য", "$active", Modifier.weight(1f))
        StatCard("মোট সদস্য", "${vm.members.size}", Modifier.weight(1f))
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard("এ মাসের বাজার খরচ", vm.summary?.let { Money.format(it.totalExpenseMinor) } ?: "—", Modifier.weight(1f))
        StatCard("মোট বকেয়া", vm.dues?.let { Money.format(it.totalDueMinor) } ?: "—", Modifier.weight(1f))
    }

    SectionTitle("দ্রুত কাজ")
    val tiles = mutableListOf(Tile("নতুন সদস্যের কোড", !vm.busy) { vm.createInvite(Role.MEMBER) })
    if (isMain) tiles.add(Tile("নতুন অ্যাডমিনের কোড", !vm.busy) { vm.createInvite(Role.ADMIN) })
    tiles.add(Tile("পেমেন্ট ও চার্জ", true) { vm.go(Screen.Finance) })
    tiles.add(Tile("আমার ব্যালেন্স", true) { vm.openBills() })
    tiles.add(Tile("বাজার খরচ ও গেস্ট মিল", true) { vm.go(Screen.Expenses) })
    tiles.add(Tile("মিল ও রেট", true) { vm.go(Screen.Meal) })
    tiles.add(Tile("নোটিশ দিন", false, soon))
    TileGrid(tiles)

    vm.lastInvite?.let { code ->
        AppCard {
            Text("সর্বশেষ ইনভাইট কোড", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(code, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("৭ দিন বৈধ, একবারই ব্যবহার করা যাবে।", fontSize = 12.sp)
        }
    }

    if (isMain) {
        AppCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("লিডারবোর্ড", fontWeight = FontWeight.Bold)
                    Text(if (vm.leaderboard) "চালু" else "বন্ধ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = vm.leaderboard, onCheckedChange = { vm.toggleLeaderboard(it) }, enabled = !vm.busy)
            }
        }
        SectionTitle("নিয়ন্ত্রণ")
        ControlRow("অ্যাডমিনের অনুমতি", soon)
        ControlRow("বকেয়ার সীমা ও মিল নীতি", soon)
        ControlRow("ম্যানেজার রোটেশন", soon)
        ControlRow("অডিট লগ", soon)
        ControlRow("CSV রিপোর্ট", soon)
        ControlRow("নিরাপত্তা সেটিংস", soon)
    } else {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(12.dp)) {
            Text(
                "অ্যাডমিন রোল বদল ও নিরাপত্তা সেটিংস শুধু মূল অ্যাডমিন করতে পারেন।",
                modifier = Modifier.padding(12.dp),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }

    SectionTitle("সদস্য তালিকা")
    vm.members.forEach { m ->
        AppCard {
            Text("${m.fullName} (${m.memberCode})", fontWeight = FontWeight.Bold)
            Text(m.phone, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                StatusChip(roleLabel(m.role), positive = m.role != Role.MEMBER)
                StatusChip(if (m.isActive) "সক্রিয়" else "স্থগিত", positive = m.isActive)
            }
            val canStatus = m.id != me.id && m.role != Role.MAIN_ADMIN && (isMain || m.role == Role.MEMBER)
            val canRole = isMain && m.id != me.id && m.role != Role.MAIN_ADMIN
            if (canStatus || canRole) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canRole) {
                        val target = if (m.role == Role.MEMBER) Role.ADMIN else Role.MEMBER
                        OutlinedButton(onClick = { pending = Pending(m, target, null, "রোল: ${roleLabel(target)}") }) {
                            Text(if (target == Role.ADMIN) "অ্যাডমিন করুন" else "সদস্য করুন")
                        }
                    }
                    if (canStatus) {
                        val next = if (m.isActive) "SUSPENDED" else "ACTIVE"
                        OutlinedButton(onClick = { pending = Pending(m, null, next, if (m.isActive) "স্থগিত করুন" else "সক্রিয় করুন") }) {
                            Text(if (m.isActive) "স্থগিত করুন" else "সক্রিয় করুন")
                        }
                    }
                }
            }
        }
    }

    pending?.let { act ->
        var reason by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(act.label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${act.target.fullName}-এর জন্য কারণ লিখুন। এটি অডিট লগে যাবে।")
                    OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("কারণ") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (act.role != null) vm.changeRole(act.target, act.role, reason)
                    else if (act.status != null) vm.changeStatus(act.target, act.status, reason)
                    pending = null
                }) { Text("নিশ্চিত করুন") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("বাতিল") } }
        )
    }
}

@Composable
fun ProfileScreen(vm: AppViewModel) {
    val p = vm.profile ?: return
    var name by rememberSaveable(p.id) { mutableStateOf(p.fullName) }
    AppScaffold(vm, 3, "প্রোফাইল", "আমার তথ্য") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    p.fullName.take(1).uppercase(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Column {
                Text(p.fullName, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("সদস্য আইডি: ${p.memberCode}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AppCard {
            KeyValue("ফোন", p.phone)
            KeyValue("রুম নং", p.roomNo ?: "—")
            KeyValue("সিট নং", p.seatNo ?: "—")
            KeyValue("যোগদান", p.joinDate)
            KeyValue("অবস্থা", if (p.isActive) "সক্রিয়" else "স্থগিত")
            KeyValue("রোল", roleLabel(p.role))
            KeyValue("বর্তমান ম্যানেজার", if (p.isCurrentManager) "হ্যাঁ" else "না")
        }
        Field("নাম", name, { name = it })
        MessageText(vm)
        PrimaryButton("নাম সংরক্ষণ", !vm.busy) { vm.saveName(name) }
        SectionTitle("সেটিংস")
        ControlRow("ভাষা (বাংলা / English)") { vm.comingSoon() }
        ControlRow("থিম") { vm.comingSoon() }
        OutlinedButton(
            onClick = { vm.logout() },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth()
        ) { Text("লগআউট", fontWeight = FontWeight.Bold) }
    }
}
