package com.messerp.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messerp.app.data.ChatMessage
import com.messerp.app.data.Conversation
import com.messerp.app.data.Dates
import com.messerp.app.data.Money
import com.messerp.app.data.ReceiptPdf
import com.messerp.app.data.Role

private fun chatTime(atLocal: String): String {
    val long = ReceiptPdf.formatLocal(atLocal)
    return if (atLocal.startsWith(Dates.todayDhaka())) long.substringAfter(", ") else long
}

@Composable
fun OfflineBanner() {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
        Text(
            "ইন্টারনেট নেই। বার্তা ফোনে জমা থাকবে, নেট এলে নিজে চলে যাবে। আগের বার্তা পড়া যাবে।",
            modifier = Modifier.padding(10.dp),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

@Composable
fun ChatListScreen(vm: AppViewModel) {
    val p = vm.profile ?: return
    val isAdmin = p.role != Role.MEMBER
    LaunchedEffect(Unit) {
        vm.loadConversations()
        if (isAdmin) vm.ensureMembersLoaded()
    }
    AppScaffold(vm, 3, "বার্তা", "মেসেঞ্জারের মতো, শুধু লেখা") {
        MessageText(vm)
        if (vm.chatOffline) OfflineBanner()
        if (vm.conversations.isEmpty()) Text("লোড হচ্ছে...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        vm.conversations.forEach { c ->
            AppCard(modifier = Modifier.clickable { vm.openChat(c) }) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            (if (c.kind == "PRIVATE") "🔒 " else "") + c.title,
                            fontWeight = if (c.unread > 0) FontWeight.Bold else FontWeight.SemiBold
                        )
                        Text(
                            if (c.lastBody == null) "এখনো কোনো বার্তা নেই" else (c.lastSender?.let { it + ": " } ?: "") + c.lastBody,
                            fontSize = 13.sp,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (c.lastLocal != null) Text(chatTime(c.lastLocal), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (c.unread > 0) StatusChip(Money.bn(c.unread.toString()), positive = true)
                }
            }
        }
        if (isAdmin) {
            SectionTitle("সদস্যের সঙ্গে ব্যক্তিগত বার্তা শুরু করুন")
            AppCard {
                vm.members.filter { it.role == Role.MEMBER && it.isActive }.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 2.dp)) {
                        row.forEach { m -> ChoiceChip(m.fullName, false) { vm.startPrivateWith(m.id) } }
                    }
                }
            }
        }
        Text(
            "গ্রুপে সবাই (সদস্য ও অ্যাডমিন) থাকেন। 🔒 ব্যক্তিগত বার্তা শুধু ওই সদস্য ও অ্যাডমিনরা দেখেন। শুধু লেখা পাঠানো যায়।",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Bubble(vm: AppViewModel, conv: Conversation, m: ChatMessage, onClick: () -> Unit) {
    val me = vm.profile?.id
    val mine = m.senderId == me
    val c = MaterialTheme.colorScheme
    val seen = vm.chatReads.count { it.userId != me && it.ts >= m.ts }
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Surface(
            color = if (mine) c.primary else c.surfaceVariant,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(max = 290.dp).clickable(onClick = onClick)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (!mine && conv.kind == "GROUP" && m.senderName != null) {
                    Text(m.senderName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.primary)
                }
                Text(
                    if (m.deleted) "এই বার্তা মুছে ফেলা হয়েছে" else (m.body ?: ""),
                    color = if (mine) c.onPrimary else c.onSurface,
                    fontWeight = if (m.deleted) FontWeight.Light else FontWeight.Normal
                )
                val status = when {
                    !mine -> ""
                    m.state == "QUEUED" -> "○ পাঠানো হচ্ছে…"
                    m.state == "FAILED" -> "! পাঠানো যায়নি, ট্যাপ করুন"
                    seen > 0 && conv.kind == "GROUP" -> "✓✓ " + Money.bn(seen.toString()) + " জন দেখেছেন"
                    seen > 0 -> "✓✓ দেখা হয়েছে"
                    else -> "✓ পাঠানো"
                }
                Text(
                    (if (m.state == "QUEUED") "" else chatTime(m.atLocal)) + (if (m.edited && !m.deleted) " · সম্পাদিত" else "") +
                        (if (status.isNotEmpty()) " · " + status else ""),
                    fontSize = 10.sp,
                    color = if (mine) c.onPrimary else c.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ChatScreen(vm: AppViewModel) {
    val conv = vm.currentConv ?: return
    val p = vm.profile ?: return
    val isAdmin = p.role != Role.MEMBER
    val listState = rememberLazyListState()
    var input by rememberSaveable { mutableStateOf("") }
    var actionFor by remember { mutableStateOf<ChatMessage?>(null) }
    var editFor by remember { mutableStateOf<ChatMessage?>(null) }
    var editText by rememberSaveable { mutableStateOf("") }
    val msgs = vm.chatMessages

    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) listState.animateScrollToItem(msgs.size - 1) }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { vm.openChatList() }) { Text("‹", color = MaterialTheme.colorScheme.onPrimary, fontSize = 24.sp) }
                Column {
                    Text(conv.title, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        if (conv.kind == "GROUP") "সবাই এখানে আছেন" else "ব্যক্তিগত: শুধু সদস্য ও অ্যাডমিন",
                        color = MaterialTheme.colorScheme.onPrimary, fontSize = 11.sp
                    )
                }
            }
        }
        if (vm.chatOffline) OfflineBanner()
        MessageText(vm)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(msgs, key = { it.id }) { m -> Bubble(vm, conv, m) { actionFor = m } }
        }
        if (vm.chatTyping.isNotEmpty()) {
            Text(
                vm.chatTyping.joinToString(", ") + " লিখছে…",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp)
            )
        }
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it; vm.onChatTyping() },
                    placeholder = { Text("বার্তা লিখুন") },
                    maxLines = 4,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { vm.sendChat(input); input = "" }, enabled = input.isNotBlank()) { Text("পাঠান") }
            }
        }
    }

    actionFor?.let { m ->
        val mine = m.senderId == p.id
        AlertDialog(
            onDismissRequest = { actionFor = null },
            title = { Text("বার্তা") },
            text = { Text(if (m.deleted) "এই বার্তা মুছে ফেলা হয়েছে।" else (m.body ?: "")) },
            confirmButton = {
                Column {
                    if (m.state == "FAILED") {
                        TextButton(onClick = { vm.retryChat(m.id); actionFor = null }) { Text("আবার পাঠান") }
                        TextButton(onClick = { vm.discardChat(m.id); actionFor = null }) { Text("বাদ দিন") }
                    } else if (m.state == "SENT" && !m.deleted && mine) {
                        TextButton(onClick = { editText = m.body ?: ""; editFor = m; actionFor = null }) { Text("সম্পাদনা (১৫ মিনিটের মধ্যে)") }
                        TextButton(onClick = { vm.deleteChat(m.id); actionFor = null }) { Text("মুছুন") }
                    } else if (m.state == "SENT" && !m.deleted && isAdmin) {
                        TextButton(onClick = { vm.deleteChat(m.id); actionFor = null }) { Text("মুছুন (মডারেশন, অডিট হবে)") }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { actionFor = null }) { Text("বন্ধ") } }
        )
    }
    editFor?.let { m ->
        AlertDialog(
            onDismissRequest = { editFor = null },
            title = { Text("বার্তা সম্পাদনা") },
            text = { OutlinedTextField(value = editText, onValueChange = { editText = it }) },
            confirmButton = { TextButton(onClick = { vm.editChat(m.id, editText); editFor = null }) { Text("সংরক্ষণ") } },
            dismissButton = { TextButton(onClick = { editFor = null }) { Text("বাতিল") } }
        )
    }
}
