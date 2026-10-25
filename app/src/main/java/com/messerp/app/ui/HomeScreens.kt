package com.messerp.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.unit.dp
import com.messerp.app.data.Profile
import com.messerp.app.data.Role

private fun roleLabel(r: Role) = when (r) {
    Role.MEMBER -> "সদস্য"
    Role.ADMIN -> "অ্যাডমিন"
    Role.MAIN_ADMIN -> "মূল অ্যাডমিন"
}

@Composable
fun HeaderBar(title: String, subtitle: String, onProfile: (() -> Unit)? = null) {
    Surface(color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(subtitle, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.bodySmall)
                Text(title, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.titleLarge)
            }
            if (onProfile != null) {
                TextButton(onClick = onProfile) { Text("প্রোফাইল", color = MaterialTheme.colorScheme.onPrimary) }
            }
        }
    }
}

@Composable
fun InfoCard(title: String, lines: List<String>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            lines.forEach { Text(it) }
        }
    }
}

@Composable
fun HomeScreen(vm: AppViewModel) {
    val p = vm.profile ?: return
    Column(modifier = Modifier.fillMaxSize()) {
        val title = when (p.role) {
            Role.MEMBER -> p.fullName
            Role.ADMIN -> "অ্যাডমিন ড্যাশবোর্ড"
            Role.MAIN_ADMIN -> "মূল অ্যাডমিন ড্যাশবোর্ড"
        }
        HeaderBar(title, "আসসালামু আলাইকুম, ${p.fullName}") { vm.go(Screen.ProfileScreen) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MessageText(vm)
            InfoCard(
                "আমার তথ্য",
                listOf(
                    "সদস্য আইডি: ${p.memberCode}",
                    "রোল: ${roleLabel(p.role)}",
                    "রুম: ${p.roomNo ?: "নির্ধারিত নয়"}, সিট: ${p.seatNo ?: "নির্ধারিত নয়"}",
                    "যোগদান: ${p.joinDate}",
                    if (p.isCurrentManager) "আপনি বর্তমান ম্যানেজার" else "বর্তমান ম্যানেজার নন"
                )
            )
            InfoCard(
                "আসছে পরের ধাপে",
                listOf("ব্যালেন্স ও বাকি, মিল ও চাল, বিল ও রসিদ, ডিউটি, নোটিশ ও চ্যাট। এই ধাপে এগুলোর কোনো সংখ্যা দেখানো হচ্ছে না।")
            )
            if (p.role != Role.MEMBER) AdminPanel(vm, p)
        }
    }
}

private class Pending(val target: Profile, val role: Role?, val status: String?, val label: String)

@Composable
fun AdminPanel(vm: AppViewModel, me: Profile) {
    val isMain = me.role == Role.MAIN_ADMIN
    var pending by remember { mutableStateOf<Pending?>(null) }

    InfoCard("সারাংশ", listOf("মোট সদস্য: ${vm.members.size}"))

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ইনভাইট কোড", style = MaterialTheme.typography.titleMedium)
            vm.lastInvite?.let { Text("সর্বশেষ কোড: $it", style = MaterialTheme.typography.titleMedium) }
            Button(onClick = { vm.createInvite(Role.MEMBER) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                Text("নতুন সদস্যের কোড তৈরি")
            }
            if (isMain) {
                OutlinedButton(onClick = { vm.createInvite(Role.ADMIN) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                    Text("নতুন অ্যাডমিনের কোড তৈরি")
                }
            }
        }
    }

    if (isMain) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("লিডারবোর্ড", style = MaterialTheme.typography.titleMedium)
                    Text(if (vm.leaderboard) "চালু" else "বন্ধ")
                }
                Switch(checked = vm.leaderboard, onCheckedChange = { vm.toggleLeaderboard(it) }, enabled = !vm.busy)
            }
        }
    }

    Text("সদস্য তালিকা", style = MaterialTheme.typography.titleMedium)
    vm.members.forEach { m ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${m.fullName} (${m.memberCode})", style = MaterialTheme.typography.titleSmall)
                Text("${roleLabel(m.role)} · ${if (m.isActive) "সক্রিয়" else "স্থগিত"} · ${m.phone}")
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
                            OutlinedButton(onClick = { pending = Pending(m, null, next, if (m.isActive) "স্থগিত" else "সক্রিয়") }) {
                                Text(if (m.isActive) "স্থগিত করুন" else "সক্রিয় করুন")
                            }
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
    Column(modifier = Modifier.fillMaxSize()) {
        HeaderBar("প্রোফাইল", p.memberCode)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfoCard(
                "তথ্য",
                listOf(
                    "ফোন: ${p.phone}",
                    "রোল: ${roleLabel(p.role)}",
                    "অবস্থা: ${if (p.isActive) "সক্রিয়" else "স্থগিত"}",
                    "রুম: ${p.roomNo ?: "নির্ধারিত নয়"}, সিট: ${p.seatNo ?: "নির্ধারিত নয়"}",
                    "যোগদান: ${p.joinDate}"
                )
            )
            Field("নাম", name, { name = it })
            MessageText(vm)
            Button(onClick = { vm.saveName(name) }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("নাম সংরক্ষণ") }
            OutlinedButton(onClick = { vm.go(Screen.Home) }, modifier = Modifier.fillMaxWidth()) { Text("ফিরে যান") }
            OutlinedButton(onClick = { vm.logout() }, modifier = Modifier.fillMaxWidth()) { Text("লগআউট") }
        }
    }
}
