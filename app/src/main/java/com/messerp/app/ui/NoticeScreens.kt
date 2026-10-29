package com.messerp.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messerp.app.data.AppNotification
import com.messerp.app.data.Money
import com.messerp.app.data.Notifier
import com.messerp.app.data.Push
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

private fun deptLabel(d: String) = when (d) {
    "MEAL" -> "মিল"
    "BILL" -> "বিদ্যুৎ ও রাঁধুনি"
    else -> "সাধারণ"
}

private const val MEETING = "EMERGENCY_MEETING"

/** Shown on the Home page when the app is opened and a notice was marked as a pop-up. */
@Composable
fun NoticePopup(n: AppNotification, onDismiss: () -> Unit) {
    val meeting = n.kind == "MEETING"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text((if (meeting) "জরুরি মিটিং: " else "নোটিশ: ") + Money.bn(n.title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(Money.bn(n.body))
                Text(
                    ReceiptPdf.formatLocal(n.createdLocal),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("বুঝেছি") } }
    )
}

@Composable
fun NotificationsScreen(vm: AppViewModel) {
    val p = vm.profile ?: return
    val isAdmin = p.role != Role.MEMBER
    val context = LocalContext.current
    val deptOptions = buildList {
        if (isAdmin || vm.canGeneralNotice) add("GENERAL")
        if (isAdmin || vm.isMealMgr) add("MEAL")
        if (isAdmin || vm.isBillMgr) add("BILL")
    }
    val meetingAllowed = isAdmin || vm.canMeetingNotice
    val normalAllowed = deptOptions.isNotEmpty()

    var type by rememberSaveable(vm.formVersion) { mutableStateOf(if (normalAllowed) "NORMAL" else MEETING) }
    var dept by rememberSaveable(vm.formVersion) { mutableStateOf(deptOptions.firstOrNull() ?: "GENERAL") }
    var title by rememberSaveable(vm.formVersion) { mutableStateOf("") }
    var body by rememberSaveable(vm.formVersion) { mutableStateOf("") }
    var meetTime by rememberSaveable(vm.formVersion) { mutableStateOf("") }
    var meetPlace by rememberSaveable(vm.formVersion) { mutableStateOf("") }
    var level by rememberSaveable(vm.formVersion) { mutableStateOf("NORMAL") }
    var audience by rememberSaveable(vm.formVersion) { mutableStateOf("MEMBERS") }
    var popup by rememberSaveable(vm.formVersion) { mutableStateOf(false) }
    var hText by rememberSaveable(vm.headlineText) { mutableStateOf(vm.headlineText) }
    var hOn by rememberSaveable(vm.headlineEnabled) { mutableStateOf(vm.headlineEnabled) }

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
        AppCard {
            Text("তাৎক্ষণিক পুশ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (Push.available(context)) "এই ফোনে চালু আছে" else "এই অ্যাপ সংস্করণে সেটআপ বাকি। আপাতত বিজ্ঞপ্তি সর্বোচ্চ ১৫ মিনিটে ফোনে পৌঁছায়।",
                fontWeight = FontWeight.SemiBold
            )
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
                    StatusChip(if (n.kind == "MEETING") "জরুরি মিটিং" else levelLabel(n.level), positive = !n.read)
                    if (n.dept != "GENERAL") StatusChip(deptLabel(n.dept))
                    Text(ReceiptPdf.formatLocal(n.createdLocal), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(Money.bn(n.title), fontWeight = if (n.read) FontWeight.Normal else FontWeight.Bold)
                Text(Money.bn(n.body), fontSize = 13.sp)
            }
        }

        if (isAdmin) {
            SectionTitle("স্ক্রলিং হেডলাইন (হোম পেজের উপরে)")
            AppCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("হেডলাইন", fontWeight = FontWeight.Bold)
                        Text(if (hOn) "চালু" else "বন্ধ", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = hOn, onCheckedChange = { hOn = it })
                }
                Field("হেডলাইনের লেখা (সর্বোচ্চ ২০০ অক্ষর)", hText, { hText = it })
                PrimaryButton("হেডলাইন সংরক্ষণ", !vm.busy) { vm.saveHeadline(hText, hOn) }
                Text(
                    "চালু থাকলে সবার হোম পেজের উপরে লেখাটি ডান থেকে বামে চলতে থাকবে।",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (vm.canPublish && (normalAllowed || meetingAllowed)) {
            SectionTitle("নতুন নোটিফিকেশন পাঠান")
            AppCard {
                if (!isAdmin) {
                    Text(
                        "আপনি ম্যানেজার হিসেবে শুধু সদস্যদের কাছে নোটিশ পাঠাতে পারবেন, এবং শুধু অনুমতি পাওয়া ধরনের।",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("ধরন", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (normalAllowed) ChoiceChip("সাধারণ নোটিফিকেশন", type == "NORMAL") { type = "NORMAL" }
                    if (meetingAllowed) ChoiceChip("জরুরি মিটিং", type == MEETING) { type = MEETING }
                }
                Field(if (type == MEETING) "শিরোনাম (ফাঁকা রাখলে \"জরুরি মিটিং\")" else "শিরোনাম", title, { title = it })
                if (type == MEETING) {
                    Field("মিটিংয়ের সময়", meetTime, { meetTime = it })
                    Field("স্থান", meetPlace, { meetPlace = it })
                    Field("অতিরিক্ত বিবরণ (ঐচ্ছিক)", body, { body = it }, singleLine = false)
                    Text(
                        "জরুরি মিটিং সবসময় পপ-আপ হয়। বিজ্ঞপ্তি পেলে সমর্থিত Android ফোন ৩ সেকেন্ড ভাইব্রেট করে (ফোন সাইলেন্ট বা ভাইব্রেশন বন্ধ থাকলে নাও করতে পারে)।",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    if (deptOptions.size > 1) {
                        Text("বিভাগ", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            deptOptions.forEach { d -> ChoiceChip(deptLabel(d), dept == d) { dept = d } }
                        }
                    } else {
                        Text("বিভাগ: " + deptLabel(dept), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Field("বিবরণ", body, { body = it }, singleLine = false)
                    Text("গুরুত্ব", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val levels = if (isAdmin) listOf("NORMAL", "IMPORTANT", "EMERGENCY") else listOf("NORMAL", "IMPORTANT")
                        levels.forEach { l -> ChoiceChip(levelLabel(l), level == l) { level = l } }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = popup, onCheckedChange = { popup = it })
                        Text("অ্যাপ খুললে Home পেজে পপ-আপ দেখান")
                    }
                }
                if (isAdmin) {
                    Text("কারা পাবে", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("MEMBERS", "ADMINS", "EVERYONE").forEach { a -> ChoiceChip(audienceLabel(a), audience == a) { audience = a } }
                    }
                } else {
                    Text("প্রাপক: সব সদস্য", fontSize = 13.sp)
                }
                PrimaryButton(if (type == MEETING) "জরুরি মিটিং নোটিফিকেশন পাঠান" else "নোটিফিকেশন পাঠান", !vm.busy) {
                    val finalBody = if (type == MEETING) {
                        buildString {
                            if (meetTime.isNotBlank()) append("সময়: ").append(meetTime.trim()).append('\n')
                            if (meetPlace.isNotBlank()) append("স্থান: ").append(meetPlace.trim()).append('\n')
                            append(body.trim())
                        }.trim()
                    } else {
                        body
                    }
                    vm.createNotice(
                        title, finalBody,
                        if (type == MEETING) "EMERGENCY" else level,
                        if (isAdmin) audience else "MEMBERS",
                        type, popup,
                        if (type == MEETING) "GENERAL" else dept
                    )
                }
            }

            SectionTitle(if (isAdmin) "পাঠানো নোটিশের ইতিহাস" else "আমার পাঠানো নোটিশ")
            if (vm.noticeHistory.isEmpty()) Text("এখনো কোনো নোটিশ পাঠানো হয়নি।", color = MaterialTheme.colorScheme.onSurfaceVariant)
            vm.noticeHistory.forEach { h ->
                AppCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusChip(if (h.noticeType == MEETING) "জরুরি মিটিং" else levelLabel(h.level))
                        Text(audienceLabel(h.audience), fontSize = 12.sp)
                        if (h.showPopup) Text("পপ-আপ", fontSize = 12.sp)
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
