package com.messerp.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messerp.app.data.Money
import com.messerp.app.data.Notifier
import com.messerp.app.data.ReceiptPdf
import com.messerp.app.data.Role

private fun levelLabel(l: String) = when (l) {
    "IMPORTANT" -> "গুরুত্বপূর্ণ"
    "EMERGENCY" -> "জরুরি"
    else -> "সাধারণ"
}

private fun audienceLabel(a: String) = when (a) {
    "ADMINS" -> "শুধু অ্যাডমিন"
    "EVERYONE" -> "সবাই"
    else -> "সব সদস্য"
}

@Composable
fun NotificationsScreen(vm: AppViewModel) {
    val p = vm.profile ?: return
    val isAdmin = p.role != Role.MEMBER
    var title by rememberSaveable(vm.formVersion) { mutableStateOf("") }
    var body by rememberSaveable(vm.formVersion) { mutableStateOf("") }
    var level by rememberSaveable(vm.formVersion) { mutableStateOf("NORMAL") }
    var audience by rememberSaveable(vm.formVersion) { mutableStateOf("MEMBERS") }
    val context = LocalContext.current

    LaunchedEffect(Unit) { vm.loadNotifications() }

    AppScaffold(vm, 2, "নোটিশ ও বিজ্ঞপ্তি", "আমার বিজ্ঞপ্তি") {
        MessageText(vm)
        if (!Notifier.canPost(context)) {
            AppCard {
                Text(
                    "ফোনে এই অ্যাপের নোটিফিকেশন বন্ধ আছে। ফোনের সেটিংসে চালু করলে নতুন বিজ্ঞপ্তি ফোনের ওপরে দেখাবে। অ্যাপের ভেতরে এখানে সবসময় দেখা যাবে।",
                    fontSize = 13.sp
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("অপঠিত: " + Money.bn(vm.unreadCount.toLong()), fontWeight = FontWeight.Bold)
            if (vm.unreadCount > 0) {
                OutlinedButton(onClick = { vm.markAllRead() }, enabled = !vm.busy) { Text("সব পড়া হয়েছে") }
            }
        }
        if (vm.notifications.isEmpty()) {
            Text("এখনো কোনো বিজ্ঞপ্তি নেই।", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        vm.notifications.forEach { n ->
            AppCard(modifier = Modifier.clickable { if (!n.read) vm.markRead(n.id) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(levelLabel(n.level), positive = !n.read)
                    Text(ReceiptPdf.formatLocal(n.createdLocal), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    Money.bn(n.title),
                    fontWeight = if (n.read) FontWeight.Normal else FontWeight.Bold
                )
                Text(Money.bn(n.body), fontSize = 13.sp)
            }
        }

        if (isAdmin) {
            SectionTitle("নতুন নোটিশ দিন")
            AppCard {
                Field("শিরোনাম", title, { title = it })
                Field("বিবরণ", body, { body = it }, singleLine = false)
                Text("গুরুত্ব", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("NORMAL", "IMPORTANT", "EMERGENCY").forEach { l -> ChoiceChip(levelLabel(l), level == l) { level = l } }
                }
                Text("কারা পাবে", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("MEMBERS", "ADMINS", "EVERYONE").forEach { a -> ChoiceChip(audienceLabel(a), audience == a) { audience = a } }
                }
                PrimaryButton("নোটিশ পাঠান", !vm.busy) { vm.createNotice(title, body, level, audience) }
            }
            SectionTitle("পাঠানো নোটিশের ইতিহাস")
            if (vm.noticeHistory.isEmpty()) Text("এখনো কোনো নোটিশ পাঠানো হয়নি।", color = MaterialTheme.colorScheme.onSurfaceVariant)
            vm.noticeHistory.forEach { h ->
                AppCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusChip(levelLabel(h.level))
                        Text(audienceLabel(h.audience), fontSize = 12.sp)
                    }
                    Text(h.title, fontWeight = FontWeight.Bold)
                    Text(ReceiptPdf.formatLocal(h.createdLocal), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "প্রাপক " + Money.bn(h.recipients.toLong()) + " · ফোনে পাঠানো " + Money.bn(h.delivered.toLong()) +
                            " · পড়েছেন " + Money.bn(h.readCount.toLong()),
                        fontSize = 13.sp
                    )
                }
            }
            Text(
                "\"ফোনে পাঠানো\" মানে ওই সদস্যের ফোন বিজ্ঞপ্তিটি দেখানোর জন্য নিয়েছে। ফোন বন্ধ বা অফলাইন থাকলে দেরি হতে পারে।",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
